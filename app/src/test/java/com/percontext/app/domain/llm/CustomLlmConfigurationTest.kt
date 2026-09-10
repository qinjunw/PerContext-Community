package com.percontext.app.domain.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CustomLlmConfigurationTest {
    @Test
    fun `base URL and complete endpoint resolve to the same service`() {
        assertEquals("https://example.com/v1", normalizeLlmBaseUrl(" https://EXAMPLE.com:443/v1/ "))
        assertEquals("https://example.com/v1", normalizeLlmBaseUrl("https://example.com/v1/chat/completions/"))
        assertEquals("https://example.com:8443", normalizeLlmBaseUrl("https://example.com:8443"))
    }

    @Test
    fun `credentials query strings fragments and insecure endpoints are rejected`() {
        listOf("http://example.com", "https://user:pass@example.com", "https://example.com?key=secret",
            "https://example.com#fragment", "https://example.com:0", "file:///tmp/model", "not a URL").forEach {
            assertThrows(IllegalArgumentException::class.java) { normalizeLlmBaseUrl(it) }
        }
        assertThrows(IllegalArgumentException::class.java) { validateCustomLlmConfiguration("https://example.com", " ") }
        assertThrows(IllegalArgumentException::class.java) { validateCustomLlmConfiguration("https://example.com", "model\nname") }
    }
}
