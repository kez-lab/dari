package com.easyhooon.dari.webmessage

import android.util.Base64
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
import java.util.concurrent.atomic.AtomicLong
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

internal object DariWebMessageBridge {

    private val webMessageCounter = AtomicLong(0)
    private val json = Json { ignoreUnknownKeys = true }

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
            val payloadType = when (message.type) {
                WebMessageCompat.TYPE_ARRAY_BUFFER -> MessagePayloadType.ARRAY_BUFFER
                else -> MessagePayloadType.STRING
            }

            val stringPayload = if (payloadType == MessagePayloadType.STRING) message.data else null
            val arrayBufferPayload = if (payloadType == MessagePayloadType.ARRAY_BUFFER) {
                message.arrayBuffer
            } else {
                null
            }

            val parsedEnvelope = if (payloadType == MessagePayloadType.STRING) {
                parseEnvelope(stringPayload, config.channelName)
            } else {
                null
            }
            val requestId = parsedEnvelope?.requestId ?: createRequestId()
            val handlerName = parsedEnvelope?.handlerName ?: config.channelName
            val requestData = when (payloadType) {
                MessagePayloadType.STRING -> parsedEnvelope?.requestData ?: stringPayload
                MessagePayloadType.ARRAY_BUFFER -> {
                    if (arrayBufferPayload == null) {
                        "[ArrayBuffer]"
                    } else {
                        "[ArrayBuffer ${arrayBufferPayload.size} bytes, base64=${Base64.encodeToString(arrayBufferPayload, Base64.NO_WRAP)}]"
                    }
                }
            }

            val entry = MessageEntry(
                requestId = requestId,
                handlerName = handlerName,
                direction = MessageDirection.WEB_TO_APP,
                transport = BridgeTransport.WEB_MESSAGE_LISTENER,
                payloadType = payloadType,
                sourceOrigin = sourceOrigin.toString(),
                isMainFrame = isMainFrame,
                requestData = requestData,
            )
            Dari.repository.addEntry(entry)
            Dari.postMessageNotification(handlerName, MessageDirection.WEB_TO_APP)

            val reply = RealDariWebMessageReply(
                requestId = requestId,
                channelName = config.channelName,
                proxy = replyProxy,
            )

            handler?.onMessage(
                DariWebMessage(
                    requestId = requestId,
                    handlerName = handlerName,
                    channelName = config.channelName,
                    sourceOrigin = sourceOrigin.toString(),
                    isMainFrame = isMainFrame,
                    payloadType = payloadType,
                    text = requestData,
                    arrayBuffer = arrayBufferPayload,
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

    private fun createRequestId(): String {
        return "wml_${System.currentTimeMillis()}_${webMessageCounter.incrementAndGet()}"
    }

    private fun parseEnvelope(message: String?, fallbackHandlerName: String): ParsedEnvelope? {
        if (message.isNullOrBlank()) return null
        return try {
            val root = json.parseToJsonElement(message)
            if (root !is JsonObject) return null

            val handlerName = root["handlerName"]?.jsonPrimitive?.contentOrNull ?: fallbackHandlerName
            val requestId = root["requestId"]?.jsonPrimitive?.contentOrNull ?: createRequestId()
            val dataElement = root["data"]
            val requestData = if (dataElement == null || dataElement is JsonNull) null else dataElement.toString()

            if (!root.containsKey("handlerName") || !root.containsKey("requestId")) {
                return null
            }
            ParsedEnvelope(
                handlerName = handlerName,
                requestId = requestId,
                requestData = requestData,
            )
        } catch (_: Exception) {
            null
        }
    }

    private data class ParsedEnvelope(
        val handlerName: String,
        val requestId: String,
        val requestData: String?,
    )

    private fun parseResponseEnvelope(message: String?): ParsedResponseEnvelope? {
        if (message.isNullOrBlank()) return null
        return try {
            val root = json.parseToJsonElement(message)
            if (root !is JsonObject) return null
            if (!root.containsKey("requestId") || !root.containsKey("success")) return null

            val success = root["success"]?.jsonPrimitive?.booleanOrNull ?: return null
            val dataElement = root["data"]
            val data = if (dataElement == null || dataElement is JsonNull) null else dataElement.toString()

            ParsedResponseEnvelope(success = success, data = data)
        } catch (_: Exception) {
            null
        }
    }

    private data class ParsedResponseEnvelope(
        val success: Boolean,
        val data: String?,
    )

    private class RealDariWebMessageReply(
        private val requestId: String,
        private val channelName: String,
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
            Dari.postMessageNotification(channelName, MessageDirection.APP_TO_WEB)
        }

        override fun postArrayBuffer(bytes: ByteArray): Boolean {
            if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_ARRAY_BUFFER)) {
                markReplyError("WEB_MESSAGE_ARRAY_BUFFER unsupported")
                return false
            }

            proxy.postMessage(bytes)

            val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
            Dari.repository.updateEntry(requestId) { entry ->
                entry.copy(
                    responseData = "[ArrayBuffer ${bytes.size} bytes, base64=$encoded]",
                    status = MessageStatus.SUCCESS,
                    responseTimestamp = System.currentTimeMillis(),
                    payloadType = MessagePayloadType.ARRAY_BUFFER,
                )
            }
            Dari.postMessageNotification(channelName, MessageDirection.APP_TO_WEB)
            return true
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
