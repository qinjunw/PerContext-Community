package com.percontext.app.domain.recording

import com.percontext.app.domain.asr.TranscriptionCanceller
import com.percontext.app.domain.repository.VoiceRecordRepository

class DeleteRecordingUseCase(
    private val voiceRecordRepository: VoiceRecordRepository,
    private val transcriptionCanceller: TranscriptionCanceller,
) {
    suspend operator fun invoke(recordId: String): Boolean {
        transcriptionCanceller.cancel(recordId)
        return voiceRecordRepository.delete(recordId)
    }
}
