package org.kanga.tideserver

import org.apache.http.client.utils.DateUtils
import org.apache.http.impl.EnglishReasonPhraseCatalog
import java.time.Instant
import java.time.ZonedDateTime
import java.util.Date
import java.util.Locale

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