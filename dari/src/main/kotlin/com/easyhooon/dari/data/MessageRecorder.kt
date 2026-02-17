package com.easyhooon.dari.data

import com.easyhooon.dari.Dari
import com.easyhooon.dari.MessageDirection
import com.easyhooon.dari.MessageEntry
import com.easyhooon.dari.MessageStatus

internal object MessageRecorder {

    fun recordRequest(entry: MessageEntry) {
        Dari.repository.addEntry(entry)
        Dari.postMessageNotification(entry.handlerName, entry.direction)
    }

    fun recordResponse(requestId: String, responseData: String?, isSuccess: Boolean) {
        Dari.repository.updateEntry(requestId) { entry ->
            entry.copy(
                responseData = responseData,
                status = if (isSuccess) MessageStatus.SUCCESS else MessageStatus.ERROR,
                responseTimestamp = System.currentTimeMillis(),
            )
        }
    }

    fun postNotification(handlerName: String, direction: MessageDirection) {
        Dari.postMessageNotification(handlerName, direction)
    }
}
