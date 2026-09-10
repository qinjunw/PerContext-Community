package com.percontext.app.data.provider

import com.percontext.app.domain.asr.TranscriptResult

internal fun TranscriptResult.offsetBy(offsetMillis: Long): TranscriptResult = copy(
    segments = segments.map { segment ->
        segment.copy(
            startMillis = segment.startMillis + offsetMillis,
            endMillis = segment.endMillis + offsetMillis,
        )
    },
)

internal fun mergeTranscriptChunks(chunks: List<TranscriptResult>): TranscriptResult {
    check(chunks.isNotEmpty()) { "SenseVoice returned no transcript chunks" }
    val first = chunks.first()
    check(chunks.all { chunk -> chunk.provider == first.provider && chunk.model == first.model }) {
        "Cannot merge transcript chunks from different providers or models"
    }
    val text = chunks.map(TranscriptResult::text)
        .map(String::trim)
        .filter(String::isNotEmpty)
        .joinToString(separator = "\n")
    check(text.isNotEmpty()) { "SenseVoice returned an empty transcript" }
    return TranscriptResult(
        text = text,
        language = chunks.firstNotNullOfOrNull(TranscriptResult::language),
        segments = chunks.flatMap(TranscriptResult::segments),
        provider = first.provider,
        model = first.model,
    )
}
