#!/usr/bin/env python3
"""
Tidal database server via Lambda.
"""
# pylint: disable=C0103,C0326

from datetime import datetime, timedelta
import hashlib
from http import HTTPStatus
from json import dumps as json_dumps
from logging import basicConfig, getLogger, INFO, WARNING
from os import environ
from re import compile as re_compile
from typing import Tuple

import boto3
from botocore.exceptions import ClientError as BotoClientError
from zeep import Client as ZeepClient
from zeep.cache import InMemoryCache
from zeep.helpers import serialize_object as zeep_serialize_object
from zeep.transports import Transport

# RE for obtaining the station id from the HREF attribute in a station.
STATION_ID_REGEX = re_compile(r"^harcon\.html\?id=(?P<id>[0-9]+)$")

# RE for obtaining bucket and key from an s3://bucket/key URL
S3_LOCATION_REGEX = re_compile(r"^s3://(?P<bucket>[^/]+)/(?P<key>.*)$")


# S3 locations for uploading our results to.
NOAA_STATION_LIST_JSON_LOCATION = environ.get(
    "NOAA_STATION_LIST_JSON_LOCATION",
    "s3://tides-origin.kanga.org/stations/noaa-stations.json")

# NOAA WSDL for downloading stations. The actual WSDL is buggy (has an
# incorrect endpoint) so we point to our own version here.
NOAA_ACTIVE_STATIONS_WSDL = (
    "https://tidal-harmonics.s3-us-west-2.amazonaws.com/"
    "ActiveStationsFixed.wsdl")

# NOAA WSDL for downloading harmonic constituents
NOAA_HARMONICS_WSDL = (
    "https://opendap.co-ops.nos.noaa.gov/axis/webservices/"
    "harmonicconstituents/wsdl/HarmonicConstituents.wsdl")

log = getLogger("tideserver.parse")

# Zeep transport for creating clients.
zeep_transport = Transport(cache=InMemoryCache())


# pylint: disable=W0212
def get_noaa_station_list() -> dict:
    """
    get_noaa_station_list() -> dict
    Returns the list of all known NOAA tide stations.
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
    return zeep_serialize_object(reply)


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


def update_s3_noaa_station_list(
        json_location: str=NOAA_STATION_LIST_JSON_LOCATION) -> bool:
    """
    update_s3_noaa_station_list(bucket_name: str=STATION_LIST_BUCKET) -> bool
    Download and parse the NOAA list of station harmonics and upload the
    sanitized version to S3.
    """
    stations = get_noaa_station_list()
    stations_json = json_dumps(stations).encode("utf-8")

    bucket, key = parse_s3_location(json_location)
    expires = datetime.utcnow() + timedelta(days=30)
    write_s3obj_if_changed(
        ACL="public-read", Bucket=bucket, Body=stations_json,
        ContentType="application/json", Expires=expires, Key=key,
        RequestPayer="requester")

    return True


def lambda_handler(event, context): # pylint: disable=W0613
    """
    Invocation point for Lambda.
    """
    return


def main():
    """
    Invocation point when run from the command line.
    """
    basicConfig(level=INFO)
    getLogger("boto3").setLevel(WARNING)
    getLogger("botocore").setLevel(WARNING)
    update_s3_noaa_station_list()


if __name__ == "__main__":
    main()
