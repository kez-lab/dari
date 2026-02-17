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
import com.easyhooon.dari.data.MessageRecorder

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

            val parsedEnvelope = try {
                WebMessageEnvelopeParser.parseRequestOrThrow(message.data)
            } catch (e: IllegalArgumentException) {
                if (Dari.config.strictWebMessageParsing) {
                    throw e
                }
                val entry = MessageEntry(
                    requestId = "invalid_webmessage_${System.currentTimeMillis()}",
                    handlerName = "__invalid_web_message__",
                    direction = MessageDirection.WEB_TO_APP,
                    transport = BridgeTransport.WEB_MESSAGE_LISTENER,
                    payloadType = MessagePayloadType.STRING,
                    sourceOrigin = sourceOrigin.toString(),
                    isMainFrame = isMainFrame,
                    requestData = message.data,
                    responseData = e.message,
                    status = MessageStatus.ERROR,
                    responseTimestamp = System.currentTimeMillis(),
                )
                MessageRecorder.recordRequest(entry)
                return@addWebMessageListener
            }

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
            MessageRecorder.recordRequest(entry)

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

    private class DefaultDariWebMessageReply(
        private val requestId: String,
        private val handlerName: String,
        private val proxy: JavaScriptReplyProxy,
    ) : DariWebMessageReply {

        override fun postText(text: String) {
            proxy.postMessage(text)
            val parsedResponse = WebMessageEnvelopeParser.parseResponseOrNull(text)
            MessageRecorder.recordResponse(
                requestId = requestId,
                responseData = parsedResponse?.data ?: text,
                isSuccess = parsedResponse?.success != false,
            )
            MessageRecorder.postNotification(handlerName, MessageDirection.APP_TO_WEB)
        }

        override fun postArrayBuffer(bytes: ByteArray): Boolean {
            markReplyError("ARRAY_BUFFER is not supported")
            return false
        }

        private fun markReplyError(reason: String) {
            MessageRecorder.recordResponse(requestId, reason, false)
        }
    }
}
