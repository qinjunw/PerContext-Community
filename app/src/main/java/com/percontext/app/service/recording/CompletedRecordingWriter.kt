package com.percontext.app.service.recording

import com.percontext.app.data.file.PendingAudioRecording
import com.percontext.app.domain.model.RecordStatus
import com.percontext.app.domain.model.VoiceRecord
import com.percontext.app.domain.repository.VoiceRecordRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal sealed interface CompletedRecordingWriteResult {
    data object Stored : CompletedRecordingWriteResult

    data object PublishFailed : CompletedRecordingWriteResult

    data object RepositoryFailed : CompletedRecordingWriteResult
}

internal class CompletedRecordingWriter(
    private val repository: VoiceRecordRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun store(
        target: PendingAudioRecording,
        startedAtMillis: Long,
        stoppedAtMillis: Long,
    ): CompletedRecordingWriteResult = withContext(ioDispatcher) {
        try {
            target.publish()
        } catch (_: Throwable) {
            runCatching { target.discard() }
            return@withContext CompletedRecordingWriteResult.PublishFailed
        }

        try {
            repository.add(
                VoiceRecord(
                    id = target.recordId,
                    createdAtMillis = startedAtMillis,
                    durationMillis = (stoppedAtMillis - startedAtMillis).coerceAtLeast(0L),
                    audioLocation = target.audioLocation,
                    audioCodec = "AAC-LC/M4A",
                    status = RecordStatus.RECORDED,
                ),
            )
        } catch (_: Throwable) {
            return@withContext CompletedRecordingWriteResult.RepositoryFailed
        }
        CompletedRecordingWriteResult.Stored
    }
}
