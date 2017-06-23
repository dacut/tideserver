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
from zlib import compress

import boto3
from botocore.exceptions import ClientError as BotoClientError
from pyquery import PyQuery
import requests

# RE for obtaining the station id from the HREF attribute in a station.
STATION_ID_REGEX = re_compile(r"^harcon\.html\?id=(?P<id>[0-9]+)$")

# Bucket for uploading our results to.
STATION_LIST_BUCKET = environ.get("STATION_LIST_BUCKET", "tidal-harmonics")

# Key for uploading our results to.
NOAA_STATION_LIST_KEY = environ.get(
    "NOAA_STATION_LIST_KEY", "stations/noaa-stations.json")

# The default URL for obtaining the list of stations.
NOAA_STATION_LIST_URL = environ.get(
    "NOAA_STATION_LIST_URL",
    "https://tidesandcurrents.noaa.gov/stations.html?type=Harmonic+Constituents"
)

# The default user-agent we send
USER_AGENT = environ.get("USER_AGENT", "tideserver/0.1.0")

# Headers we send to NOAA
EXTRA_HEADERS = {
    "X-About": "https://github.com/dacut/tideserver"
}

log = getLogger("tideserver.parse")


def parse_noaa_station_list(html: str) -> dict:
    """
    parse_noaa_station_list(html: str) -> dict
    Parse the NOAA list of station harmonics.

    The resulting dictionary has the structure:
    {
        "Stations": [
            {"Source": "NOAA", "StationId": "9447130", "StationName": "Seattle, WA"}, ...
        ]
    }

    Note that StationId is a string, not a number. Although station ids appear
    to be numerical, we don't know if things like leading zeros will be
    significant; we're being conservative in our assumptions here.
    """
    q = PyQuery(html)

    results = []

    for station in q("div.span4.station a"):
        href = station.get("href")
        station_id = STATION_ID_REGEX.match(href).group("id")
        station_id_and_name = station.text

        assert station_id_and_name.startswith(station_id + " ")
        station_name = station_id_and_name[len(station_id)+1:]

        results.append({
            "Source": "NOAA",
            "StationId": station_id,
            "StationName": station_name,
        })

    return {"Stations": results}


def get_noaa_station_list(url: str=NOAA_STATION_LIST_URL,
                          user_agent:str=USER_AGENT) -> dict:
    """
    get_noaa_station_list(url:str =NOAA_STATION_LIST_URL,
                          user_agent: str=USER_AGENT) -> dict
    Download and parse the NOAA list of station harmonics.

    See parse_noaa_station_list() for the format of the resulting structure.
    """
    headers = {"User-Agent": user_agent}
    headers.update(EXTRA_HEADERS)

    r = requests.get(url, headers=headers)
    html = r.text
    return parse_noaa_station_list(html)


def update_s3_noaa_station_list(url: str=NOAA_STATION_LIST_URL,
                                bucket_name: str=STATION_LIST_BUCKET,
                                key_name: str=NOAA_STATION_LIST_KEY,
                                user_agent: str=USER_AGENT) -> bool:
    """
    update_s3_noaa_station_list(url: str=NOAA_STATION_LIST_URL,
                                bucket_name: str=STATION_LIST_BUCKET,
                                key_name: str=NOAA_STATION_LIST_KEY,
                                user_agent: str=USER_AGENT) -> bool
    Download and parse the NOAA list of station harmonics and upload the
    sanitized version to S3.
    """
    stations = get_noaa_station_list(url=url, user_agent=user_agent)
    stations_json = json_dumps(stations)
    stations_json_deflated = compress(stations_json.encode("utf-8"))
    stations_etag = '"' + hashlib.md5(stations_json_deflated).hexdigest() + '"'

    # Do we need to update this?
    s3 = boto3.resource("s3")
    s3obj = s3.Object(bucket_name, key_name)

    try:
        existing_etag = s3obj.e_tag
    except BotoClientError as e:
        error_code = int(e.response.get("Error", {}).get("Code", "0"))
        if error_code != HTTPStatus.NOT_FOUND:
            log.error("Failed to get ETag of s3://%s/%s: %s",
                      bucket_name, key_name, e, exc_info=True)
            raise
        log.info("Object s3://%s/%s does not exist, so no ETag available",
                 bucket_name, key_name)
        existing_etag = None

    if existing_etag != stations_etag:
        log.info("Uploading new NOAA station list to S3: "
                 "old ETag=%s, new ETag=%s", existing_etag, stations_etag)
        expires = datetime.utcnow() + timedelta(days=30)
        s3obj.put(ACL="public-read", Body=stations_json_deflated,
                  ContentEncoding="deflate", ContentType="application/json",
                  Expires=expires, RequestPayer="requester")
    else:
        log.info("Skipping S3 upload")

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
