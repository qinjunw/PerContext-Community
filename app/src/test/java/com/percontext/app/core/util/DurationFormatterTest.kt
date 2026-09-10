package com.percontext.app.core.util

import org.junit.Assert.assertEquals
import org.junit.Test

class DurationFormatterTest {
    @Test
    fun `formats sub-hour duration as minutes and seconds`() {
        assertEquals("02:07", formatDuration(127_000L))
    }

    @Test
    fun `formats long recording with hours`() {
        assertEquals("1:02:03", formatDuration(3_723_000L))
    }

    @Test
    fun `negative duration is clamped to zero`() {
        assertEquals("00:00", formatDuration(-1L))
    }
}
