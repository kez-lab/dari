package com.easyhooon.dari.interceptor

import android.webkit.WebView
import com.easyhooon.dari.Dari
import com.easyhooon.dari.MessageDirection
import com.easyhooon.dari.MessageEntry
import com.easyhooon.dari.MessageStatus
import com.easyhooon.dari.webmessage.DariWebMessageBridge
import com.easyhooon.dari.webmessage.DariWebMessageConfig
import com.easyhooon.dari.webmessage.DariWebMessageHandler
import com.easyhooon.dari.webmessage.ParsedResponseEnvelope
import com.easyhooon.dari.webmessage.WebMessageListenerCallbacks

/**
 * Default implementation of [DariInterceptor].
 * Stores intercepted messages in [Dari]'s repository and posts notifications.
 */
class DefaultDariInterceptor : DariInterceptor {

    override fun onWebToAppRequest(handlerName: String, requestId: String, requestData: String?) {
        val entry = MessageEntry(
            requestId = requestId,
            handlerName = handlerName,
            direction = MessageDirection.WEB_TO_APP,
            requestData = requestData,
        )
        recordRequest(entry)
    }

    override fun onWebToAppResponse(
        handlerName: String,
        requestId: String,
        responseData: String?,
        isSuccess: Boolean,
    ) {
        recordResponse(requestId, responseData, isSuccess)
    }

    override fun onAppToWebMessage(handlerName: String, requestId: String, data: String?) {
        val entry = MessageEntry(
            requestId = requestId,
            handlerName = handlerName,
            direction = MessageDirection.APP_TO_WEB,
            requestData = data,
        )
        recordRequest(entry)
    }

    override fun onAppToWebResponse(requestId: String, isSuccess: Boolean, responseData: String?) {
        recordResponse(requestId, responseData, isSuccess)
    }

    override fun isWebMessageListenerSupported(): Boolean {
        return DariWebMessageBridge.isSupported()
    }

    override fun addWebMessageListener(
        webView: WebView,
        config: DariWebMessageConfig,
        onMessage: DariWebMessageHandler?,
    ): Boolean {
        return DariWebMessageBridge.addListener(
            webView = webView,
            config = config,
            handler = onMessage,
            strictParsing = Dari.config.strictWebMessageParsing,
            callbacks = object : WebMessageListenerCallbacks {
                override fun onRequest(request: MessageEntry) {
                    recordRequest(request)
                }

                override fun onReplyText(
                    requestId: String,
                    handlerName: String,
                    parsedResponse: ParsedResponseEnvelope?,
                ) {
                    recordResponse(
                        requestId = requestId,
                        responseData = parsedResponse?.data.orEmpty(),
                        isSuccess = parsedResponse?.success != false,
                    )
                    Dari.postMessageNotification(handlerName, MessageDirection.APP_TO_WEB)
                }

                override fun onReplyArrayBuffer(
                    requestId: String,
                    handlerName: String,
                    bytes: ByteArray
                ): Boolean {
                    // TODO: Implement ARRAY_BUFFER response handling when binary protocol is finalized.
                    recordResponse(requestId, "ARRAY_BUFFER is not supported", false)
                    return false
                }
            },
        )
    }

    override fun removeWebMessageListener(webView: WebView, jsObjectName: String): Boolean {
        return DariWebMessageBridge.removeListener(webView, jsObjectName)
    }

    private fun recordRequest(entry: MessageEntry) {
        Dari.repository.addEntry(entry)
        Dari.postMessageNotification(entry.handlerName, entry.direction)
    }

    private fun recordResponse(requestId: String, responseData: String?, isSuccess: Boolean) {
        Dari.repository.updateEntry(requestId) { entry ->
            entry.copy(
                responseData = responseData,
                status = if (isSuccess) MessageStatus.SUCCESS else MessageStatus.ERROR,
                responseTimestamp = System.currentTimeMillis(),
            )
        }
    }
}
