package com.easyhooon.dari.webmessage

import android.webkit.WebView
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.easyhooon.dari.BridgeTransport
import com.easyhooon.dari.Dari
import com.easyhooon.dari.MessageDirection
import com.easyhooon.dari.MessageEntry
import com.easyhooon.dari.MessagePayloadType
import com.easyhooon.dari.MessageStatus
import org.json.JSONObject

internal object DariWebMessageBridge {

    fun isSupported(): Boolean {
        return WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)
    }

    fun addListener(
        webView: WebView,
        config: DariWebMessageConfig,
        handler: DariWebMessageHandler?,
    ): Boolean {
        if (!isSupported()) return false

        WebViewCompat.addWebMessageListener(
            webView,
            config.jsObjectName,
            config.allowedOriginRules,
        ) { _, message, sourceOrigin, isMainFrame, replyProxy ->
            if (message.type == WebMessageCompat.TYPE_ARRAY_BUFFER) {
                // TODO: Support ARRAY_BUFFER with a dedicated binary correlation strategy.
                return@addWebMessageListener
            }

            val parsedEnvelope = parseEnvelope(message.data)

            val entry = MessageEntry(
                requestId = parsedEnvelope.requestId,
                handlerName = parsedEnvelope.handlerName,
                direction = MessageDirection.WEB_TO_APP,
                transport = BridgeTransport.WEB_MESSAGE_LISTENER,
                payloadType = MessagePayloadType.STRING,
                sourceOrigin = sourceOrigin.toString(),
                isMainFrame = isMainFrame,
                requestData = parsedEnvelope.requestData,
            )
            Dari.repository.addEntry(entry)
            Dari.postMessageNotification(parsedEnvelope.handlerName, MessageDirection.WEB_TO_APP)

            val reply = DefaultDariWebMessageReply(
                requestId = parsedEnvelope.requestId,
                handlerName = parsedEnvelope.handlerName,
                proxy = replyProxy,
            )

            handler?.onMessage(
                DariWebMessage(
                    requestId = parsedEnvelope.requestId,
                    handlerName = parsedEnvelope.handlerName,
                    jsObjectName = config.jsObjectName,
                    sourceOrigin = sourceOrigin.toString(),
                    isMainFrame = isMainFrame,
                    requestData = parsedEnvelope.requestData,
                    reply = reply,
                ),
            )
        }

        return true
    }

    fun removeListener(webView: WebView, jsObjectName: String): Boolean {
        if (!isSupported()) return false
        WebViewCompat.removeWebMessageListener(webView, jsObjectName)
        return true
    }

    private fun parseEnvelope(message: String?): ParsedEnvelope {
        return try {
            val json = JSONObject(message ?: throw IllegalArgumentException())
            ParsedEnvelope(
                handlerName = json.getString("handlerName"),
                requestId = json.getString("requestId"),
                requestData = json.opt("data")?.let { if (it == JSONObject.NULL) null else it.toString() },
            )
        } catch (e: Exception) {
            throw IllegalArgumentException("WebMessage request payload must match {handlerName, requestId, data}", e)
        }
    }

    private data class ParsedEnvelope(
        val handlerName: String,
        val requestId: String,
        val requestData: String?,
    )

    private fun parseResponseEnvelope(message: String?): ParsedResponseEnvelope? {
        val json = try {
            JSONObject(message ?: return null)
        } catch (_: Exception) {
            return null
        }
        if (!json.has("requestId") || !json.has("success")) return null

        return ParsedResponseEnvelope(
            success = json.getBoolean("success"),
            data = json.opt("data")?.let { if (it == JSONObject.NULL) null else it.toString() },
        )
    }

    private data class ParsedResponseEnvelope(
        val success: Boolean,
        val data: String?,
    )

    private class DefaultDariWebMessageReply(
        private val requestId: String,
        private val handlerName: String,
        private val proxy: JavaScriptReplyProxy,
    ) : DariWebMessageReply {

        override fun postText(text: String) {
            proxy.postMessage(text)
            val parsedResponse = parseResponseEnvelope(text)
            Dari.repository.updateEntry(requestId) { entry ->
                entry.copy(
                    responseData = parsedResponse?.data ?: text,
                    status = if (parsedResponse?.success == false) {
                        MessageStatus.ERROR
                    } else {
                        MessageStatus.SUCCESS
                    },
                    responseTimestamp = System.currentTimeMillis(),
                )
            }
            Dari.postMessageNotification(handlerName, MessageDirection.APP_TO_WEB)
        }

        override fun postArrayBuffer(bytes: ByteArray): Boolean {
            markReplyError("ARRAY_BUFFER is not supported")
            return false
        }

        private fun markReplyError(reason: String) {
            Dari.repository.updateEntry(requestId) { entry ->
                entry.copy(
                    responseData = reason,
                    status = MessageStatus.ERROR,
                    responseTimestamp = System.currentTimeMillis(),
                )
            }
        }
    }
}
