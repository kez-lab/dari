package com.easyhooon.dari.webmessage

/**
 * Configuration for WebMessageListener integration.
 */
data class DariWebMessageConfig(
    val jsObjectName: String = "DariBridge",
    val allowedOriginRules: Set<String> = setOf("https://*", "http://*"),
    val channelName: String = jsObjectName,
)
