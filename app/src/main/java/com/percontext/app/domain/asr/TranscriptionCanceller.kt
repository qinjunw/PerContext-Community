package com.percontext.app.domain.asr

fun interface TranscriptionCanceller {
    suspend fun cancel(recordId: String)
}
