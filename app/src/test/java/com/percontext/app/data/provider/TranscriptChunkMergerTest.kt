package com.percontext.app.data.provider

import com.percontext.app.domain.asr.TranscriptResult
import com.percontext.app.domain.asr.TranscriptSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TranscriptChunkMergerTest {
    @Test
    fun `merges nonblank text and offsets segment timestamps`() {
        val chunks = listOf(
            transcript(
                text = "第一段。",
                language = "zh",
                segments = listOf(TranscriptSegment(100L, 300L, "第一段")),
            ),
            transcript(text = "", language = null),
            transcript(
                text = "第二段。",
                language = "zh",
                segments = listOf(TranscriptSegment(50L, 250L, "第二段")),
            ).offsetBy(30_000L),
        )

        val result = mergeTranscriptChunks(chunks)

        assertEquals("第一段。\n第二段。", result.text)
        assertEquals("zh", result.language)
        assertEquals(listOf(100L, 30_050L), result.segments.map { it.startMillis })
        assertEquals(listOf(300L, 30_250L), result.segments.map { it.endMillis })
    }

    @Test
    fun `all blank chunks fail instead of persisting an empty transcript`() {
        assertThrows(IllegalStateException::class.java) {
            mergeTranscriptChunks(listOf(transcript(text = "", language = null)))
        }
    }

    private fun transcript(
        text: String,
        language: String?,
        segments: List<TranscriptSegment> = emptyList(),
    ) = TranscriptResult(
        text = text,
        language = language,
        segments = segments,
        provider = "sherpa-onnx",
        model = "sensevoice-int8-2024-07-17",
    )
}
