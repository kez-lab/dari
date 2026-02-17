package com.easyhooon.dari.webmessage

import org.json.JSONObject

internal object WebMessageEnvelopeParser {

    fun parseRequestOrThrow(message: String?): ParsedRequestEnvelope {
        return try {
            val json = JSONObject(message ?: throw IllegalArgumentException())
            ParsedRequestEnvelope(
                handlerName = json.getString("handlerName"),
                requestId = json.getString("requestId"),
                requestData = json.opt("data")?.let { if (it == JSONObject.NULL) null else it.toString() },
            )
        } catch (e: Exception) {
            throw IllegalArgumentException(
                "WebMessage request payload must match {handlerName, requestId, data}",
                e,
            )
        }
    }

    fun parseResponseOrNull(message: String?): ParsedResponseEnvelope? {
        val json = try {
            JSONObject(message ?: return null)
        } catch (_: Exception) {
            return null
        }
        if (!json.has("requestId") || !json.has("success")) return null

        val successValue = json.opt("success")
        if (successValue !is Boolean) return null

        return ParsedResponseEnvelope(
            success = successValue,
            data = json.opt("data")?.let { if (it == JSONObject.NULL) null else it.toString() },
        )
    }
}

internal data class ParsedRequestEnvelope(
    val handlerName: String,
    val requestId: String,
    val requestData: String?,
)

internal data class ParsedResponseEnvelope(
    val success: Boolean,
    val data: String?,
)
