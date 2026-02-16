package com.easyhooon.dari.webmessage

interface DariWebMessageReply {
    fun postText(text: String)
    fun postArrayBuffer(bytes: ByteArray): Boolean
}
