#!/usr/bin/env python3
"""
Tidal database server via Lambda.
"""
# pylint: disable=C0103,C0326

from datetime import datetime, timedelta
import hashlib
from http import HTTPStatus
from json import dumps as json_dumps, loads as json_loads
from logging import DEBUG, getLogger
from os import environ
from re import compile as re_compile
from typing import List, Dict, Tuple

import boto3
from botocore.exceptions import ClientError as BotoClientError
from zeep import Client as ZeepClient
from zeep.cache import InMemoryCache
from zeep.exceptions import Fault as ZeepFault
from zeep.helpers import serialize_object as zeep_serialize_object
from zeep.transports import Transport

# RE for obtaining the station id from the HREF attribute in a station.
STATION_ID_REGEX = re_compile(r"^harcon\.html\?id=(?P<id>[0-9]+)$")

# RE for obtaining bucket and key from an s3://bucket/key URL
S3_LOCATION_REGEX = re_compile(r"^s3://(?P<bucket>[^/]+)/(?P<key>.*)$")

# NOAA WSDL for downloading stations. The actual WSDL is buggy (has an
# incorrect endpoint) so we point to our own version here.
NOAA_ACTIVE_STATIONS_WSDL = (
    "https://tides.kanga.org/wsdl/noaa-active-stations-fixed.wsdl")

# NOAA WSDL for downloading harmonic constituents
NOAA_HARMONICS_WSDL = (
    "https://opendap.co-ops.nos.noaa.gov/axis/webservices/"
    "harmonicconstituents/wsdl/HarmonicConstituents.wsdl")

log = getLogger("tideserver")
log.setLevel(DEBUG)

# Zeep transport for creating clients.
zeep_transport = Transport(cache=InMemoryCache())


# pylint: disable=W0212
def get_noaa_station_list() -> List[dict]:
    """
    get_noaa_station_list() -> List[dict]
    Returns the list of all known NOAA tide stations.

    The resulting structure has the following format:
    [
        {
            "StationId": "9447130",
            "StationName": "Seattle",
            "Sensors": [
                {
                    "SensorName": "Water Level",
                    "SensorId": "A1",

                }
            ]
        }
    ]
    """
    active_stations = ZeepClient(NOAA_ACTIVE_STATIONS_WSDL)

    # Zeep 2.2.0 has an issue with APIs that take no arguments:
    # https://github.com/mvantellingen/python-zeep/issues/479
    # We break down the parts of the call and patch the result here.
    op = active_stations.service.getActiveStations
    proxy = op._proxy
    binding = proxy._binding
    client = proxy._client

    envelope, http_headers = binding._create(
        op._op_name, (), {}, client=active_stations,
        options=proxy._binding_options)

    body = envelope.getchildren()[0]
    if body.getchildren():
        # Work around Zeep bug that includes another soap:Body within the
        # existing soap:Body
        body.remove(body.getchildren()[0])

    response = client.transport.post_xml(
        proxy._binding_options['address'], envelope, http_headers)

    operation_obj = binding.get(op._op_name)
    reply = binding.process_reply(client, operation_obj, response)
    stations = zeep_serialize_object(reply)

    # There's error in the resulting structure: name and ID are swapped.
    for station in stations:
        station_id = station.pop("name")
        station_name = station.pop("ID")
        station["StationId"] = station_id
        station["StationName"] = station_name

    return stations


def get_noaa_station_harmonics(station_id: str) -> List[dict]:
    """
    get_noaa_station_harmonics(station_id: str) -> List[dict]
    Returns the harmonics for a given station.
    """
    harmonics_service = ZeepClient(NOAA_HARMONICS_WSDL)
    harmonics = harmonics_service.service.getHarmonicConstituents(
        stationId=station_id, unit=0, timeZone=0)

    return zeep_serialize_object(harmonics)


def update_s3_noaa_station_list(
        json_location: str, sqs_work_queue: str) -> List[Dict]:
    """
    update_s3_noaa_station_list(
        json_location: str, sqs_work_queue: str) -> List[Dict]:
    Download and parse the NOAA list of station harmonics and upload the
    sanitized version to S3.

    Returns a list of the stations found.
    """
    stations = get_noaa_station_list()
    stations_json = json_dumps(stations).encode("utf-8")

    bucket, key = parse_s3_location(json_location)
    expires = datetime.utcnow() + timedelta(days=30)
    write_s3obj_if_changed(
        ACL="public-read", Bucket=bucket, Body=stations_json,
        ContentType="application/json", Expires=expires, Key=key,
        RequestPayer="requester")

    # Add each station to the refresh queue
    sqs = boto3.resource("sqs")
    queue = sqs.Queue(sqs_work_queue)
    messages = []

    for station in stations:
        body = json_dumps({
            "Action": "UpdateNOAAStation",
            "StationId": station["StationId"],
        })

        message = {
            "Id": station["StationId"],
            "MessageBody": body
        }

        messages.append(message)
        if len(messages) == 10:
            sqs_send_messages(queue, messages)
            messages = []

    if messages:
        sqs_send_messages(queue, messages)

    return stations


# pylint: disable=R0914
def update_single_noaa_station_harmonics(
        sqs_work_queue: str, noaa_station_harmonics_location: str) -> int:
    """
    update_single_noaa_station_harmonics(
        sqs_work_queue: str, noaa_station_harmonics_location: str) -> int
    Update the harmonics for a single NOAA station from the list of stations in
    the queue.
    """
    sqs = boto3.resource("sqs")
    sqs_client = boto3.client("sqs")
    queue = sqs.Queue(sqs_work_queue)

    messages = queue.receive_messages(
        AttributeNames=[], MaxNumberOfMessages=1, VisibilityTimeout=120,
        WaitTimeSeconds=20)

    for message in messages:
        body = json_loads(message.body)
        station_id = body["StationId"]

        log.info("Obtaining harmonics for station %s", station_id)

        try:
            harmonics = get_noaa_station_harmonics(station_id)
            harmonics_json = json_dumps(harmonics).encode("utf-8")

            bucket, key = parse_s3_location(
                noaa_station_harmonics_location.replace(
                    "${StationId}", station_id))

            expires = datetime.utcnow() + timedelta(days=30)
            write_s3obj_if_changed(
                ACL="public-read", Bucket=bucket, Body=harmonics_json,
                ContentType="application/json", Expires=expires, Key=key)
        except ZeepFault as e:
            log.warning("Failed to get station data for %s: %s", station_id, e,
                        exc_info=True)

        message.delete()

    result = sqs_client.get_queue_attributes(
        QueueUrl=sqs_work_queue, AttributeNames=["ApproximateNumberOfMessages"])
    return int(result["Attributes"]["ApproximateNumberOfMessages"])

def write_s3obj_if_changed(Bucket: str, Key: str, Body: bytes, **kw) -> bool:
    """
    write_s3obj_if_changed(Bucket: str, Key: str, Body: bytes, **kw) -> bool
    Write contents to S3 only if it has changed.

    The return value is True if a new object was written, False if the file
    in S3 is the same as the contents.

    Additional keywords, if specified, are sent to S3.
    """
    s3 = boto3.resource("s3")
    s3obj = s3.Object(Bucket, Key)

    etag = '"' + hashlib.md5(Body).hexdigest() + '"'

    try:
        existing_etag = s3obj.e_tag
    except BotoClientError as e:
        error_code = int(e.response.get("Error", {}).get("Code", "0"))
        if error_code != HTTPStatus.NOT_FOUND:
            log.error("Failed to get ETag of s3://%s/%s: %s",
                      Bucket, Key, e, exc_info=True)
            raise
        log.debug("Object s3://%s/%s does not exist, so no ETag available",
                  Bucket, Key)
        existing_etag = None

    if existing_etag == etag:
        log.debug("Object s3://%s/%s has same ETag as new content",
                  Bucket, Key)
        return False

    log.debug("Object s3://%s/%s differs from new content; replacing",
              Bucket, Key)
    s3obj.put(Body=Body, **kw)
    return True


def parse_s3_location(s3_location: str) -> Tuple[str, str]:
    """
    parse_s3_location(s3_location: str) -> Tuple[str, str]
    Parse an S3 location URL in the form s3://bucket/key to (bucket, key).
    """
    m = S3_LOCATION_REGEX.match(s3_location)
    if not m:
        raise ValueError(
            "Location %r is not a valid S3 URL in the form s3://bucket/key" %
            s3_location)
    return m.group("bucket"), m.group("key")


def sqs_send_messages(queue, messages: List[dict]):
    """
    sqs_send_messages(queue, messages: List[dict])
    Send a list of messages to SQS, redriving as necessary.
    """
    messages_by_id = dict(
        [(message["Id"], message) for message in messages])

    while messages_by_id:
        result = queue.send_messages(Entries=list(messages_by_id.values()))

        successes = result.get("Successful", [])
        fails = result.get("Failed", [])

        for success in successes:
            del messages_by_id[success["Id"]]

        for fail in fails:
            if fail["SenderFault"]:
                raise ValueError(
                    "Failed to send message to SQS: %s %s: message=%s" % (
                        fail["Code"], fail["Message"],
                        messages_by_id[fail["Id"]]))

    return


def lambda_handler(event, context): # pylint: disable=W0613
    """
    Invocation point for Lambda.
    """
    action = event.get("Action")

    if not action:
        raise ValueError("No Action specified in Lambda event.")

    # S3 locations for uploading the list of sttaions to.
    noaa_station_list_json_location = event.get(
        "NOAAStationListJSONLocation",
        environ.get("NOAAStationListJSONLocation"))

    # S3 location for uploading individual station data to.
    noaa_station_harmonics_location = event.get(
        "NOAAStationHarmonicsLocation",
        environ.get("NOAAStationHarmonicsLocation"))

    # SQS queue for sending work to.
    sqs_work_queue = event.get("SQSWorkQueue", environ.get("SQSWorkQueue"))

    if not noaa_station_list_json_location:
        raise ValueError("NOAAStationListJSONLocation not specified in either "
                         "Lambda environment or event.")

    if not noaa_station_harmonics_location:
        raise ValueError("NOAAStationHarmonics not specified in either Lambda "
                         "environment or event.")

    if not sqs_work_queue:
        raise ValueError("SQSWorkQueue not specified in either Lambda "
                         "environment or event.")

    if action == "UpdateNOAAStationList":
        stations = update_s3_noaa_station_list(
            noaa_station_list_json_location, sqs_work_queue)
        return {
            "Action": "UpdateSingleNOAAStation",
            "StationsAvailable": len(stations),
            "NOAAStationListJSONLocation": noaa_station_list_json_location,
            "NOAAStationHarmonicsLocation": noaa_station_harmonics_location,
            "SQSWorkQueue": sqs_work_queue,
        }
    elif action == "UpdateSingleNOAAStation":
        n_available = update_single_noaa_station_harmonics(
            sqs_work_queue, noaa_station_harmonics_location)
        return {
            "Action": "UpdateSingleNOAAStation",
            "StationsAvailable": n_available,
            "NOAAStationListJSONLocation": noaa_station_list_json_location,
            "NOAAStationHarmonicsLocation": noaa_station_harmonics_location,
            "SQSWorkQueue": sqs_work_queue,
        }
    else:
        raise ValueError("Invalid Action: %r" % action)
    return
