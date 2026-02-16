package com.easyhooon.dari.webmessage

import com.easyhooon.dari.MessagePayloadType

data class DariWebMessage(
    val requestId: String,
    val handlerName: String,
    val channelName: String,
    val sourceOrigin: String,
    val isMainFrame: Boolean,
    val payloadType: MessagePayloadType,
    val text: String?,
    val arrayBuffer: ByteArray?,
    val reply: DariWebMessageReply,
)

fun interface DariWebMessageHandler {
    fun onMessage(message: DariWebMessage)
}
