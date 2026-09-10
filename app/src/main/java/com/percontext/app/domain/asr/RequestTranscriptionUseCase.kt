package com.percontext.app.domain.asr

import com.percontext.app.domain.model.TranscriptStatus
import com.percontext.app.domain.repository.TranscriptRepository
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

class RequestTranscriptionUseCase(
    private val transcriptRepository: TranscriptRepository,
    private val transcriptionScheduler: TranscriptionScheduler,
) {
    suspend operator fun invoke(recordId: String): Boolean {
        if (!transcriptRepository.queueIfRequestable(recordId)) return false

        try {
            transcriptionScheduler.enqueue(recordId)
        } catch (error: Throwable) {
            withContext(NonCancellable) {
                transcriptRepository.setStatus(recordId, TranscriptStatus.FAILED)
            }
            throw error
        }
        return true
    }
}
