package com.easyhooon.dari.webmessage

import android.webkit.WebView
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.easyhooon.dari.BridgeTransport
import com.easyhooon.dari.MessageDirection
import com.easyhooon.dari.MessageEntry
import com.easyhooon.dari.MessagePayloadType
import com.easyhooon.dari.MessageStatus

internal object DariWebMessageBridge {

    fun isSupported(): Boolean {
        return WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)
    }

    fun addListener(
        webView: WebView,
        config: DariWebMessageConfig,
        handler: DariWebMessageHandler?,
        strictParsing: Boolean,
        callbacks: WebMessageListenerCallbacks,
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
                if (strictParsing) {
                    throw e
                }
                callbacks.onRequest(
                    MessageEntry(
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
                    ),
                )
                return@addWebMessageListener
            }

            val request = MessageEntry(
                requestId = parsedEnvelope.requestId,
                handlerName = parsedEnvelope.handlerName,
                direction = MessageDirection.WEB_TO_APP,
                transport = BridgeTransport.WEB_MESSAGE_LISTENER,
                payloadType = MessagePayloadType.STRING,
                sourceOrigin = sourceOrigin.toString(),
                isMainFrame = isMainFrame,
                requestData = parsedEnvelope.requestData,
            )
            callbacks.onRequest(request)

            val reply = DefaultDariWebMessageReply(
                requestId = request.requestId,
                handlerName = request.handlerName,
                proxy = replyProxy,
                callbacks = callbacks,
            )

            handler?.onMessage(
                DariWebMessage(
                    requestId = request.requestId,
                    handlerName = request.handlerName,
                    jsObjectName = config.jsObjectName,
                    sourceOrigin = request.sourceOrigin ?: "unknown",
                    isMainFrame = request.isMainFrame ?: false,
                    requestData = request.requestData,
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
        private val callbacks: WebMessageListenerCallbacks,
    ) : DariWebMessageReply {

        override fun postText(text: String) {
            proxy.postMessage(text)
            val parsedResponse = WebMessageEnvelopeParser.parseResponseOrNull(text)
            callbacks.onReplyText(requestId, handlerName, parsedResponse)
        }

        override fun postArrayBuffer(bytes: ByteArray): Boolean {
            return callbacks.onReplyArrayBuffer(requestId, handlerName, bytes)
        }
    }
}

internal interface WebMessageListenerCallbacks {
    fun onRequest(request: MessageEntry)
    fun onReplyText(
        requestId: String,
        handlerName: String,
        parsedResponse: ParsedResponseEnvelope?,
    )

    fun onReplyArrayBuffer(requestId: String, handlerName: String, bytes: ByteArray): Boolean
}
