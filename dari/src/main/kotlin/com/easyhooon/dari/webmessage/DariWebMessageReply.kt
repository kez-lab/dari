package com.easyhooon.dari.webmessage

/**
 * Reply channel for a single WebMessage request.
 * Implementations are provided by Dari and tied to the current callback invocation.
 */
interface DariWebMessageReply {
    /**
     * Sends a string response back to JavaScript.
     *
     * Recommended payload shape:
     * `{"requestId":"...","success":true|false,"data":...}`
     */
    fun postText(text: String)

    /**
     * Sends a binary response.
     *
     * Currently unsupported by Dari's default implementation and returns `false`.
     */
    fun postArrayBuffer(bytes: ByteArray): Boolean
}
