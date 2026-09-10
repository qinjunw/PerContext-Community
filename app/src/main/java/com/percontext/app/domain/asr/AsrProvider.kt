package com.percontext.app.domain.asr

fun interface AsrProvider {
    suspend fun transcribe(audioLocation: String): TranscriptResult
}

data class TranscriptResult(
    val text: String,
    val language: String?,
    val segments: List<TranscriptSegment>,
    val provider: String,
    val model: String,
)

data class TranscriptSegment(
    val startMillis: Long,
    val endMillis: Long,
    val text: String,
)
