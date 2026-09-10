package com.percontext.app.service.recording

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PcmInputLevelMeterTest {
    @Test
    fun `digital silence stays at zero`() {
        val meter = PcmInputLevelMeter()

        val level = meter.update(ShortArray(256), 256)

        assertEquals(0f, level, 0.0001f)
    }

    @Test
    fun `normal speech range produces a visible bounded level`() {
        val meter = PcmInputLevelMeter()
        val samples = ShortArray(256) { 4_096 }

        val level = meter.update(samples, samples.size)

        assertTrue("Expected a visible response, got $level", level in 0.35f..1f)
    }

    @Test
    fun `louder samples produce a stronger response`() {
        val quietMeter = PcmInputLevelMeter()
        val loudMeter = PcmInputLevelMeter()

        val quiet = quietMeter.update(ShortArray(256) { 512 }, 256)
        val loud = loudMeter.update(ShortArray(256) { 8_192 }, 256)

        assertTrue("Expected $loud to exceed $quiet", loud > quiet)
        assertTrue(loud <= 1f)
    }

    @Test
    fun `level falls after speech instead of remaining stuck`() {
        val meter = PcmInputLevelMeter()
        val speaking = meter.update(ShortArray(256) { 8_192 }, 256)

        val released = meter.update(ShortArray(256), 256)

        assertTrue(released in 0f..<speaking)
    }

    @Test
    fun `only the reported sample count contributes to the level`() {
        val meter = PcmInputLevelMeter()
        val samples = shortArrayOf(0, 0, 0, Short.MAX_VALUE)

        val level = meter.update(samples, 3)

        assertEquals(0f, level, 0.0001f)
    }
}
