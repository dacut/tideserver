package org.kanga.tideserver

import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.regex.Pattern

val hours1 = Duration.of(1, ChronoUnit.HOURS)!!
val days1 = Duration.of(1, ChronoUnit.DAYS)!!
val days7 = Duration.of(7, ChronoUnit.DAYS)!!
val days30 = Duration.of(30, ChronoUnit.DAYS)!!
val days365 = Duration.of(365, ChronoUnit.DAYS)!!

val noaaTimestampFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd HH:mm")
val noaaWaterLevelTimestampFormat = Pattern.compile(
    "^(?<year>[0-9]{4})-?(?<month>0[1-9]|1[0-2])-?(?<day>0[1-9]|[12][0-9]|3[01]) " +
    "(?<hour>[0-1][0-9]|2[0-3]):(?<minute>[0-5][0-9])(?::(?<second>[0-5][0-9]|6[0-1])(?:\\.(?<secondFrac>[0-9]+))?)?$")!!
val noaaPredictionTimestampFormat = Pattern.compile(
    "^(?<month>0[1-9]|1[0-2])/(?<day>0[1-9]|12[0-9]|3[01])/(?<year>[0-9]{4}) "+
    "(?<hour>[0-1][0-9]|2[0-3]):(?<minute>[0-5][0-9])(?::(?<second>[0-5][0-9]|6[0-1])(?:\\.(?<secondFrac>[0-9]+))?)?$")!!

/**
 *  Convert a Java timestamp into the format expected by NOAA.
 *
 *  NOAA timestamps are in the form yyyymmdd HH:MM. See https://opendap.co-ops.nos.noaa.gov/axis/doc.html.
 *  @param timestamp    The Java timestamp to format.
 *  @return The equivalent NOAA timestamp.
 */
@Suppress("unused")
fun toNOAATimestampUTC(timestamp: ZonedDateTime): String {
    val utcTimestamp = timestamp.withZoneSameInstant(ZoneOffset.UTC)
    return noaaTimestampFormatter.format(utcTimestamp)
}

/**
 *  Convert a NOAA water level timestamp to a LocalDateTime.
 *
 *  Per the documentation, this is supposed to be in yyyymmdd HH:MM format. However, we also see yyyy-mm-dd HH:MM:SS.s
 *  from WaterLevelVerifiedSixMin and mm/dd/yyyy HH:MM from Predictions. This accepts any of these formats.
 *  @param  noaaTimestamp   The NOAA timestamp string
 *  @return The LocalDateTime equivalent.
 *  @throws IllegalArgumentException If the NOAA timestamp cannot be parsed.
 */
fun noaaTimestampToLocalDateTime(noaaTimestamp: String): LocalDateTime {
    var matcher = noaaWaterLevelTimestampFormat.matcher(noaaTimestamp)
    if (!matcher.matches()) {
        matcher = noaaPredictionTimestampFormat.matcher(noaaTimestamp)

        if (!matcher.matches())
            throw IllegalArgumentException("Invalid NOAA timestamp: $noaaTimestamp")
    }

    val year = matcher.group("year")!!.toInt()
    val month = matcher.group("month")!!.toInt()
    val day = matcher.group("day")!!.toInt()
    val hour = matcher.group("hour")!!.toInt()
    val minute = matcher.group("minute")!!.toInt()
    val second = (matcher.group("second") ?: "00").toInt()
    var secondFrac = matcher.group("secondFrac") ?: "0"

    // Convert secondFrac into nanoseconds
    while (secondFrac.length < 9)
        secondFrac += "0"

    val nanos = secondFrac.toInt()

    return LocalDateTime.of(year, month, day, hour, minute, second, nanos)
}
