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
import java.security.MessageDigest
import java.util.regex.Matcher

val bufferSize = 1.shl(20)
val utf8 = Charset.forName("utf8")!!
val activeStationsService = ActiveStationsService().activeStations!!
val waterLevelVerifiedService = WaterLevelVerifiedSixMinService().waterLevelVerifiedSixMin!!
val waterLevelRawService = WaterLevelRawSixMinService().waterLevelRawSixMin!!
val log : Logger = Logger.getLogger("org.kanga.tideserver.noaa")

val httpListSplitter = Regex("\\s*,\\s*")
val stationListRegex = Pattern.compile("^/stations$")!!
val stationWaterLevelVerifiedRegex = Pattern.compile(
    "^/station/(?<stationId>[^/]+)/water-level/(?<date>\\d{8})/verified$")!!
val stationWaterLevelPreliminaryRegex = Pattern.compile(
    "^/station/(?<stationId>[^/]+)/water-level/(?<date>\\d{8})/preliminary$")!!
val waterLevelMinDate = LocalDate.of(1990, 1, 1).atStartOfDay(ZoneOffset.UTC)!!
val waterLevelVerifiedSixMinPackedDataFormat = Json.createArrayBuilder(listOf("waterLevel", "sigma", "flags")).build()!!
val waterLevelRawSixMinPackedDataFormat = Json.createArrayBuilder(listOf("waterLevel", "sigma", "samplesOutsideThreeSigma", "flags")).build()!!

val pathHandlers = listOf(
    stationListRegex to ::getActiveStations,
    stationWaterLevelVerifiedRegex to ::getStationWaterLevelVerified,
    stationWaterLevelPreliminaryRegex to ::getStationWaterLevelRaw)

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

    @Suppress("unused_parameter")
    fun get(path: String, queryStringParameters: Map<String, String>, headers: Map<String, String>): RequestResult {
        val responseHeaders = mutableMapOf("Content-Type" to "application/json")
        val requestCacheControl = (headers["cache-control"] ?: "").split(httpListSplitter).map {
            it.toLowerCase()
        }

        if (path.isEmpty() || path[0] != '/') {
            log.warn("Client requested invalid path")
            throw NotFoundException()
        }

        var cachedData: String? = null
        var cachedETag: String? = null

        if ("no-cache" !in requestCacheControl) {
            try {
                log.debug("Requesting path from S3: $path")
                val s3Object = s3.getObject(bucketName, prefix + path.substring(1))!!
                val expires = s3Object.objectMetadata?.httpExpiresDate?.toInstant()
                val now = Instant.now()
                cachedData = readS3ObjectAsString(s3Object)
                cachedETag = s3Object.objectMetadata.eTag

                when {
                    (expires == null || expires.isAfter(now)) -> {
                        if (expires == null) {
                            // Tell browser and CloudFront to cache for up to 30 days
                            log.info("Data found in S3 with no expiration date")
                            responseHeaders["Cache-Control"] = "public, max-age=${days30.seconds}"
                        } else {
                            // Use the Expires header.
                            log.info("Data found in S3; expires=$expires")
                            responseHeaders["Cache-Control"] = "public"
                            responseHeaders["Expires"] = toHTTPDateString(expires)
                        }

                        responseHeaders["ETag"] = cachedETag
                        return RequestResult(HttpStatus.SC_OK, cachedData, responseHeaders)
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

        try {
            val noaaResult = makeNOAARequest(path)
            val metadata = ObjectMetadata()
            metadata.contentType = "application/json"

            val expires = if (noaaResult.expires != null) {
                val tmpExpires = Instant.now().plus(noaaResult.expires)
                metadata.httpExpiresDate = Date.from(tmpExpires)
                tmpExpires
            } else {
                null
            }

            val body = noaaResult.body.toString()

            if ("no-store" in requestCacheControl) {
                responseHeaders["Cache-Control"] = "no-cache, no-store, must-revalidate"
            } else {
                try {
                    val bodyStream = body.byteInputStream()
                    s3.putObject(bucketName, prefix + path.substring(1), bodyStream, metadata)
                    log.info("Data cached to S3")
                }
                catch (e: Exception) {
                    log.error("Failed to cache data to S3 (will still return data to client)", e)
                }

                if ("no-cache" in requestCacheControl) {
                    responseHeaders["Cache-Control"] = "no-cache"
                }
                else if (expires === null) {
                    responseHeaders["Cache-Control"] = "public, max-age=${days30.seconds}"
                } else {
                    responseHeaders["Cache-Control"] = "public"
                    responseHeaders["Expires"] = toHTTPDateString(expires)
                }
            }

            responseHeaders["ETag"] = etagForString(body)
            return RequestResult(HttpStatus.SC_OK, body, responseHeaders)
        }
        catch (e: Exception) {
            // Do we have valid cache data?
            if (cachedData === null || cachedETag === null)
                throw e

            // Yep; return it, but don't cache the output
            log.warn("NOAA request failed; returning stale cached data.", e)
            responseHeaders["ETag"] = cachedETag
            responseHeaders["Cache-Control"] = "public, no-cache, no-store, must-revalidate"

            return RequestResult(HttpStatus.SC_OK, cachedData, responseHeaders)
        }
    }

    fun makeNOAARequest(path: String): NOAAResult {
        // This should use a routes-style mechanism, but Ktor doesn't make this easy to access independently
        for ((regex, fn) in pathHandlers) {
            val matcher = regex.matcher(path)!!
            if (matcher.matches()) {
                return fn(matcher)
            }
        }

        throw NotFoundException(path)
    }
}

fun getActiveStations(@Suppress("UNUSED_PARAMETER") matcher: Matcher): NOAAResult {
    val stations = activeStationsService.activeStations.stations ?: (
        throw IllegalStateException("Stations returned by NOAA web service is null"))
    return NOAAResult(stations.toJSON(), days30)
}

fun getStationWaterLevelVerified(matcher: Matcher): NOAAResult {
    // Validate that date makes sense and is in the past
    val stationId = matcher.group("stationId")!!
    val dateString = matcher.group("date")!!

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
    // Expiration date depends on distance between now and date
    val measurementAge = Duration.between(date, now)
    val expires = when {
        measurementAge < days1 -> Duration.ZERO            // Don't cache same-day measurements
        measurementAge < days7 -> days1                // Same-week measurements valid for one day
        measurementAge < days365 -> days30              // One year, valid for 30 days
        else -> null                                        // Otherwise, valid forever.
    }

    return NOAAResult(measurement.toJSON(stationId), expires)
}

fun getStationWaterLevelRaw(matcher: Matcher): NOAAResult {
    // Validate that date makes sense and is in the past
    val stationId = matcher.group("stationId")!!
    val dateString = matcher.group("date")!!

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

    // Expiration date depends on distance between now and date
    val measurementAge = Duration.between(date, now)
    val expires = when {
        measurementAge < days1 -> Duration.ZERO            // Don't cache same-day measurements
        measurementAge < days7 -> days1                // Same-week measurements valid for one day
        measurementAge < days365 -> days30              // One year, valid for 30 days
        else -> null                                        // Otherwise, valid forever.
    }

    return NOAAResult(measurement.toJSON(stationId), expires)
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
 *  @param  s3Object     The S3Object to read.
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

/**
 *  Compute the ETag (MD5 hash, enclosed in double quotes) for the UTF-8 representation of a string.
 *  @param  s       The string to hash
 *  @return The ETag value of the UTF-8 representation of s
 */
fun etagForString(s: String): String {
    val md5 = MessageDigest.getInstance("MD5")
    val digest = md5.digest(s.toByteArray())
    val result = StringBuilder()
    result.append('"')
    for (i in digest) {
        val hex = i.toInt().toString(16)
        if (hex.length == 1)
            result.append('0')

        result.append(hex)
    }
    result.append('"')
    return result.toString()
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
