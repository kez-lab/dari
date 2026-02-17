package com.easyhooon.dari.interceptor

import android.webkit.WebView
import com.easyhooon.dari.MessageDirection
import com.easyhooon.dari.MessageEntry
import com.easyhooon.dari.data.MessageRecorder
import com.easyhooon.dari.webmessage.DariWebMessageBridge
import com.easyhooon.dari.webmessage.DariWebMessageConfig
import com.easyhooon.dari.webmessage.DariWebMessageHandler

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
        MessageRecorder.recordRequest(entry)
    }

    override fun onWebToAppResponse(
        handlerName: String,
        requestId: String,
        responseData: String?,
        isSuccess: Boolean,
    ) {
        MessageRecorder.recordResponse(requestId, responseData, isSuccess)
    }

    override fun onAppToWebMessage(handlerName: String, requestId: String, data: String?) {
        val entry = MessageEntry(
            requestId = requestId,
            handlerName = handlerName,
            direction = MessageDirection.APP_TO_WEB,
            requestData = data,
        )
        MessageRecorder.recordRequest(entry)
    }

    override fun onAppToWebResponse(requestId: String, isSuccess: Boolean, responseData: String?) {
        MessageRecorder.recordResponse(requestId, responseData, isSuccess)
    }

    override fun isWebMessageListenerSupported(): Boolean {
        return DariWebMessageBridge.isSupported()
    }

    override fun addWebMessageListener(
        webView: WebView,
        config: DariWebMessageConfig,
        onMessage: DariWebMessageHandler?,
    ): Boolean {
        return DariWebMessageBridge.addListener(webView, config, onMessage)
    }

    override fun removeWebMessageListener(webView: WebView, jsObjectName: String): Boolean {
        return DariWebMessageBridge.removeListener(webView, jsObjectName)
    }
}
