package com.easyhooon.dari.webmessage

data class DariWebMessageConfig(
    val jsObjectName: String = "DariBridge",
    val allowedOriginRules: Set<String> = setOf("https://*", "http://*"),
    val channelName: String = jsObjectName,
)
