package org.kanga.tideserver

import org.apache.http.HttpStatus
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

val stackTraceKey = (System.getenv("STACK_TRACE_KEY") ?: "")
val aesKeySize = 128 / 8
val aesIVSize = 128 / 8
val hashAlgorithm = "SHA-256"
val cipherAlgorithm = "AES/CBC/PKCS5Padding"
val keyAlgorithm = "AES"

fun encryptStackTrace(e: Throwable): String {
    if (stackTraceKey.isEmpty()) {
        log.error("Unable to encrypt stack trace: STACK_TRACE_KEY unset")
        return ""
    }

    val digest = MessageDigest.getInstance(hashAlgorithm)
    digest.update(stackTraceKey.toByteArray())
    val stackTraceKeyBytes = digest.digest().copyOfRange(0, aesKeySize)

    val stackTraceStream = ByteArrayOutputStream()
    PrintStream(stackTraceStream).use { printStream ->
        e.printStackTrace(printStream)
        val plaintext = stackTraceStream.toByteArray()

        try {
            val cipher = Cipher.getInstance(cipherAlgorithm)
            val sr = SecureRandom()
            val iv = ByteArray(aesIVSize)
            sr.nextBytes(iv)

            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(stackTraceKeyBytes, keyAlgorithm), IvParameterSpec(iv))
            val cipherText = cipher.doFinal(plaintext)
            val rawResult = iv.plus(cipherText)
            val b64Encode = Base64.getEncoder()
            return b64Encode.encodeToString(rawResult)

        }
        catch (e: GeneralSecurityException) {
            log.error("Unable to encrypt stack trace", e)
            return ""
        }
    }
}

/**
 *  Exceptions that map to HTTP status codes.
 */
@Suppress("UNUSED")
open class HTTPException : RuntimeException {
    val statusCode: Int
    val headers: HTTPHeaders

    constructor(message: String?, cause: Throwable?, statusCode: Int, headers: HTTPHeaders?=null): super(message ?: getHTTPStatusMessage(statusCode), cause) {
        this.statusCode = statusCode
        this.headers = headers ?: hashMapOf()
    }

    constructor(statusCode: Int, headers: HTTPHeaders?=null): this(null, null, statusCode, headers)
    constructor(message: String?, statusCode: Int, headers: HTTPHeaders?=null): this(message, null, statusCode, headers)
    constructor(cause: Throwable?, statusCode: Int, headers: HTTPHeaders?=null): this(null, cause, statusCode, headers)
}

/**
 *  Exception thrown when we encounter an issue with a downstream service. This should result in a 5xx error code.
 */
@Suppress("UNUSED")
open class HTTPServerException : HTTPException {
    constructor(message: String?, cause: Throwable?, statusCode:Int=HttpStatus.SC_BAD_GATEWAY, headers: HTTPHeaders?=null): super(message, cause, statusCode, headers)
    constructor(statusCode: Int=HttpStatus.SC_BAD_GATEWAY, headers: HTTPHeaders?=null): this(null, null, statusCode, headers)
    constructor(message: String?, statusCode: Int=HttpStatus.SC_BAD_GATEWAY, headers: HTTPHeaders?=null): this(message, null, statusCode, headers)
    constructor(cause: Throwable?, statusCode: Int=HttpStatus.SC_BAD_GATEWAY): this(null, cause, statusCode)
}

/**
 *  Exception thrown when we encounter an issue with a downstream service.
 */
@Suppress("UNUSED")
class BadGatewayException : HTTPServerException {
    constructor(message: String?, cause: Throwable?, statusCode:Int=HttpStatus.SC_BAD_GATEWAY, headers: HTTPHeaders?=null): super(message, cause, statusCode, headers)
    constructor(statusCode: Int=HttpStatus.SC_BAD_GATEWAY, headers: HTTPHeaders?=null): this(null, null, statusCode, headers)
    constructor(message: String?, statusCode: Int=HttpStatus.SC_BAD_GATEWAY, headers: HTTPHeaders?=null): this(message, null, statusCode, headers)
    constructor(cause: Throwable?, statusCode: Int=HttpStatus.SC_BAD_GATEWAY, headers: HTTPHeaders?=null): this(null, cause, statusCode, headers)
}

/**
 *  Exception thrown when we encounter an issue with the request. This should result in a 4xx error code.
 */
@Suppress("UNUSED")
open class HTTPClientException : HTTPException {
    constructor(message: String?, cause: Throwable?, statusCode:Int=HttpStatus.SC_BAD_REQUEST, headers: HTTPHeaders?=null): super(message, cause, statusCode, headers)
    constructor(statusCode: Int=HttpStatus.SC_BAD_REQUEST, headers: HTTPHeaders?=null): this(null, null, statusCode, headers)
    constructor(message: String?, statusCode: Int=HttpStatus.SC_BAD_REQUEST, headers: HTTPHeaders?=null): this(message, null, statusCode, headers)
    constructor(cause: Throwable?, statusCode: Int=HttpStatus.SC_BAD_REQUEST, headers: HTTPHeaders?=null): this(null, cause, statusCode, headers)
}

/**
 *  Exception thrown when the client sent a bad request.
 */
@Suppress("UNUSED")
open class BadRequestException : HTTPClientException {
    constructor(message: String?, cause: Throwable?, statusCode:Int=HttpStatus.SC_BAD_REQUEST, headers: HTTPHeaders?=null): super(message ?: "Bad reqeust", cause, statusCode, headers)
    constructor(statusCode: Int=HttpStatus.SC_BAD_REQUEST, headers: HTTPHeaders?=null): this(null, null, statusCode, headers)
    constructor(message: String?, statusCode: Int=HttpStatus.SC_BAD_REQUEST, headers: HTTPHeaders?=null): this(message, null, statusCode, headers)
    constructor(cause: Throwable?, statusCode: Int=HttpStatus.SC_BAD_REQUEST, headers: HTTPHeaders?=null): this(null, cause, statusCode, headers)
}

/**
 *  Exception thrown when a request is forbidden (e.g. not available yet).
 */
@Suppress("UNUSED")
open class ForbiddenException : HTTPClientException {
    constructor(message: String?, cause: Throwable?, statusCode:Int=HttpStatus.SC_FORBIDDEN, headers: HTTPHeaders?=null): super(message ?: "Request forbidden", cause, statusCode, headers)
    constructor(statusCode: Int=HttpStatus.SC_FORBIDDEN, headers: HTTPHeaders?=null): this(null, null, statusCode, headers)
    constructor(message: String?, statusCode: Int=HttpStatus.SC_FORBIDDEN, headers: HTTPHeaders?=null): this(message, null, statusCode, headers)
    constructor(cause: Throwable?, statusCode: Int=HttpStatus.SC_FORBIDDEN, headers: HTTPHeaders?=null): this(null, cause, statusCode, headers)
}

/**
 *  Exception thrown when a path is unknown.
 */
@Suppress("UNUSED")
open class NotFoundException : HTTPClientException {
    constructor(message: String?, cause: Throwable?, statusCode:Int=HttpStatus.SC_NOT_FOUND, headers: HTTPHeaders?=null): super(message ?: "Path not found", cause, statusCode, headers)
    constructor(statusCode: Int=HttpStatus.SC_NOT_FOUND, headers: HTTPHeaders?=null): this(null, null, statusCode, headers)
    constructor(message: String?, statusCode: Int=HttpStatus.SC_NOT_FOUND, headers: HTTPHeaders?=null): this(message, null, statusCode, headers)
    constructor(cause: Throwable?, statusCode: Int=HttpStatus.SC_NOT_FOUND, headers: HTTPHeaders?=null): this(null, cause, statusCode, headers)
}

/**
 *  Exception thrown when a bad HTTP method is used.
 */
@Suppress("UNUSED")
open class MethodNotAllowedException : HTTPClientException {
    constructor(message: String?, cause: Throwable?, statusCode: Int = HttpStatus.SC_METHOD_NOT_ALLOWED, headers: HTTPHeaders? = null) : super(message ?: "Method not allowed", cause, statusCode, headers)
    constructor(statusCode: Int = HttpStatus.SC_METHOD_NOT_ALLOWED, headers: HTTPHeaders? = null) : this(null, null, statusCode, headers)
    constructor(message: String?, statusCode: Int = HttpStatus.SC_METHOD_NOT_ALLOWED, headers: HTTPHeaders? = null) : this(message, null, statusCode, headers)
    constructor(cause: Throwable?, statusCode: Int = HttpStatus.SC_METHOD_NOT_ALLOWED, headers: HTTPHeaders? = null) : this(null, cause, statusCode, headers)
}