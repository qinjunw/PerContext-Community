package com.percontext.app.domain.asr

fun interface TranscriptionScheduler {
    suspend fun enqueue(recordId: String)
}
