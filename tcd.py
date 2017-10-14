#!/usr/bin/env python3
# pylint: disable=C0103,C0111,R0903,R0913,W0611
"""
Test the Tide Components Database (tcd) library.
"""
from ctypes import (
    CDLL, cast, byref, POINTER, pointer, Structure,
    c_char, c_char_p, c_ubyte, c_int16, c_uint16, c_int32, c_uint32, c_float,
    c_double
)
from typing import List, Optional, Tuple

libtcd = CDLL("/usr/local/xtide/lib/libtcd.dylib")
ONELINER_LENGTH = 90
MONOLOGUE_LENGTH = 10000
MAX_CONSTITUENTS = 255

REFERENCE_STATION = 1
SUBORDINATE_STATION = 2

class DB_HEADER_PUBLIC(Structure):
    _fields_ = [
        ("version", c_char * ONELINER_LENGTH),
        ("major_rev", c_uint32),
        ("minor_rev", c_uint32),
        ("last_modified", c_char * ONELINER_LENGTH),
        ("number_of_records", c_uint32),
        ("start_year", c_int32),
        ("number_of_years", c_uint32),
        ("constituents", c_uint32),
        ("level_unit_types", c_uint32),
        ("dir_unit_types", c_uint32),
        ("restriction_types", c_uint32),
        ("datum_types", c_uint32),
        ("countries", c_uint32),
        ("tzfiles", c_uint32),
        ("legaleses", c_uint32),
        ("pedigree_types", c_uint32),
    ]

    def __repr__(self):
        return "DB_HEADER_PUBLIC(" + ",".join(
            ["%s=%r" % (field[0], getattr(self, field[0]))
             for field in DB_HEADER_PUBLIC._fields_]) + ")"


class TIDE_STATION_HEADER(Structure):
    _fields_ = [
        ("record_number", c_int32),
        ("record_size", c_uint32),
        ("record_type", c_ubyte),
        ("latitude", c_double),
        ("longitude", c_double),
        ("reference_station", c_int32),
        ("tzfile", c_int16),
        ("name", c_char * ONELINER_LENGTH),
    ]

    def __repr__(self):
        return "TIDE_STATION_HEADER(" + ",".join(
            ["%s=%r" % (field[0], getattr(self, field[0]))
             for field in TIDE_STATION_HEADER._fields_]) + ")"


class TIDE_RECORD(Structure):
    _fields_ = [
        # Common
        ("header", TIDE_STATION_HEADER),
        ("country", c_int16),
        ("source", c_char * ONELINER_LENGTH),
        ("restriction", c_ubyte),
        ("comments", c_char * MONOLOGUE_LENGTH),
        ("notes", c_char * MONOLOGUE_LENGTH),
        ("legalese", c_ubyte),
        ("station_id_context", c_char * ONELINER_LENGTH),
        ("station_id", c_char * ONELINER_LENGTH),
        ("date_imported", c_uint32),
        ("xfields", c_char * MONOLOGUE_LENGTH),
        ("direction_units", c_ubyte),
        ("min_direction", c_int32),
        ("max_direction", c_int32),
        ("level_units", c_ubyte),

        # Type 1
        ("datum_offset", c_float),
        ("datum", c_int16),
        ("zone_offset", c_int32),
        ("expiration_date", c_uint32),
        ("months_on_station", c_uint16),
        ("last_date_on_station", c_uint32),
        ("confidence", c_ubyte),
        ("amplitude", c_float * MAX_CONSTITUENTS),
        ("epoch", c_float * MAX_CONSTITUENTS),

        # Type 2
        ("min_time_add", c_int32),
        ("min_level_add", c_float),
        ("min_level_multiply", c_float),
        ("max_time_add", c_int32),
        ("max_level_add", c_float),
        ("max_level_multiply", c_float),
        ("flood_begins", c_int32),
        ("ebb_begins", c_int32),

        # Deprecated
        ("pedigree", c_int16),
        ("units", c_ubyte),
        ("avg_level_units", c_ubyte),
        ("min_avg_level", c_float),
        ("max_avg_level", c_float),
    ]

    def __repr__(self):
        return "TIDE_RECORD(" + ",".join(
            ["%s=%r" % (field[0], getattr(self, field[0]))
             for field in TIDE_RECORD._fields_]) + ")"


# DWF: This value signifies "null" or "omitted" slack offsets
# (flood_begins, ebb_begins).  Zero is *not* the same.
# Time offsets are represented as hours * 100 plus minutes.
# 0xA00 = 2560
# It turns out that offsets do exceed 24 hours (long story), but we
# should still be safe with the 60.
NULLSLACKOFFSET = 0xA00

# This is the level below which an amplitude rounds to zero.
# It should be exactly (0.5 / DEFAULT_AMPLITUDE_SCALE).
AMPLITUDE_EPSILON = 0.00005

libtcd.dump_tide_record.argtypes = (POINTER(TIDE_RECORD),)
libtcd.dump_tide_record.restype = None
def dump_tide_record(rec: TIDE_RECORD) -> None:
    return libtcd.dump_tide_record(byref(rec))

# For fields in the tide record that are indices into tables of
# character string values, these functions are used to retrieve the
# character string value corresponding to a particular index.  The
# value "Unknown" is returned when no translation exists.  The return
# value is a pointer into static memory.
libtcd.get_country.argtypes = (c_int32,)
libtcd.get_country.restype = c_char_p
def get_country(num: int) -> str:
    return libtcd.get_country(num).decode("utf-8")


libtcd.get_tzfile.argtypes = (c_int32,)
libtcd.get_tzfile.restype = c_char_p
def get_tzfile(num: int) -> str:
    return libtcd.get_tzfile(num).decode("utf-8")


libtcd.get_level_units.argtypes = (c_int32,)
libtcd.get_level_units.restype = c_char_p
def get_level_units(num: int) -> str:
    return libtcd.get_level_units(num).decode("utf-8")


libtcd.get_dir_units.argtypes = (c_int32,)
libtcd.get_dir_units.restype = c_char_p
def get_dir_units(num: int) -> str:
    return libtcd.get_dir_units(num).decode("utf-8")


libtcd.get_restriction.argtypes = (c_int32,)
libtcd.get_restriction.restype = c_char_p
def get_restriction(num: int) -> str:
    return libtcd.get_restriction(num).decode("utf-8")


libtcd.get_datum.argtypes = (c_int32,)
libtcd.get_datum.restype = c_char_p
def get_datum(num: int) -> str:
    return libtcd.get_datum(num).decode("utf-8")


libtcd.get_legalese.argtypes = (c_int32,)
libtcd.get_legalese.restype = c_char_p
def get_legalese(num: int) -> str:
    return libtcd.get_legalese(num).decode("utf-8")



# Get the name of the constituent corresponding to index num
# [0,constituents-1].  The return value is a pointer into static
# memory.
libtcd.get_constituent.argtypes = (c_int32,)
libtcd.get_constituent.restype = c_char_p
def get_constituent(num: int) -> str:
    return libtcd.get_constituent(num).decode("utf-8")



# Get the name of the station whose record_number is num
# [0,number_of_records-1].  The return value is a pointer into static
# memory.
libtcd.get_station.argtypes = (c_int32,)
libtcd.get_station.restype = c_char_p
def get_station(num: int) -> str:
    return libtcd.get_station(num).decode("utf-8")



# Returns the speed of the constituent indicated by num
# [0,constituents-1].
libtcd.get_speed.argtypes = (c_int32,)
libtcd.get_speed.restype = c_double
def get_speed(num: int) -> float:
    return libtcd.get_speed(num)



# Get the equilibrium argument and node factor for the constituent
# indicated by num [0,constituents-1], for the year
# start_year+year.
libtcd.get_equilibrium.argtypes = (c_int32, c_int32)
libtcd.get_equilibrium.restype = c_double
def get_equilibrium(num: int, year: int) -> float:
    return libtcd.get_equilibrium(num, year)


libtcd.get_node_factor.argtypes = (c_int32, c_int32)
libtcd.get_node_factor.restype = c_double
def get_node_factor(num: int, year: int) -> float:
    return libtcd.get_node_factor(num, year)



# Get all available equilibrium arguments and node factors for the
# constituent indicated by num [0,constituents-1].  The return value
# is a pointer into static memory which is an array of
# number_of_years floats, corresponding to the years start_year
# through start_year+number_of_years-1.
libtcd.get_equilibriums.argtypes = (c_int32,)
libtcd.get_equilibriums.restype = POINTER(c_float)
def get_equilibriums(num: int) -> float:
    n_years = get_tide_db_header().number_of_years

    result = libtcd.get_equilibriums(num)
    return cast(result, c_float * n_years)

libtcd.get_node_factors.argtypes = (c_int32,)
libtcd.get_node_factors.restype = POINTER(c_float)
def get_node_factors(num: int) -> float:
    n_years = get_tide_db_header().number_of_years

    result = libtcd.get_node_factors(num)
    return cast(result, c_float * n_years)


# Convert between character strings of the form "[+-]HH:MM" and the
# encoding Hours * 100 + Minutes.  ret_time pads the hours with a
# leading zero when less than 10; ret_time_neat omits the leading
# zero and omits the sign when the value is 0:00.  Returned pointers
# point into static memory.
libtcd.get_time.argtypes = (c_char_p,)
libtcd.get_time.restype = c_int32
def get_time(string: str) -> int:
    return int(libtcd.get_time(string.encode("utf-8")))



libtcd.ret_time.argtypes = (c_int32,)
libtcd.ret_time.restype = c_char_p
def ret_time(time: c_int32):
    return libtcd.ret_time(time).decode("utf-8")


libtcd.ret_time_neat.argtypes = (c_int32,)
libtcd.ret_time_neat.restype = c_char_p
def ret_time_neat(time: c_int32) -> str:
    return libtcd.ret_time_neat(time).decode("utf-8")



# Convert the encoding Year * 10000 + Month [1, 12] * 100 + Day [1,
# 31] to a character string of the form "YYYY-MM-DD", or "NULL" if
# the value is zero.  The returned pointer points into static memory.
# (The compact form, without hyphens, is obtainable just by printing
# the integer.) */
libtcd.ret_date.argtypes = (c_uint32,)
libtcd.ret_date.restype = c_char_p
def ret_date(date: int) -> str:
    return libtcd.ret_date(date).decode("utf-8")


# When invoked multiple times with the same string, returns record
# numbers of all stations that have that string anywhere in the
# station name.  This search is case insensitive.  When no more
# records are found it returns -1.
libtcd.search_station.argtypes = (c_char_p,)
libtcd.search_station.restype = c_int32
def search_station(string: str) -> int:
    return int(libtcd.search_station(string.encode("utf-8")))



# Inverses of the corresponding get_ operations.  Return -1 for not
# found.
libtcd.find_station.argtypes = (c_char_p,)
libtcd.find_station.restype = c_int32
def find_station(name: str) -> int:
    return int(libtcd.find_station(name.encode("utf-8")))


libtcd.find_tzfile.argtypes = (c_char_p,)
libtcd.find_tzfile.restype = c_int32
def find_tzfile(name: str) -> int:
    return int(libtcd.find_tzfile(name.encode("utf-8")))


libtcd.find_country.argtypes = (c_char_p,)
libtcd.find_country.restype = c_int32
def find_country(name: str) -> int:
    return int(libtcd.find_country(name.encode("utf-8")))


libtcd.find_level_units.argtypes = (c_char_p,)
libtcd.find_level_units.restype = c_int32
def find_level_units(name: str) -> int:
    return int(libtcd.find_level_units(name.encode("utf-8")))


libtcd.find_dir_units.argtypes = (c_char_p,)
libtcd.find_dir_units.restype = c_int32
def find_dir_units(name: str) -> int:
    return int(libtcd.find_dir_units(name.encode("utf-8")))


libtcd.find_restriction.argtypes = (c_char_p,)
libtcd.find_restriction.restype = c_int32
def find_restriction(name: str) -> int:
    return int(libtcd.find_restriction(name.encode("utf-8")))


libtcd.find_datum.argtypes = (c_char_p,)
libtcd.find_datum.restype = c_int32
def find_datum(name: str) -> int:
    return int(libtcd.find_datum(name.encode("utf-8")))


libtcd.find_constituent.argtypes = (c_char_p,)
libtcd.find_constituent.restype = c_int32
def find_constituent(name: str) -> int:
    return int(libtcd.find_constituent(name.encode("utf-8")))


libtcd.find_legalese.argtypes = (c_char_p,)
libtcd.find_legalese.restype = c_int32
def find_legalese(name: str) -> int:
    return int(libtcd.find_legalese(name.encode("utf-8")))



# Add the value of name to the corresponding lookup table and return
# the index of the new value.  If db is not NULL, the database header
# struct pointed to will be updated to reflect the changes.
libtcd.add_restriction.argtypes = (c_char_p, POINTER(DB_HEADER_PUBLIC))
libtcd.add_restriction.restype = c_int32
def add_restriction(name: str, db: DB_HEADER_PUBLIC) -> int:
    return int(libtcd.add_restriction(name.encode("utf-8"), byref(db)))


libtcd.add_tzfile.argtypes = (c_char_p, POINTER(DB_HEADER_PUBLIC))
libtcd.add_tzfile.restype = c_int32
def add_tzfile(name: str, db: DB_HEADER_PUBLIC) -> int:
    return int(libtcd.add_tzfile(name.encode("utf-8"), byref(db)))


libtcd.add_country.argtypes = (c_char_p, POINTER(DB_HEADER_PUBLIC))
libtcd.add_country.restype = c_int32
def add_country(name: str, db: DB_HEADER_PUBLIC) -> int:
    return int(libtcd.add_country(name.encode("utf-8"), byref(db)))


libtcd.add_datum.argtypes = (c_char_p, POINTER(DB_HEADER_PUBLIC))
libtcd.add_datum.restype = c_int32
def add_datum(name: str, db: DB_HEADER_PUBLIC) -> int:
    return int(libtcd.add_datum(name.encode("utf-8"), byref(db)))


libtcd.add_legalese.argtypes = (c_char_p, POINTER(DB_HEADER_PUBLIC))
libtcd.add_legalese.restype = c_int32
def add_legalese(name: str, db: DB_HEADER_PUBLIC) -> int:
    return int(libtcd.add_legalese(name.encode("utf-8"), byref(db)))



# Add the value of name to the corresponding lookup table if and
# only if it is not already present.  Return the index of the value.
# If db is not NULL, the database header struct pointed to will be
# updated to reflect the changes.
libtcd.find_or_add_restriction.argtypes = (c_char_p, POINTER(DB_HEADER_PUBLIC))
libtcd.find_or_add_restriction.restype = c_int32
def find_or_add_restriction(name: str, db: DB_HEADER_PUBLIC) -> int:
    return int(libtcd.find_or_add_restriction(name.encode("utf-8"), byref(db)))


libtcd.find_or_add_tzfile.argtypes = (c_char_p, POINTER(DB_HEADER_PUBLIC))
libtcd.find_or_add_tzfile.restype = c_int32
def find_or_add_tzfile(name: str, db: DB_HEADER_PUBLIC) -> int:
    return int(libtcd.find_or_add_tzfile(name.encode("utf-8"), byref(db)))


libtcd.find_or_add_country.argtypes = (c_char_p, POINTER(DB_HEADER_PUBLIC))
libtcd.find_or_add_country.restype = c_int32
def find_or_add_country(name: str, db: DB_HEADER_PUBLIC) -> int:
    return int(libtcd.find_or_add_country(name.encode("utf-8"), byref(db)))


libtcd.find_or_add_datum.argtypes = (c_char_p, POINTER(DB_HEADER_PUBLIC))
libtcd.find_or_add_datum.restype = c_int32
def find_or_add_datum(name: str, db: DB_HEADER_PUBLIC) -> int:
    return int(libtcd.find_or_add_datum(name.encode("utf-8"), byref(db)))


libtcd.find_or_add_legalese.argtypes = (c_char_p, POINTER(DB_HEADER_PUBLIC))
libtcd.find_or_add_legalese.restype = c_int32
def find_or_add_legalese(name: str, db: DB_HEADER_PUBLIC) -> int:
    return int(libtcd.find_or_add_legalese(name.encode("utf-8"), byref(db)))



# Set the speed for the constituent corresponding to index num
# [0,constituents-1].
libtcd.set_speed.argtypes = (c_int32, c_double)
libtcd.set_speed.restype = None
def set_speed(num: int, value: float) -> None:
    return libtcd.set_speed(num, value)


# Set the equilibrium argument and node factor for the constituent
# corresponding to index num [0,constituents-1], for the year
# start_year+year.
libtcd.set_equilibrium.argtypes = (c_int32, c_int32, c_float)
libtcd.set_equilibrium.restype = None
def set_equilibrium(num: int, year: int, value: float) -> None:
    return libtcd.set_equilibrium(num, year, value)


libtcd.set_node_factor.argtypes = (c_int32, c_int32, c_float)
libtcd.set_node_factor.restype = None
def set_node_factor(num: int, year: int, value: float) -> None:
    return libtcd.set_node_factor(num, year, value)


# Opens the specified TCD file.  If a different database is already
# open, it will be closed.  libtcd maintains considerable internal
# state and can only handle one open database at a time.  Returns
# false if the open failed.
libtcd.open_tide_db.argtypes = (c_char_p,)
libtcd.open_tide_db.restype = c_ubyte
def open_tide_db(file: str) -> bool:
    return bool(libtcd.open_tide_db(file.encode("utf-8")))


# Closes the open database.
libtcd.close_tide_db.argtypes = ()
libtcd.close_tide_db.restype = None
def close_tide_db() -> None:
    libtcd.close_tide_db()


# Creates a TCD file with the supplied constituents and no tide
# stations.  Returns false if creation failed.  The database is left
# in an open state.
libtcd.create_tide_db.argtypes = (
    c_char_p, c_uint32, POINTER(c_char_p), POINTER(c_double), c_int32,
    c_uint32, POINTER(POINTER(c_float)), POINTER(POINTER(c_float)))
libtcd.create_tide_db.restype = c_ubyte
def create_tide_db(
        file: str, constituent: List[str], speed: List[float],
        start_year: int, num_years: int, equilibrium: List[List[float]],
        node_factor: List[List[float]]) -> bool:
    constituents = len(constituent)
    if constituents != len(speed):
        raise ValueError(
            "speed must have the same length as consituent (%d vs %d)" %
            (len(speed), constituents))

    if constituents != len(equilibrium):
        raise ValueError(
            "equilibrium must have the same length as consituent (%d vs %d)" %
            (len(equilibrium), constituents))

    if constituents != len(node_factor):
        raise ValueError(
            "node_factor must have the same length as consituent (%d vs %d)" %
            (len(node_factor), constituents))

    tcd_constituent = (c_char_p * constituents)()
    tcd_speed = (c_double * constituents)()
    tcd_equilibrium = (POINTER(c_float) * constituents)()
    tcd_node_factor = (POINTER(c_float) * constituents)()

    for i in range(constituents):
        tcd_constituent[i] = c_char_p(constituent[i])
        tcd_speed[i] = c_double(speed[i])

        tcd_equilibrium[i] = (c_float * len(equilibrium[i]))()
        for j in range(len(equilibrium[i])):
            tcd_equilibrium[i][j] = equilibrium[i][j]

        tcd_node_factor[i] = (c_float * len(node_factor[i]))()
        for j in range(len(node_factor[i])):
            tcd_node_factor[i][j] = node_factor[i][j]

    result = libtcd.create_tide_db(
        file.encode("utf-8"), constituents, tcd_constituent,
        tcd_speed, start_year, num_years, tcd_equilibrium, tcd_node_factor)

    return bool(result)

# Returns a copy of the database header for the open database.
libtcd.get_tide_db_header.argtypes = ()
libtcd.get_tide_db_header.restype = DB_HEADER_PUBLIC
def get_tide_db_header():
    return libtcd.get_tide_db_header()


# Gets "header" portion of tide record for the station whose
# record_number is num [0,number_of_records-1] and writes it into
# rec.  Returns false if num is out of range.  num is preserved in
# the static variable current_index.
libtcd.get_partial_tide_record.argtypes = (
    c_int32, POINTER(TIDE_STATION_HEADER))
libtcd.get_partial_tide_record.restype = c_ubyte
def get_partial_tide_record(num: int) -> TIDE_STATION_HEADER:
    result = TIDE_STATION_HEADER()
    if not libtcd.get_partial_tide_record(num, byref(result)):
        raise ValueError("num out of range")

    return result


# Invokes get_partial_tide_record for current_index+1.  Returns the
# record number or -1 for failure.
libtcd.get_next_partial_tide_record.argtypes = (POINTER(TIDE_STATION_HEADER),)
libtcd.get_next_partial_tide_record.restype = c_int32
def get_next_partial_tide_record() -> Tuple[int, TIDE_STATION_HEADER]:
    header = TIDE_STATION_HEADER()
    index = libtcd.get_next_partial_tide_record(byref(header))
    if index == -1:
        raise ValueError("out of records")

    return (index, header)


# Invokes get_partial_tide_record for a station that appears closest
# to the specified lat and lon in the Cylindrical Equidistant
# projection.  Returns the record number or -1 for failure.
libtcd.get_nearest_partial_tide_record.argtypes = (
    c_double, c_double, POINTER(TIDE_STATION_HEADER))
libtcd.get_nearest_partial_tide_record.restype = c_int32
def get_nearest_partial_tide_record(
        lat: float, lon: float) -> Tuple[int, TIDE_STATION_HEADER]:
    header = TIDE_STATION_HEADER()
    index = libtcd.get_nearest_partial_tide_record(lat, lon, byref(header))
    if index == -1:
        raise ValueError("failed to find partial tide record")

    return (index, header)


# Gets tide record for the station whose record_number is num
# [0,number_of_records-1] and writes it into rec.  num is preserved
# in the static variable current_record.  Returns num, or -1 if num is
# out of range.
libtcd.read_tide_record.argtypes = (c_int32, POINTER(TIDE_RECORD))
libtcd.read_tide_record.restype = c_int32
def read_tide_record(num: int) -> TIDE_RECORD:
    rec = TIDE_RECORD()
    if libtcd.read_tide_record(num, byref(rec)) == -1:
        raise ValueError("num out of range")

    return rec


# Invokes read_tide_record for current_record+1.  Returns the record
# number or -1 for failure.
libtcd.read_next_tide_record.argtypes = (POINTER(TIDE_RECORD),)
libtcd.read_next_tide_record.restype = c_int32
def read_next_tide_record() -> Tuple[int, TIDE_RECORD]:
    rec = TIDE_RECORD()
    num = libtcd.read_next_tide_record(byref(rec))
    if num == -1:
        raise ValueError("out of records")

    return (num, rec)


# Add a new record, update an existing record, or delete an existing
# record.  If the deleted record is a reference station, all
# dependent subordinate stations will also be deleted.  Add and
# update return false if the new record is invalid; delete and update
# return false if the specified num is invalid.  If db is not NULL,
# the database header struct pointed to will be updated to reflect
# the changes.
libtcd.add_tide_record.argtypes = (
    POINTER(TIDE_RECORD), POINTER(DB_HEADER_PUBLIC))
libtcd.add_tide_record.restype = c_ubyte
def add_tide_record(rec: TIDE_RECORD, db: Optional[DB_HEADER_PUBLIC]) -> None:
    if not libtcd.add_tide_record(byref(rec), byref(db) if db else None):
        raise ValueError("invalid record")

    return


libtcd.update_tide_record.argtypes = (
    c_int32, POINTER(TIDE_RECORD), POINTER(DB_HEADER_PUBLIC))
libtcd.update_tide_record.restype = c_ubyte
def update_tide_record(
        num: int, rec: TIDE_RECORD, db: Optional[DB_HEADER_PUBLIC]) -> None:
    if not libtcd.update_tide_record(
            num, byref(rec), byref(db) if db else None):
        raise ValueError("invalid record or num")

    return


libtcd.delete_tide_record.argtypes = (c_int32, POINTER(DB_HEADER_PUBLIC))
libtcd.delete_tide_record.restype = c_ubyte
def delete_tide_record(num: int, db: Optional[DB_HEADER_PUBLIC]) -> None:
    if not libtcd.delete_tide_record(num, byref(db) if db else None):
        raise ValueError("invalid num")

    return


# Computes inferred constituents when M2, S2, K1, and O1 are given
# and fills in the remaining unfilled constituents.  The inferred
# constituents are developed or decided based on Article 230 of
# "Manual of Harmonic Analysis and Prediction of Tides," Paul
# Schureman, C&GS Special Publication No. 98, October 1971.  Returns
# false if M2, S2, K1, or O1 is missing. */
libtcd.infer_constituents.argtypes = (POINTER(TIDE_RECORD),)
libtcd.infer_constituents.restype = c_ubyte
def infer_constituents(rec: TIDE_RECORD) -> None:
    if not libtcd.infer_constituents(byref(rec)):
        raise ValueError("Missing M2, S2, K1, or O1 constituents")
