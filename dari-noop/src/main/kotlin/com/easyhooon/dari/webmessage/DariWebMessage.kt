package com.easyhooon.dari.webmessage

data class DariWebMessage(
    val requestId: String,
    val handlerName: String,
    val jsObjectName: String,
    val sourceOrigin: String,
    val isMainFrame: Boolean,
    val requestData: String?,
    val reply: DariWebMessageReply,
)

fun interface DariWebMessageHandler {
    fun onMessage(message: DariWebMessage)
}
