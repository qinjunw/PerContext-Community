package com.percontext.app.core.media

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PcmWindowChunkerTest {
    @Test
    fun `arbitrary input chunks produce fixed windows and one tail`() {
        val windows = mutableListOf<PcmWindow>()
        val chunker = PcmWindowChunker(
            windowSampleCount = 4,
            onWindow = windows::add,
        )

        chunker.accept(floatArrayOf(0f, 1f, 2f))
        chunker.accept(floatArrayOf(3f, 4f, 5f, 6f, 7f, 8f))
        chunker.finish()

        assertEquals(listOf(0L, 4L, 8L), windows.map(PcmWindow::startSampleIndex))
        assertArrayEquals(floatArrayOf(0f, 1f, 2f, 3f), windows[0].samples, 0f)
        assertArrayEquals(floatArrayOf(4f, 5f, 6f, 7f), windows[1].samples, 0f)
        assertArrayEquals(floatArrayOf(8f), windows[2].samples, 0f)
    }

    @Test
    fun `exact window does not create an empty tail`() {
        val windows = mutableListOf<PcmWindow>()
        val chunker = PcmWindowChunker(
            windowSampleCount = 2,
            onWindow = windows::add,
        )

        chunker.accept(floatArrayOf(1f, 2f))
        chunker.finish()

        assertEquals(1, windows.size)
        assertArrayEquals(floatArrayOf(1f, 2f), windows.single().samples, 0f)
    }

    @Test
    fun `finished chunker rejects more input`() {
        val chunker = PcmWindowChunker(
            windowSampleCount = 2,
            onWindow = {},
        )
        chunker.finish()

        assertThrows(IllegalStateException::class.java) {
            chunker.accept(floatArrayOf(1f))
        }
    }
}
