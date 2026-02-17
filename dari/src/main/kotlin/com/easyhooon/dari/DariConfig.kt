package com.easyhooon.dari

/**
 * Dari configuration
 */
data class DariConfig(
    /** Maximum number of messages to keep in the in-memory buffer */
    val maxEntries: Int = 500,
    /** Whether to show the status notification */
    val showNotification: Boolean = true,
    /**
     * If true, malformed WebMessage request envelopes throw immediately.
     * If false, malformed payloads are recorded as error entries instead.
     */
    val strictWebMessageParsing: Boolean = true,
)
