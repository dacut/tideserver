package org.kanga.tideserver
import com.amazonaws.services.cloudwatch.model.MetricDatum
import com.amazonaws.services.cloudwatch.model.StandardUnit.Count
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.model.AmazonS3Exception
import com.amazonaws.services.s3.model.ObjectMetadata
import com.amazonaws.services.s3.model.S3Object
import gov.noaa.nos.coops.opendap.webservices.activestations.ActiveStationsService
import gov.noaa.nos.coops.opendap.webservices.activestations.Location as ActiveStationsLocation
import gov.noaa.nos.coops.opendap.webservices.activestations.Metadata as ActiveStationsMetadata
import gov.noaa.nos.coops.opendap.webservices.activestations.Parameter as ActiveStationsParameter
import gov.noaa.nos.coops.opendap.webservices.activestations.Station as ActiveStationsStation
import gov.noaa.nos.coops.opendap.webservices.activestations.Stations as ActiveStationsStations
import gov.noaa.nos.coops.opendap.webservices.highlowtidepred.Data as HighLowData
import gov.noaa.nos.coops.opendap.webservices.highlowtidepred.HighlowtidepredService as HighLowService
import gov.noaa.nos.coops.opendap.webservices.highlowtidepred.Parameters as HighLowParameters
import gov.noaa.nos.coops.opendap.webservices.predictions.Parameters as PredictionsParameters
import gov.noaa.nos.coops.opendap.webservices.predictions.Data as PredictionsData
import gov.noaa.nos.coops.opendap.webservices.predictions.PredictionsService
import gov.noaa.nos.coops.opendap.webservices.waterlevelrawsixmin.Data as RawData
import gov.noaa.nos.coops.opendap.webservices.waterlevelrawsixmin.Parameters as RawParameters
import gov.noaa.nos.coops.opendap.webservices.waterlevelrawsixmin.WaterLevelRawSixMinService
import gov.noaa.nos.coops.opendap.webservices.waterlevelverifiedsixmin.Data as VerifiedData
import gov.noaa.nos.coops.opendap.webservices.waterlevelverifiedsixmin.Parameters as VerifiedParameters
import gov.noaa.nos.coops.opendap.webservices.waterlevelverifiedsixmin.WaterLevelVerifiedSixMinService

import org.apache.http.HttpStatus
import java.nio.charset.Charset
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.ZoneOffset
import java.util.Date
import java.util.regex.Pattern
import javax.json.JsonValue
import org.apache.log4j.Logger
import java.security.MessageDigest
import java.util.regex.Matcher

val bufferSize = 1.shl(20)
val utf8 = Charset.forName("utf8")!!
val activeStationsService = ActiveStationsService().activeStations!!
val waterLevelVerifiedService = WaterLevelVerifiedSixMinService().waterLevelVerifiedSixMin!!
val waterLevelRawService = WaterLevelRawSixMinService().waterLevelRawSixMin!!
val predictionsService = PredictionsService().predictions!!
val highLowService = HighLowService().highlowtidepred!!
val log : Logger = Logger.getLogger("org.kanga.tideserver.noaa")

val stationListRegex = Pattern.compile("^/stations$")!!
val stationWaterLevelVerifiedRegex = Pattern.compile(
    "^/station/(?<stationId>[^/]+)/water-level/(?<date>\\d{8})/verified$")!!
val stationWaterLevelPreliminaryRegex = Pattern.compile(
    "^/station/(?<stationId>[^/]+)/water-level/(?<date>\\d{8})/preliminary$")!!
val stationWaterLevelPredictedRegex = Pattern.compile(
    "^/station/(?<stationId>[^/]+)/water-level/(?<date>\\d{8})/predicted$")!!
val stationExtremaPredictionRegex = Pattern.compile(
    "^/station/(?<stationId>[^/]+)/extrema/(?<date>\\d{8})/predicted$")!!

val falseStringValues = Pattern.compile("^fFnN0")!!

val waterLevelMinDate = LocalDate.of(1990, 1, 1).atStartOfDay(ZoneOffset.UTC)!!

/**
 *  The return value from a live NOAA oceanographic service request.
 *  @property body      The result from the request, as a JSON value (not string).
 *  @property maxAge    If non-null, the duration after which the page should expire from the cache.
 */
data class NOAAResult(val body: JsonValue, val maxAge: Duration?)

interface NoaaApi {
    val apiName: String
    fun execute(handler: NOAARequestHandler): NOAAResult
}

object GetActiveStations: NoaaApi {
    override val apiName: String
        get() = "GetActiveStations"

    override fun execute(handler: NOAARequestHandler): NOAAResult {
        val stations = Timer(handler.metrics, "Time", "Service=NOAA,API=GetActiveStations").time {
            activeStationsService.activeStations.stations ?: (
                throw IllegalStateException("Stations returned by NOAA web service is null"))
        }

        return NOAAResult(stations.toJSON(), days30)
    }
}

object GetStationWaterLevelVerified: NoaaApi {
    override val apiName: String
        get() = "GetStationWaterLevelVerified"

    override fun execute(handler: NOAARequestHandler): NOAAResult {
        // Validate that date makes sense and is in the past
        val stationId = handler.pathMatcher.group("stationId")!!
        val dateString = handler.pathMatcher.group("date")!!

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
        val measurement = Timer(
            handler.metrics, "Time", "Service=NOAA,API=GetWaterLevelVerifiedSixMin").time {
            waterLevelVerifiedService.getWaterLevelVerifiedSixMin(params)
        }
        // Expiration date depends on distance between now and date
        val measurementAge = Duration.between(date, now)
        val expires = when {
            measurementAge < days1 -> Duration.ZERO            // Don't cache same-day measurements
            measurementAge < days7 -> days1                // Same-week measurements valid for one day
            measurementAge < days365 -> days30              // One year, valid for 30 days
            else -> null                                        // Otherwise, valid forever.
        }

        return NOAAResult(measurement.toJSON(stationId, date.toLocalDate()), expires)
    }
}

object GetStationWaterLevelPreliminary: NoaaApi {
    override val apiName: String
        get() = "GetStationWaterLevelPreliminary"

    override fun execute(handler: NOAARequestHandler): NOAAResult {
        // Validate that date makes sense and is in the past
        val stationId = handler.pathMatcher.group("stationId")!!
        val dateString = handler.pathMatcher.group("date")!!

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
        val measurement = Timer(
            handler.metrics, "Time", "Service=NOAA,API=GetWaterLevelRawSixMin").time {
            waterLevelRawService.getWaterLevelRawSixMin(params)
        }

        // Expiration date depends on distance between now and date
        val measurementAge = Duration.between(date, now)
        val expires = when {
            measurementAge < days1 -> Duration.ZERO            // Don't cache same-day measurements
            measurementAge < days7 -> days1                // Same-week measurements valid for one day
            measurementAge < days365 -> days30              // One year, valid for 30 days
            else -> null                                        // Otherwise, valid forever.
        }

        return NOAAResult(measurement.toJSON(stationId, date.toLocalDate()), expires)
    }
}

object GetStationWaterLevelPredicted: NoaaApi {
    override val apiName: String
        get() = "GetStationWaterLevelPredicted"

    override fun execute(handler: NOAARequestHandler): NOAAResult {
        // Validate that date makes sense and is in the past
        val stationId = handler.pathMatcher.group("stationId")!!
        val dateString = handler.pathMatcher.group("date")!!

        val date = dateStringToLocalDate(dateString).atStartOfDay(ZoneOffset.UTC)
        val now = ZonedDateTime.now(ZoneOffset.UTC)

        if (waterLevelMinDate.isAfter(date)) {
            throw ForbiddenException("Data is not available before $waterLevelMinDate")
        }

        val params = PredictionsParameters()
        params.stationId = stationId
        params.beginDate = dateString
        params.endDate = dateString
        params.timeZone = 0 // UTC
        params.datum = "MLLW" // Mean lower low-water
        params.unit = 0 // Meters
        params.dataInterval = 6
        val predictions = Timer(
            handler.metrics, "Time", "Service=NOAA,GetPredictions").time {
            predictionsService.getPredictions(params)
        }

        // Expiration date depends on distance between date and now
        val measurementAge = Duration.between(now, date)
        val expires = when {
            measurementAge < Duration.ZERO -> null              // Cache old measurements indefinitely
            measurementAge < days1 -> Duration.ZERO             // Don't cache same-day measurements
            measurementAge < days7 -> days1                     // Same-week measurements valid for one day
            else -> days30                                      // Otherwise, valid for 30 days
        }

        return NOAAResult(predictions.toJSON(stationId, date.toLocalDate()), expires)
    }
}

object GetStationExtremaPredicted: NoaaApi {
    override val apiName: String
        get() = "GetStationExtremaPredicted"

    override fun execute(handler: NOAARequestHandler): NOAAResult {
        // Validate that date makes sense and is in the past
        val stationId = handler.pathMatcher.group("stationId")!!
        val dateString = handler.pathMatcher.group("date")!!

        val date = dateStringToLocalDate(dateString).atStartOfDay(ZoneOffset.UTC)
        val now = ZonedDateTime.now(ZoneOffset.UTC)

        if (waterLevelMinDate.isAfter(date)) {
            throw ForbiddenException("Data is not available before $waterLevelMinDate")
        }

        val params = HighLowParameters()
        params.stationId = stationId
        params.beginDate = dateString
        params.endDate = dateString
        params.timeZone = 1 // UTC -- this is *reversed* from the docs
        params.datum = "MLLW" // Mean lower low-water
        params.unit = 1 // Meters -- this is *reversed* from the docs
        val hlPredictions = Timer(
            handler.metrics, "Time", "Service=NOAA,API=GetHighLowTidePredictions").time {
            highLowService.getHighLowTidePredictions(params)
        }
        // Expiration date depends on distance between date and now
        val measurementAge = Duration.between(now, date)
        val expires = when {
            measurementAge < Duration.ZERO -> null              // Cache old measurements indefinitely
            measurementAge < days1 -> Duration.ZERO             // Don't cache same-day measurements
            measurementAge < days7 -> days1                     // Same-week measurements valid for one day
            else -> days30                                      // Otherwise, valid for 30 days
        }

        return NOAAResult(hlPredictions.toJSON(stationId, date.toLocalDate()), expires)
    }
}


val pathHandlers= listOf(
    stationListRegex to GetActiveStations,
    stationWaterLevelVerifiedRegex to GetStationWaterLevelVerified,
    stationWaterLevelPreliminaryRegex to GetStationWaterLevelPreliminary,
    stationWaterLevelPredictedRegex to GetStationWaterLevelPredicted,
    stationExtremaPredictionRegex to GetStationExtremaPredicted
)

/**
 *  NOAA oceanographic services as a REST endpoint, with data persisted to S3. Data is returned in JSON format.
 *
 *  @constructor Create a new NOAARequestHandler endpoint.
 *  @property s3            A handle to the S3 service.
 *  @property bucketName    The bucket to use to persist data.
 *  @property prefix        The prefix within the bucket.
 */
@Suppress("MemberVisibilityCanPrivate")
class NOAARequestHandler constructor(
    val request: Request, val s3: AmazonS3, val bucketName: String, val prefix: String) {
    val metrics = mutableListOf<MetricDatum>()
    val response = Response(headers = mapOf("Content-Type" to "application/json"))
    val pathMatcher: Matcher
        get() = this._matcher ?: throw HTTPServerException("Invalid server state")

    val isS3CacheEnabled: Boolean
        get() {
            return when {
                request.query.containsKey("no-cache") -> false
                falseStringValues.matcher(request.query["cache"] ?: "").matches() -> false
                else -> true
            }
        }

    val isS3StoreEnabled: Boolean
        get() {
            return when {
                request.query.containsKey("no-store") -> false
                falseStringValues.matcher(request.query["store"] ?: "").matches() -> false
                else -> true
            }
        }

    val s3Path: String
        get() = prefix + request.path.substring(1)

    private var _matcher: Matcher? = null

    fun handleRequest() {
        if (!(request.method == "GET" || request.method == "HEAD")) {
            log.warn("Client used illegal method: ${request.method}")
            throw MethodNotAllowedException()
        }

        if (request.path.length < 2 || request.path[0] != '/') {
            log.warn("Client requested invalid path: ${request.path}")
            throw NotFoundException()
        }

        if (isS3CacheEnabled && getS3CachedObject())
            return

        val noaaResult: NOAAResult

        try {
            noaaResult = makeNOAARequest()
            response.statusCode = HttpStatus.SC_OK
            response.body = noaaResult.body.toString()
            response.headers["ETag"] = etagForString(response.body)
            when {
                noaaResult.maxAge === null -> response.headers["Cache-Control"] = "public, max-age=${days30.seconds}"
                else -> {
                    response.headers["Cache-Control"] = "public"
                    response.headers["Expires"] = toHTTPDateString(Instant.now().plus(noaaResult.maxAge))
                }
            }
        } catch (e: Exception) {
            // Do we have valid cache data?
            if (response.body.isEmpty() || response.headers["ETag"] === null)
                throw e

            // Yep; return it, but don't cache the output
            log.warn("NOAA request failed; returning stale cached data.", e)
            response.headers["Cache-Control"] = "no-cache, no-store, must-revalidate"
            response.statusCode = HttpStatus.SC_OK
            return
        }

        if (isS3StoreEnabled) {
            putS3CachedObject(noaaResult)
        } else {
            response.headers["Cache-Control"] = "private, no-cache, no-store, must-revalidate"
        }

        return
    }

    fun getS3CachedObject(): Boolean {
        try {
            log.debug("Requesting path from S3: $s3Path")
            val s3Object = Timer(metrics,"Time", "Service=S3,API=GetObject").time {
                val s3Object = s3.getObject(bucketName, s3Path)!!
                response.body = readS3ObjectAsString(s3Object)
                s3Object
            }

            val expires = s3Object.objectMetadata?.httpExpiresDate?.toInstant()
            response.headers["ETag"] = s3Object.objectMetadata.eTag

            val now = Instant.now()
            when {
                (expires == null || expires.isAfter(now)) -> {
                    metrics.add("S3CacheHit", 1.0, Count)
                    if (expires == null) {
                        // Tell browser and CloudFront to cache for up to 30 days
                        log.info("Data found in S3 with no expiration date")
                        response.headers["Cache-Control"] = "public, max-age=${days30.seconds}"
                    } else {
                        // Use the Expires header.
                        log.info("Data found in S3; maxAge=$expires")
                        response.headers["Cache-Control"] = "public"
                        response.headers["Expires"] = toHTTPDateString(expires)
                    }

                    return true
                }
                else -> {
                    metrics.add("S3CacheStaleHit", 1.0, Count)
                    log.info("Data found in S3 but expired on " + expires)
                    return false
                }
            }
        } catch (e: AmazonS3Exception) {
            if (e.errorCode != "NoSuchKey") {
                metrics.add("S3CacheError", 1.0, Count)
                log.error("S3 returned exception:", e)
                throw BadGatewayException("An internal error occurred while serving your request")
            }

            metrics.add("S3CacheMiss", 1.0, Count)
            return false
        }
    }

    fun putS3CachedObject(noaaResult: NOAAResult) {
        val metadata = ObjectMetadata()
        metadata.contentType = response.headers["Content-Type"]

        if (noaaResult.maxAge != null)
            metadata.httpExpiresDate = Date.from(Instant.now().plus(noaaResult.maxAge))

        try {
            val bodyStream = response.body.byteInputStream()
            Timer(metrics, "Time", "Service=S3,API=PutObject").time {
                s3.putObject(bucketName, s3Path, bodyStream, metadata)
            }
            log.info("Data cached to S3")
        } catch (e: Exception) {
            log.error("Failed to cache data to S3 (will still return data to client)", e)
        }

        return
    }

    fun makeNOAARequest(): NOAAResult {
        // This should use a routes-style mechanism, but Ktor doesn't make this easy to access independently
        for ((regex, obj) in pathHandlers) {
            val matcher = regex.matcher(request.path)!!
            if (matcher.matches()) {
                _matcher = matcher
                response.headers["X-TideServer-Api"] = obj.apiName
                return obj.execute(this)
            }
        }

        throw NotFoundException(request.path)
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
