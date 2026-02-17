package com.easyhooon.dari.webmessage

import org.json.JSONObject

/**
 * Sends a response envelope with fixed shape:
 * {"requestId":"...","success":true|false,"data":...}
 */
fun DariWebMessage.replyJson(isSuccess: Boolean, data: JSONObject) {
    val response = JSONObject().apply {
        put("requestId", requestId)
        put("success", isSuccess)
        put("data", data)
    }
    reply.postText(response.toString())
}

fun DariWebMessage.replySuccess(data: JSONObject) {
    replyJson(isSuccess = true, data = data)
}

fun DariWebMessage.replyError(data: JSONObject) {
    replyJson(isSuccess = false, data = data)
}
