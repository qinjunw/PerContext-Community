package com.percontext.app.domain.repository

import com.percontext.app.domain.asr.TranscriptResult
import com.percontext.app.domain.model.TranscriptStatus

interface TranscriptRepository {
    suspend fun queueIfRequestable(recordId: String): Boolean

    suspend fun setStatus(recordId: String, status: TranscriptStatus): Boolean

    suspend fun saveSuccessful(recordId: String, result: TranscriptResult)
}
