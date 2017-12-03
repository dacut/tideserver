package org.kanga.tideserver

import gov.noaa.nos.coops.opendap.webservices.activestations.Location
import gov.noaa.nos.coops.opendap.webservices.activestations.Metadata
import gov.noaa.nos.coops.opendap.webservices.activestations.Parameter
import gov.noaa.nos.coops.opendap.webservices.activestations.Station
import gov.noaa.nos.coops.opendap.webservices.activestations.Stations
import gov.noaa.nos.coops.opendap.webservices.highlowtidepred.HighLowValues
import gov.noaa.nos.coops.opendap.webservices.predictions.PredictionsValues
import gov.noaa.nos.coops.opendap.webservices.waterlevelrawsixmin.WaterLevelRawSixMinMeasurements
import gov.noaa.nos.coops.opendap.webservices.waterlevelverifiedsixmin.WaterLevelVerifiedSixMinMeasurements
import java.time.DateTimeException
import java.time.LocalDate
import javax.json.Json
import javax.json.JsonArray
import javax.json.JsonObject

val waterLevelVerifiedSixMinPackedDataFormat = Json.createArrayBuilder(listOf("waterLevelMeters", "sigma", "flags")).build()!!
val waterLevelRawSixMinPackedDataFormat = Json.createArrayBuilder(listOf("waterLevelMeters", "sigma", "samplesOutsideThreeSigma", "flags")).build()!!
val predictionsPackedDataFormat = Json.createArrayBuilder(listOf("waterLevelMeters")).build()!!
val extremaPackedDataFormat = Json.createArrayBuilder(listOf("timestampUTC", "waterLevelMeters", "extremaType")).build()!!

fun Stations.toJSON(): JsonObject {
    return Json.createObjectBuilder().add("Stations", noaaStationListToJSON(this.station)).build()
}

fun noaaStationListToJSON(stationList: List<Station>): JsonArray {
    val builder = Json.createArrayBuilder()
    stationList.forEach { builder.add(it.toJSON()) }
    return builder.build()
}

fun Station.toJSON(): JsonObject {
    val builder = Json.createObjectBuilder()
    // Note: name and id are swapped by the NOAA service.
    builder.add("ID", this.name)
    builder.add("name", this.id)
    builder.add("metadata", this.metadata.toJSON())
    builder.add("parameters", noaaParameterListToJSON(this.parameter))
    return builder.build()
}

fun Metadata.toJSON(): JsonObject {
    val builder = Json.createObjectBuilder()
    builder.add("dateEstablished", this.dateEstablished)
    builder.add("location", this.location.toJSON())
    return builder.build()
}

fun Location.toJSON(): JsonObject {
    val builder = Json.createObjectBuilder()
    builder.add("lat", this.lat)
    builder.add("long", this.long)
    builder.add("state", this.state)
    return builder.build()
}

fun noaaParameterListToJSON(plist: List<Parameter>): JsonArray {
    val builder = Json.createArrayBuilder()
    plist.forEach { builder.add(it.toJSON()) }
    return builder.build()
}

fun Parameter.toJSON(): JsonObject {
    val builder = Json.createObjectBuilder()
    builder.add("name", this.name)
    builder.add("dcp", this.dcp)
    builder.add("sensorID", this.sensorID)
    builder.add("status", this.status)
    return builder.build()
}

fun WaterLevelVerifiedSixMinMeasurements.toJSON(stationId: String, date: LocalDate): JsonObject {
    val builder = Json.createObjectBuilder()
    builder.add("stationId", stationId)
    builder.add("dateUTC", date.toString())
    builder.add("dataPoints", this.data.item.size)
    builder.add("packedDataFormat", waterLevelVerifiedSixMinPackedDataFormat)

    if (this.data.item.size == 0)
        throw BadGatewayException("No data provided for station $stationId")

    val firstDataPoint = this.data.item[0]
    val startTimestamp = noaaTimestampToLocalDateTime(firstDataPoint.timeStamp)

    if (startTimestamp != date.atStartOfDay()) {
        log.error("First timestamp for station $stationId does not start at start of day: $startTimestamp vs. $date")
        throw BadGatewayException("Expected start timestamp to be at start of day")
    }

    val dpBuilder = Json.createArrayBuilder()

    // This is the next expected timestamp.
    var nextExpectedTimestamp = startTimestamp

    this.data.item.forEach {
        val wlTimestamp = noaaTimestampToLocalDateTime(it.timeStamp)
        while (wlTimestamp.isAfter(nextExpectedTimestamp)) {
            // Indicate missing data points.
            log.warn("Timestamp for station $stationId overshot expected: nextExpectedTimestamp=$nextExpectedTimestamp, wlTimestamp=$wlTimestamp")
            dpBuilder.addNull()
            nextExpectedTimestamp = nextExpectedTimestamp.plusMinutes(6)
        }

        if (wlTimestamp != nextExpectedTimestamp) {
            log.error("Timestamp for station $stationId undershot expected: nextExpectedTimestamp=$nextExpectedTimestamp, wlTimestamp=$wlTimestamp")
            throw BadGatewayException("Invalid timestamp from NOAA WaterLevelVerifiedSixMin")
        }

        val flags = Json.createArrayBuilder()
        if (it.i != 0) flags.add("inferred")
        if (it.f != 0) flags.add("flatToleranceLimitExceeded")
        if (it.r != 0) flags.add("rateOfChangeToleranceLimitExceeded")
        if (it.t != 0) flags.add("temperatureToleranceLimitExceeded")
        val dataArray = Json.createArrayBuilder()
        dataArray.add(it.wl)
        dataArray.add(it.sigma)
        dataArray.add(flags)
        dpBuilder.add(dataArray)

        nextExpectedTimestamp = nextExpectedTimestamp.plusMinutes(6)
    }

    builder.add("data", dpBuilder)
    return builder.build()
}

fun WaterLevelRawSixMinMeasurements.toJSON(stationId: String, date: LocalDate): JsonObject {
    val builder = Json.createObjectBuilder()
    builder.add("stationId", stationId)
    builder.add("dateUTC", date.toString())
    builder.add("dataPoints", this.data.item.size)
    builder.add("packedDataFormat", waterLevelRawSixMinPackedDataFormat)

    if (this.data.item.size == 0) {
        log.error("NOAA did not provide raw data for $stationId")
        throw BadGatewayException("No data provided for station $stationId")
    }

    val firstDataPoint = this.data.item[0]
    val startTimestamp = noaaTimestampToLocalDateTime(firstDataPoint.timeStamp)

    if (startTimestamp != date.atStartOfDay()) {
        log.error("First timestamp for station $stationId does not start at start of day: $startTimestamp vs. $date")
        throw BadGatewayException("Expected start timestamp to be at start of day")
    }

    val dpBuilder = Json.createArrayBuilder()

    // This is the next expected timestamp.
    var nextExpectedTimestamp = startTimestamp

    this.data.item.forEach {
        val wlTimestamp = noaaTimestampToLocalDateTime(it.timeStamp)
        while (wlTimestamp.isAfter(nextExpectedTimestamp)) {
            // Indicate missing data points.
            log.warn("Timestamp for station $stationId overshot expected: nextExpectedTimestamp=$nextExpectedTimestamp, wlTimestamp=$wlTimestamp")
            dpBuilder.addNull()
            nextExpectedTimestamp = nextExpectedTimestamp.plusMinutes(6)
        }

        if (wlTimestamp != nextExpectedTimestamp) {
            log.error("Timestamp for station $stationId undershot expected: nextExpectedTimestamp=$nextExpectedTimestamp, wlTimestamp=$wlTimestamp")
            throw BadGatewayException("Invalid timestamp from NOAA WaterLevelRawSixMin")
        }

        val flags = Json.createArrayBuilder()
        if (it.f != 0) flags.add("flatToleranceLimitExceeded")
        if (it.r != 0) flags.add("rateOfChangeToleranceLimitExceeded")
        if (it.l != 0) flags.add("waterLevelLimitExceeded")

        val dataArray = Json.createArrayBuilder()
        dataArray.add(it.wl)
        dataArray.add(it.sigma)
        dataArray.add(it.o)
        dataArray.add(flags)
        dpBuilder.add(dataArray)

        nextExpectedTimestamp = nextExpectedTimestamp.plusMinutes(6)
    }

    builder.add("data", dpBuilder)
    return builder.build()
}

fun PredictionsValues.toJSON(stationId: String, date: LocalDate): JsonObject {
    val builder = Json.createObjectBuilder()
    builder.add("stationId", stationId)
    builder.add("dateUTC", date.toString())
    builder.add("dataPoints", this.data.item.size)
    builder.add("packedDataFormat", predictionsPackedDataFormat)

    if (this.data.item.size == 0)
        throw BadGatewayException("No data provided for station $stationId")

    val firstDataPoint = this.data.item[0]
    val startTimestamp = noaaTimestampToLocalDateTime(firstDataPoint.timeStamp)

    if (startTimestamp != date.atStartOfDay()) {
        log.error("First timestamp for station $stationId does not start at start of day: $startTimestamp vs. $date")
        throw BadGatewayException("Expected start timestamp to be at start of day")
    }

    val dpBuilder = Json.createArrayBuilder()

    // This is the next expected timestamp.
    var nextExpectedTimestamp = startTimestamp

    this.data.item.forEach {
        val predictionTimestamp = noaaTimestampToLocalDateTime(it.timeStamp)
        while (predictionTimestamp.isAfter(nextExpectedTimestamp)) {
            // Indicate missing data points.
            log.warn("Timestamp for station $stationId overshot expected: nextExpectedTimestamp=$nextExpectedTimestamp, wlTimestamp=$predictionTimestamp")
            dpBuilder.addNull()
            nextExpectedTimestamp = nextExpectedTimestamp.plusMinutes(6)
        }

        if (predictionTimestamp != nextExpectedTimestamp) {
            log.error("Timestamp for station $stationId undershot expected: nextExpectedTimestamp=$nextExpectedTimestamp, wlTimestamp=$predictionTimestamp")
            throw BadGatewayException("Invalid timestamp from NOAA WaterLevelVerifiedSixMin")
        }

        val dataArray = Json.createArrayBuilder()
        dataArray.add(it.pred)
        dpBuilder.add(dataArray)
        nextExpectedTimestamp = nextExpectedTimestamp.plusMinutes(6)
    }

    builder.add("data", dpBuilder)
    return builder.build()
}

fun HighLowValues.toJSON(stationId: String, date: LocalDate): JsonObject {
    val builder = Json.createObjectBuilder()
    builder.add("stationId", stationId)
    builder.add("dataPoints", this.highLowValues.item.size)
    builder.add("dateLocalTimeZone", date.toString())
    builder.add("packedDataFormat", extremaPackedDataFormat)

    val dpBuilder = Json.createArrayBuilder()

    this.highLowValues.item.forEach { dataForDate ->
        val dateString = dataForDate.date

        dataForDate.data.forEach {
            val timestampString = dateString + " " + it.time
            val timestamp = noaaTimestampToLocalDateTime(timestampString)

            val dataArray = Json.createArrayBuilder()
            dataArray.add(timestamp.toString())
            dataArray.add(it.pred)
            dataArray.add(it.type)

            dpBuilder.add(dataArray)
        }
    }

    builder.add("data", dpBuilder)
    return builder.build()
}

/**
 *  Convert a NOAA date string in YYYYMMDD format to a Java LocalDate object.
 *  @param dateString   The string to convert
 *  @return The equivalent Java LocalDate object
 *  @throws IllegalArgumentException if the format of dateString is invalid.
 */
fun dateStringToLocalDate(dateString: String): LocalDate {
    if (dateString.length != 8)
        throw IllegalArgumentException("dateString must be exactly 8 digits (YYYYMMDD)")

    val yearString = dateString.substring(0, 4)
    val monthString = dateString.substring(4, 6)
    val dayString = dateString.substring(6, 8)
    val year: Int
    val month: Int
    val day: Int

    try {
        year = yearString.toInt()
        month = monthString.toInt()
        day = dayString.toInt()
    }
    catch (e: NumberFormatException) {
        throw IllegalArgumentException("dateString cannot contain non-digits")
    }

    try {
        return LocalDate.of(year, month, day)
    }
    catch (e: DateTimeException) {
        throw IllegalArgumentException("dateString must be a valid date in YYYYMMDD format")
    }
}
