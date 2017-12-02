package org.kanga.tideserver
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.model.AmazonS3Exception
import com.amazonaws.services.s3.model.ObjectMetadata
import com.amazonaws.services.s3.model.S3Object
import gov.noaa.nos.coops.opendap.webservices.activestations.ActiveStationsService
import gov.noaa.nos.coops.opendap.webservices.activestations.Location
import gov.noaa.nos.coops.opendap.webservices.activestations.Metadata
import gov.noaa.nos.coops.opendap.webservices.activestations.Parameter
import gov.noaa.nos.coops.opendap.webservices.activestations.Station
import gov.noaa.nos.coops.opendap.webservices.activestations.Stations
import gov.noaa.nos.coops.opendap.webservices.waterlevelrawsixmin.Data as RawData
import gov.noaa.nos.coops.opendap.webservices.waterlevelrawsixmin.Parameters as RawParameters
import gov.noaa.nos.coops.opendap.webservices.waterlevelrawsixmin.WaterLevelRawSixMinMeasurements
import gov.noaa.nos.coops.opendap.webservices.waterlevelrawsixmin.WaterLevelRawSixMinService
import gov.noaa.nos.coops.opendap.webservices.waterlevelverifiedsixmin.Data as VerifiedData
import gov.noaa.nos.coops.opendap.webservices.waterlevelverifiedsixmin.Parameters as VerifiedParameters
import gov.noaa.nos.coops.opendap.webservices.waterlevelverifiedsixmin.WaterLevelVerifiedSixMinMeasurements
import gov.noaa.nos.coops.opendap.webservices.waterlevelverifiedsixmin.WaterLevelVerifiedSixMinService
import org.apache.http.HttpStatus
import java.nio.charset.Charset
import java.time.DateTimeException
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.ZoneOffset
import java.util.Date
import java.util.regex.Pattern
import javax.json.Json
import javax.json.JsonArray
import javax.json.JsonObject
import javax.json.JsonValue
import org.apache.log4j.Logger

val bufferSize = 1.shl(20)
val utf8 = Charset.forName("utf8")!!
val activeStationsService = ActiveStationsService().activeStations!!
val waterLevelVerifiedService = WaterLevelVerifiedSixMinService().waterLevelVerifiedSixMin!!
val waterLevelRawService = WaterLevelRawSixMinService().waterLevelRawSixMin!!
val log : Logger = Logger.getLogger("org.kanga.tideserver.noaa")

val httpListSplitter = Regex("\\s*,\\s*")
val stationListRegex = Pattern.compile("^station(?:/?)$")!!
val stationWaterLevelVerifiedRegex = Pattern.compile(
    "^station/(?<stationId>[^/]+)/water-level/(?<date>\\d{8})/verified$")!!
val stationWaterLevelPreliminaryRegex = Pattern.compile(
        "^station/(?<stationId>[^/]+)/water-level/(?<date>\\d{8})/preliminary$")!!
val waterLevelMinDate = LocalDate.of(1990, 1, 1).atStartOfDay(ZoneOffset.UTC)!!
val waterLevelVerifiedSixMinPackedDataFormat = Json.createArrayBuilder(listOf("waterLevel", "sigma", "flags")).build()!!
val waterLevelRawSixMinPackedDataFormat = Json.createArrayBuilder(listOf("waterLevel", "sigma", "samplesOutsideThreeSigma", "flags")).build()!!

/**
 *  The return value from a cached NOAA oceanographic service request.
 *  @property httpStatus    The HTTP status code to return to the client.
 *  @property body      The JSON-formatted result body.
 *  @property headers   The headers to return from the request.
 */
data class RequestResult(val httpStatus: Int, val body: String, val headers: Map<String, String>)

/**
 *  The return value from a live NOAA oceanographic service request.
 *  @property body      The result from the request, as a JSON value (not string).
 *  @property expires   If non-null, the time when the page should expire from the cache.
 */
data class NOAAResult(val body: JsonValue, val expires: Duration?)

/**
 *  NOAA oceanographic services as a REST endpoint, with data persisted to S3. Data is returned in JSON format.
 *
 *  @constructor Create a new S3CachedNOAAOceanographicJSON endpoint.
 *  @property s3            A handle to the S3 service.
 *  @property bucketName    The bucket to use to persist data.
 *  @property prefix        The prefix within the bucket.
 */
@Suppress("MemberVisibilityCanPrivate")
class S3CachedNOAAOceanographicJSON constructor(
        private val s3: AmazonS3, private val bucketName: String, private val prefix: String)
{
    @Suppress("unused")
    constructor(s3: AmazonS3, bucketName: String) : this(s3, bucketName, "")

    fun get(path: String, queryStringParameters: Map<String, Any>, headers: Map<String, Any>): RequestResult {
        val responseHeaders = mutableMapOf<String, String>("Content-Type" to "application/json")
        val requestCacheControl = (headers.get("cache-control")?.toString() ?: "").split(httpListSplitter).map {
            it.toLowerCase()
        }

        if (path.isEmpty() || path[0] != '/') {
            log.warn("Client requested invalid path")
            throw NotFoundException()
        }

        if ("no-cache" !in requestCacheControl) {
            try {
                log.debug("Requesting path from S3: $path")
                val s3Object = s3.getObject(bucketName, prefix + path.substring(1))!!
                val expires = s3Object.objectMetadata?.httpExpiresDate?.toInstant()
                val now = Instant.now()

                when {
                    expires == null -> {
                        log.info("Data found in S3; no expiration set; defaulting to 30 days")
                        responseHeaders["Cache-Control"] = "max-age=${thirtyDays.seconds}"
                        return RequestResult(HttpStatus.SC_OK, readS3ObjectAsString(s3Object), responseHeaders)
                    }
                    expires.isBefore(now) -> {
                        log.info("Data found in S3; expires on $expires")
                        val duration = Duration.between(now, expires)
                        responseHeaders["Cache-Control"] = "max-age=${duration.seconds}"
                        return RequestResult(HttpStatus.SC_OK, readS3ObjectAsString(s3Object), responseHeaders)
                    }
                    else -> {
                        log.info("Data found in S3 but expired on " + expires)
                    }
                }
            } catch (e: AmazonS3Exception) {
                if (e.errorCode != "NoSuchKey") {
                    log.error("S3 returned exception:", e)
                    throw BadGatewayException("An internal error occurred while serving your request")
                }
            }
        }

        val noaaResult = makeNOAARequest(path)
        val metadata = ObjectMetadata()
        metadata.contentType = "application/json"
        if (noaaResult.expires != null) {
            val expires = Instant.now().plus(noaaResult.expires)
            metadata.httpExpiresDate = Date.from(expires)
        }
        val body = noaaResult.body.toString()
        val bodyStream = body.byteInputStream()

        if ("no-store" in requestCacheControl) {
            responseHeaders["Cache-Control"] = "no-cache, no-store, must-revalidate"
        } else {
            val cacheDuration = noaaResult.expires ?: thirtyDays
            s3.putObject(bucketName, prefix + path.substring(1), bodyStream, metadata)
            responseHeaders["Cache-Control"] = "max-age=${cacheDuration.seconds}"
        }

        return RequestResult(HttpStatus.SC_OK, body, responseHeaders)
    }

    fun makeNOAARequest(path: String): NOAAResult {
        // This should use a routes-style mechanism, but Ktor doesn't make this easy to access independently
        if (stationListRegex.matcher(path)!!.matches()) return getActiveStations()

        val stationWaterLevelVerifiedMatch = stationWaterLevelVerifiedRegex.matcher(path)!!
        if (stationWaterLevelVerifiedMatch.matches()) {
            return getStationWaterLevelVerified(
                stationWaterLevelVerifiedMatch.group("stationId"),
                stationWaterLevelVerifiedMatch.group("date"))
        }

        val stationWaterLevelPreliminaryMatch = stationWaterLevelPreliminaryRegex.matcher(path)!!
        if (stationWaterLevelPreliminaryMatch.matches()) {
            return getStationWaterLevelRaw(
                    stationWaterLevelPreliminaryMatch.group("stationId"),
                    stationWaterLevelPreliminaryMatch.group("date"))
        }

        throw NotFoundException(path)
    }

    fun getActiveStations(): NOAAResult {
        val stations = activeStationsService.activeStations.stations ?: (
            throw IllegalStateException("Stations returned by NOAA web service is null"))
        return NOAAResult(stations.toJSON(), thirtyDays)
    }

    fun getStationWaterLevelVerified(stationId: String, dateString: String): NOAAResult {
        // Validate that date makes sense and is in the past
        val date = dateStringToLocalDate(dateString).atStartOfDay(ZoneOffset.UTC)
        val now = ZonedDateTime.now(ZoneOffset.UTC)

        if (now.isBefore(date)) {
            throw ForbiddenException(
                "Data is not yet available",
                headers = hashMapOf("Retry-After" to toHTTPDateString(date)))
        }

        if (waterLevelMinDate.isAfter(date)) {
            throw ForbiddenException("Data is not available before $waterLevelMinDate")
        }

        val params = VerifiedParameters()
        params.stationId = stationId
        params.beginDate = dateString
        params.endDate = dateString
        params.timeZone = 0 // UTC
        params.datum = "MLLW" // Mean lower low-water
        params.unit = 0 // Meters
        val measurement = waterLevelVerifiedService.getWaterLevelVerifiedSixMin(params)
        return NOAAResult(measurement.toJSON(stationId), null)
    }

    fun getStationWaterLevelRaw(stationId: String, dateString: String): NOAAResult {
        // Validate that date makes sense and is in the past
        val date = dateStringToLocalDate(dateString).atStartOfDay(ZoneOffset.UTC)
        val now = ZonedDateTime.now(ZoneOffset.UTC)

        if (now.isBefore(date)) {
            throw ForbiddenException(
                "Data is not yet available",
                headers = hashMapOf("Retry-After" to toHTTPDateString(date)))
        }

        if (waterLevelMinDate.isAfter(date)) {
            throw ForbiddenException("Data is not available before $waterLevelMinDate")
        }

        val params = RawParameters()
        params.stationId = stationId
        params.beginDate = dateString
        params.endDate = dateString
        params.timeZone = 0 // UTC
        params.datum = "MLLW" // Mean lower low-water
        params.unit = 0 // Meters
        val measurement = waterLevelRawService.getWaterLevelRawSixMin(params)
        return NOAAResult(measurement.toJSON(stationId), null)
    }
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

/**
 *  Given an S3 object, read the entire body as a UTF-8 string.
 *  @param s3Object     The S3Object to read.
 *  @return The body of the object decoded as a UTF-8 string.
 */
fun readS3ObjectAsString(s3Object: S3Object): String {
    var buffer = ByteArray(bufferSize)
    var totalRead = 0

    while (true) {
        val nRead = s3Object.objectContent.read(buffer, totalRead, buffer.size - totalRead)
        if (nRead <= 0) {
            break
        }

        totalRead += nRead
        if (totalRead == buffer.size) {
            buffer = buffer.copyOf(buffer.size + bufferSize)
        }
    }

    return String(buffer, 0, totalRead, utf8)
}

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

fun WaterLevelVerifiedSixMinMeasurements.toJSON(stationId: String): JsonObject {
    val builder = Json.createObjectBuilder()
    builder.add("stationId", stationId)
    builder.add("dataPoints", this.data.item.size)
    builder.add("packedDataFormat", waterLevelVerifiedSixMinPackedDataFormat)

    if (this.data.item.size == 0)
        throw BadGatewayException("No data provided for station $stationId")

    val firstDataPoint = this.data.item[0]
    val startTimestamp = noaaTimestampToLocalDateTime(firstDataPoint.timeStamp)

    if (startTimestamp.hour != 0 || startTimestamp.minute != 0 || startTimestamp.second != 0) {
        log.error("Timestamp for station $stationId does not start at start of day: $startTimestamp")
        throw BadGatewayException("Expected start timestamp to be at start of day")
    }

    builder.add("date", startTimestamp.toLocalDate().toString())
    val dpBuilder = Json.createArrayBuilder()

    // This is the next expected timestamp.
    var nextExpectedTimestamp = startTimestamp

    this.data.item.forEach {
        val wlTimestamp = noaaTimestampToLocalDateTime(it.timeStamp)
        while (wlTimestamp.isAfter(nextExpectedTimestamp)) {
            // Indicate missing data points.
            dpBuilder.add(null as? JsonValue?)
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
    }

    builder.add("data", dpBuilder)
    return builder.build()
}

fun WaterLevelRawSixMinMeasurements.toJSON(stationId: String): JsonObject {
    val builder = Json.createObjectBuilder()
    builder.add("stationId", stationId)
    builder.add("dataPoints", this.data.item.size)
    builder.add("packedDataFormat", waterLevelRawSixMinPackedDataFormat)

    if (this.data.item.size == 0) {
        log.error("NOAA did not provide raw data for $stationId")
        throw BadGatewayException("No data provided for station $stationId")
    }

    val firstDataPoint = this.data.item[0]
    val startTimestamp = noaaTimestampToLocalDateTime(firstDataPoint.timeStamp)

    if (startTimestamp.hour != 0 || startTimestamp.minute != 0 || startTimestamp.second != 0) {
        log.error("Timestamp for station $stationId does not start at start of day: $startTimestamp")
        throw BadGatewayException("Expected start timestamp to be at start of day")
    }

    builder.add("date", startTimestamp.toLocalDate().toString())
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
