package org.kanga.tideserver
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClientBuilder
import com.amazonaws.services.cloudwatch.model.MetricDatum
import com.amazonaws.services.cloudwatch.model.PutMetricDataRequest
import com.amazonaws.services.cloudwatch.model.StandardUnit.Count
import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import org.apache.http.HttpStatus
import org.apache.log4j.Appender
import org.apache.log4j.Layout
import org.apache.log4j.Level
import org.apache.log4j.Logger
import org.apache.log4j.PatternLayout
import java.util.Enumeration
import javax.json.Json

val s3: AmazonS3 = AmazonS3ClientBuilder.defaultClient()!!
val cloudWatch = AmazonCloudWatchClientBuilder.defaultClient()!!
val bucketName = System.getenv("BUCKET")!!
val prefix = System.getenv("PREFIX") ?: ""
val metricNamespace = System.getenv("METRIC_NAMESPACE") ?: "TideServer"

@Suppress("UNCHECKED_CAST")
fun setupLogging() {
    val rootLogger: Logger = Logger.getRootLogger()
    val appenders: Enumeration<Appender> = rootLogger.allAppenders!! as Enumeration<Appender>
    while (appenders.hasMoreElements()) {
        val appender: Appender? = appenders.nextElement()
        val layout: Layout? = appender?.layout
        System.out.println("Appender ${appender?.name}: layout=$layout")

        if (layout is PatternLayout) {
            System.out.println("ConversionPattern: ${layout.conversionPattern}")
            layout.conversionPattern = "%d{yyyy-MM-dd HH:mm:ss} <%X{AWSRequestId}> %-5p %c:%m%n"
        }
    }
    Logger.getLogger("org.apache.http").level = Level.INFO
    Logger.getLogger("com.amazonaws.http").level = Level.INFO
}

fun logEvent(event: Map<String, Any>) {
    var eventLog = "Event:"
    event.forEach { (key, value) ->
        eventLog += "\n$key: $value"
    }
    eventLog += "\n"
    log.debug(eventLog)
}

@Suppress("UNCHECKED_CAST", "UNUSED_CLASS")
class Tideserver: RequestHandler<Map<String, Any>, Map<String, Any>> {
    private val metrics: MutableList<MetricDatum> = mutableListOf()

    init {
        setupLogging()
    }

    /**
     *  Wrapper for handling the current Lambda request.
     *
     *  This delegates to handleRequestInner. Upon completion, this method sends all metrics back to CloudWatch.
     */
    override fun handleRequest(event: Map<String, Any>?, p1: Context?): Map<String, Any> {
        metrics.clear()
        try {
            return handleRequestInner(event, p1)
        }
        finally {
            val putMetricData = PutMetricDataRequest().withNamespace(metricNamespace)
            val batch: MutableList<MetricDatum> = mutableListOf()

            fun flush() {
                cloudWatch.putMetricData(PutMetricDataRequest().withNamespace(metricNamespace).withMetricData(batch))
                batch.clear()
            }

            // CloudWatch only allows up to 20 metrics at a time.
            metrics.forEach {
                batch.add(it)
                if (batch.size == 20)
                    flush()
            }

            if (batch.size > 0)
                flush()
        }
    }

    /**
     *  The main logic for handling the current Lambda request.
     */
    @Suppress("unused_parameter")
    private fun handleRequestInner(event: Map<String, Any>?, p1: Context?): Map<String, Any> {
        val t = Timer(metrics, "Time", "API=ALL")
        t.time {

            // This shouldn't happen, but Kotlin doesn't know that.
            if (event == null) {
                log.error("Lambda event not initialized")
                return hashMapOf(
                    "statusCode" to HttpStatus.SC_INTERNAL_SERVER_ERROR,
                    "body" to "Internal server error")
            }

            logEvent(event)

            val path = (event["path"] ?: "").toString()
            val requestContext = (event["requestContext"] as? Map<String, Any>) ?: hashMapOf()
            val requestId = (requestContext["requestId"] ?: "").toString()
            val httpMethod: String = (event["httpMethod"] ?: "").toString()
            val request = Request(
                httpMethod, path, HTTPMap(event["queryStringParameters"] as? Map<String, String> ?: hashMapOf()),
                HTTPMap(event["headers"] as? Map<String, String> ?: hashMapOf()))

            val noaaHandler = NOAARequestHandler(request = request, s3 = s3, bucketName = bucketName, prefix = prefix)
            try {
                noaaHandler.handleRequest()
                metrics.addAll(noaaHandler.metrics)
                val apiName = noaaHandler.response.headers["X-TideServer-Api"] ?: "UNKNOWN"
                t.multiDimensions.add(parseDimensions("API=$apiName"))
                metrics.add("Success", 1.0, Count)
                return hashMapOf(
                    "statusCode" to noaaHandler.response.statusCode,
                    "body" to noaaHandler.response.body,
                    "headers" to noaaHandler.response.headers)
            } catch (e: Exception) {
                when (e) {
                    is HTTPClientException -> log.info("HTTPClientException", e)
                    is HTTPServerException -> log.error("HTTPServerException", e)
                    else -> log.fatal("Service Failure", e)
                }

                val body = Json.createObjectBuilder()
                val errorBody = Json.createObjectBuilder()
                val statusCode = when (e) {
                    is HTTPException -> e.statusCode
                    else -> HttpStatus.SC_INTERNAL_SERVER_ERROR
                }
                val message = when (e) {
                    is HTTPException -> e.message ?: getHTTPStatusMessage(e.statusCode)
                    else -> "Internal Server Error"
                }
                errorBody.add("Code", getHTTPStatusMessage(statusCode))
                errorBody.add("Message", message)
                errorBody.add("Resource", path)
                errorBody.add("RequestId", requestId)
                errorBody.add("EncodedStackTrace", encryptStackTrace(e))
                body.add("Error", errorBody)

                val headers = mutableMapOf<String, String>()

                when (e) {
                    is HTTPClientException -> {
                        // Client-side exceptions can be cached
                        headers["Cache-Control"] = "public, max-age=${hours1.seconds}"
                        metrics.add("ClientError", 1.0, Count)
                        metrics.add("ServerError", 0.0, Count)
                        metrics.add("ServerFailure", 0.0, Count)
                    }
                    is HTTPServerException -> {
                        // Don't cache server-side exceptions by default.
                        headers["Cache-Control"] = "no-cache, no-store, must-revalidate"
                        metrics.add("ClientError", 0.0, Count)
                        metrics.add("ServerError", 1.0, Count)
                        metrics.add("ServerFailure", 0.0, Count)
                    }
                    else -> {
                        metrics.add("ClientError", 0.0, Count)
                        metrics.add("ServerError", 0.0, Count)
                        metrics.add("ServerFailure", 1.0, Count)
                    }
                }

                // Add all the headers from the exception, overriding ours if necessary
                if (e is HTTPException)
                    headers.putAll(e.headers)

                val result = hashMapOf("statusCode" to statusCode, "body" to body.build().toString(), "headers" to headers)
                log.info("Returning $result")
                return result
            }
        }
    }
}

object TestActiveStations {
    @JvmStatic
    fun main(args: Array<String>) {
        // Enable debugging on SOAP transfers.
        System.setProperty("com.sun.xml.ws.transport.http.client.HttpTransportPipe.dump", "true")
        System.setProperty("com.sun.xml.internal.ws.transport.http.client.HttpTransportPipe.dump", "true")
        System.setProperty("com.sun.xml.ws.transport.http.HttpAdapter.dump", "true")
        System.setProperty("com.sun.xml.internal.ws.transport.http.HttpAdapter.dump", "true")

        val ts = Tideserver()
        val result = ts.handleRequest(hashMapOf("httpMethod" to "GET", "path" to "/station/"), null)
        System.out.println("StatusCode: " + result["statusCode"])
        System.out.println("Headers:")

        val headers = result["headers"]
        if (headers is Map<*, *>) {
            for (header in headers.entries) {
                System.out.println("${header.key}: ${header.value}")
            }
        }

        System.out.println("Content:")
        System.out.println(result["body"])
    }
}