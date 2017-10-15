package org.kanga.tideserver
import kotlin.collections.HashMap
import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler

class Tideserver: RequestHandler<Map<String, Any>, Map<String, Any>> {
    override fun handleRequest(p0: Map<String, Any>?, p1: Context?): Map<String, Any> {
        var result = HashMap<String, Any>()
        return result
    }
}