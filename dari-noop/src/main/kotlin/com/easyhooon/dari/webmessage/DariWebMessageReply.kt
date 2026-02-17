package com.easyhooon.dari.webmessage

/**
 * Reply channel for a single WebMessage request.
 * No-op builds keep the same API surface.
 */
interface DariWebMessageReply {
    /** Sends a string response back to JavaScript. */
    fun postText(text: String)

    /** Sends a binary response. May be unsupported by concrete implementations. */
    fun postArrayBuffer(bytes: ByteArray): Boolean
}
