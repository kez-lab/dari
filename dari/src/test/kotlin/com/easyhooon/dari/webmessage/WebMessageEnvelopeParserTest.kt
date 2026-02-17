package com.easyhooon.dari.webmessage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class WebMessageEnvelopeParserTest {

    @Test
    fun `parseRequestOrThrow parses valid request envelope`() {
        val parsed = WebMessageEnvelopeParser.parseRequestOrThrow(
            """{"handlerName":"ping","requestId":"req-1","data":{"value":1}}""",
        )

        assertEquals("ping", parsed.handlerName)
        assertEquals("req-1", parsed.requestId)
        assertEquals("""{"value":1}""", parsed.requestData)
    }

    @Test
    fun `parseRequestOrThrow throws for invalid payload`() {
        assertThrows(IllegalArgumentException::class.java) {
            WebMessageEnvelopeParser.parseRequestOrThrow("""{"handler":"ping"}""")
        }
    }

    @Test
    fun `parseResponseOrNull parses valid response envelope`() {
        val parsed = WebMessageEnvelopeParser.parseResponseOrNull(
            """{"requestId":"req-1","success":true,"data":{"ok":true}}""",
        )

        requireNotNull(parsed)
        assertEquals(true, parsed.success)
        assertEquals("""{"ok":true}""", parsed.data)
    }

    @Test
    fun `parseResponseOrNull returns null for invalid success type`() {
        val parsed = WebMessageEnvelopeParser.parseResponseOrNull(
            """{"requestId":"req-1","success":"true","data":{"ok":true}}""",
        )

        assertNull(parsed)
    }
}
