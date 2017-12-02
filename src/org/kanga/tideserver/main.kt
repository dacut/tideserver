package org.kanga.tideserver
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

val s3 : AmazonS3 = AmazonS3ClientBuilder.defaultClient()
val bucketName = System.getenv("BUCKET")!!
val prefix = System.getenv("PREFIX") ?: ""
val noaaServer = S3CachedNOAAOceanographicJSON(s3, bucketName, prefix)

@Suppress("UNCHECKED_CAST", "UNUSED_CLASS")
class Tideserver: RequestHandler<Map<String, Any>, Map<String, Any>> {
    override fun handleRequest(event: Map<String, Any>?, p1: Context?): Map<String, Any> {
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

        if (event == null) {
            log.error("Lambda event not initialized")
            return hashMapOf("statusCode" to 500, "body" to "Internal server error")
        }

        var eventLog = "Event:"
        event.forEach { (key, value) ->
            eventLog += "\n$key: $value"
        }
        eventLog += "\n"
        log.debug(eventLog)

        val path = (event["path"] ?: "").toString()
        val requestContext = (event["requestContext"] as? Map<String, Any>) ?: hashMapOf()
        val requestId = (requestContext["requestId"] ?: "").toString()

        try {
            val httpMethod: String = (event["httpMethod"] ?: "").toString()
            if (!(httpMethod == "GET" || httpMethod == "HEAD"))
                throw MethodNotAllowedException()

            if (path.isEmpty())
                throw NotFoundException()

            val queryStringParameters = (event["queryStringParameters"] as? Map<String, String> ?: hashMapOf()).mapKeys {
                (key, _) -> key.toLowerCase()
            }

            // HTTP headers are case-insensitive
            val requestHeaders = (event["headers"] as? Map<String, String> ?: hashMapOf()).mapKeys {
                (key, _) -> key.toLowerCase()
            }

            val result: RequestResult = noaaServer.get(path, queryStringParameters, requestHeaders)
            return hashMapOf(
                "statusCode" to result.httpStatus, "body" to result.body, "headers" to result.headers)
        }
        catch (e: Exception) {
            val body = Json.createObjectBuilder()
            val errorBody = Json.createObjectBuilder()
            val statusCode = when (e) {
                is HTTPException -> e.statusCode
                else -> HttpStatus.SC_INTERNAL_SERVER_ERROR
            }
            errorBody.add("Code", getHTTPStatusMessage(statusCode))
            errorBody.add("Message", e.message ?: "")
            errorBody.add("Resource", path)
            errorBody.add("RequestId", requestId)
            errorBody.add("EncodedStackTrace", encryptStackTrace(e))
            body.add("Error", errorBody)

            val headers = mutableMapOf<String, String>()
            if (e is HTTPClientException) {
                // Client-side exceptions can be cached
                headers["Cache-Control"] = "public, max-age=${hours1.seconds}"
            } else {
                // Don't cache server-side exceptions by default.
                headers["Cache-Control"] = "no-cache, no-store, must-revalidate"
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