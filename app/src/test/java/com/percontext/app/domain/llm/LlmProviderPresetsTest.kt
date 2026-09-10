package com.percontext.app.domain.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LlmProviderPresetsTest {
    @Test
    fun `deepseek preset owns endpoint and current default model`() {
        val preset = LlmProviderPresets.deepSeek

        assertEquals("deepseek", preset.id)
        assertEquals("https://api.deepseek.com", preset.baseUrl)
        assertEquals("deepseek-v4-flash", preset.defaultModel)
        assertEquals(preset, LlmProviderPresets.find("deepseek"))
        assertNull(LlmProviderPresets.find("unknown"))
    }
}
