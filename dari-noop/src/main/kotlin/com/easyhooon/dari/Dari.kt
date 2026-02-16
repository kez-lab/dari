package com.easyhooon.dari

import android.content.Context
import android.webkit.WebView
import com.easyhooon.dari.interceptor.DariInterceptor
import com.easyhooon.dari.webmessage.DariWebMessageConfig
import com.easyhooon.dari.webmessage.DariWebMessageHandler

/**
 * Noop implementation - does not create an interceptor in release builds.
 */
object Dari {

    fun init(context: Context, config: DariConfig = DariConfig()) = Unit

    fun createInterceptor(): DariInterceptor? = null

    fun showNotification() = Unit

    fun clear() = Unit

    fun isWebMessageListenerSupported(): Boolean = false

    fun addWebMessageListener(
        webView: WebView,
        config: DariWebMessageConfig = DariWebMessageConfig(),
        onMessage: DariWebMessageHandler? = null,
    ): Boolean = false

    fun removeWebMessageListener(webView: WebView, jsObjectName: String): Boolean = false
}
