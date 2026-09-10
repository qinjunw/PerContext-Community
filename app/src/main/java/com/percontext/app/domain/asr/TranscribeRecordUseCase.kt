package com.percontext.app.domain.asr

import com.percontext.app.domain.model.TranscriptStatus
import com.percontext.app.domain.repository.TranscriptRepository
import com.percontext.app.domain.repository.VoiceRecordRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

class TranscribeRecordUseCase(
    private val voiceRecordRepository: VoiceRecordRepository,
    private val transcriptRepository: TranscriptRepository,
    private val asrProvider: AsrProvider,
) {
    suspend operator fun invoke(recordId: String) {
        val record = checkNotNull(voiceRecordRepository.findById(recordId)) {
            "Cannot transcribe missing record $recordId"
        }
        check(transcriptRepository.setStatus(recordId, TranscriptStatus.PROCESSING)) {
            "Cannot update missing record $recordId"
        }

        try {
            val result = asrProvider.transcribe(record.audioLocation)
            transcriptRepository.saveSuccessful(recordId, result)
        } catch (cancellation: CancellationException) {
            withContext(NonCancellable) {
                transcriptRepository.setStatus(recordId, TranscriptStatus.NOT_REQUESTED)
            }
            throw cancellation
        } catch (error: Throwable) {
            withContext(NonCancellable) {
                transcriptRepository.setStatus(recordId, TranscriptStatus.FAILED)
            }
            throw error
        }
    }
}
