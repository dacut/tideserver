package org.kanga.tideserver

import org.apache.http.HttpStatus
import org.apache.http.client.utils.DateUtils
import org.apache.http.impl.EnglishReasonPhraseCatalog
import java.time.Instant
import java.time.ZonedDateTime
import java.util.Date
import java.util.Locale
import java.util.SortedMap
import java.util.TreeMap

fun getHTTPStatusMessage(statusCode: Int): String {
    val reason = EnglishReasonPhraseCatalog.INSTANCE.getReason(statusCode, Locale.ENGLISH)
    return reason ?: "HTTP Error $statusCode"
}

typealias HTTPHeaders = Map<String, String>

fun toHTTPDateString(dateTime: Instant): String {
    return DateUtils.formatDate(Date.from(dateTime))
}

fun toHTTPDateString(dateTime: ZonedDateTime): String {
    return toHTTPDateString(dateTime.toInstant())
}

/**
 *  Data passed in as part of a request
 */
data class Request(val method: String, val path: String, val query: HTTPMap, val headers: HTTPMap)

/**
 *  Data returned in the response
 */
data class Response(var statusCode: Int, val headers: HTTPMap = HTTPMap(), var body: String = "") {
    constructor(statusCode: Int=HttpStatus.SC_OK, headers: Map<String, String>? = null, body: String? = null):
        this(statusCode, HTTPMap(headers ?: mapOf()), body ?: "")
}


class CaseInsensitiveStringComparator: Comparator<String> {
    override fun compare(o1: String?, o2: String?): Int {
        return when {
            o1 === null -> when (o2) {
                null -> 0
                else -> -1
            }
            o2 === null -> 1
            else -> o1.toLowerCase().compareTo(o2.toLowerCase())
        }
    }
}

class CaseInsensitiveMap<V>(impl: SortedMap<String, V>): SortedMap<String, V> by impl {
    constructor(): this(TreeMap<String, V>(CaseInsensitiveStringComparator()))
    constructor(m: Map<String, V>?): this() {
        if (m !== null)
            putAll(m)
    }
}

typealias HTTPMap = CaseInsensitiveMap<String>