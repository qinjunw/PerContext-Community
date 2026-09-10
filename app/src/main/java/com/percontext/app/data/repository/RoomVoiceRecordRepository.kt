package com.percontext.app.data.repository

import com.percontext.app.data.db.RecordDao
import com.percontext.app.data.db.RecordEntity
import com.percontext.app.data.db.RecordListRow
import com.percontext.app.data.file.AudioFileStore
import com.percontext.app.domain.model.RecordStatus
import com.percontext.app.domain.model.TranscriptStatus
import com.percontext.app.domain.model.VoiceRecord
import com.percontext.app.domain.repository.VoiceRecordRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class RoomVoiceRecordRepository(
    private val recordDao: RecordDao,
    private val audioFileStore: AudioFileStore,
) : VoiceRecordRepository {
    override val records: Flow<List<VoiceRecord>> = recordDao.observeAllWithTranscripts().map { rows ->
        rows.map { it.toDomain(audioFileStore) }
    }.flowOn(Dispatchers.IO)

    override suspend fun add(record: VoiceRecord) {
        recordDao.insert(record.toEntity())
    }

    override suspend fun findById(id: String): VoiceRecord? =
        recordDao.findWithTranscriptById(id)?.toDomain(audioFileStore)

    override suspend fun delete(id: String): Boolean = withContext(Dispatchers.IO) {
        val record = recordDao.findById(id) ?: return@withContext false
        if (recordDao.deleteById(id) != 1) return@withContext false
        runCatching { audioFileStore.deleteOwnedFile(record.audioLocation) }
        true
    }
}

private fun RecordListRow.toDomain(audioFileStore: AudioFileStore) = VoiceRecord(
    id = record.id,
    createdAtMillis = record.createdAtMillis,
    durationMillis = record.durationMillis,
    audioLocation = record.audioLocation,
    audioCodec = record.audioCodec,
    status = runCatching { RecordStatus.valueOf(record.recordStatus) }
        .getOrDefault(RecordStatus.CORRUPTED),
    fileSizeBytes = audioFileStore.sizeOfOwnedFile(record.audioLocation),
    transcriptStatus = runCatching { TranscriptStatus.valueOf(record.transcriptStatus) }
        .getOrDefault(TranscriptStatus.NOT_REQUESTED),
    transcriptText = transcriptText,
)

private fun VoiceRecord.toEntity() = RecordEntity(
    id = id,
    createdAtMillis = createdAtMillis,
    durationMillis = durationMillis,
    audioLocation = audioLocation,
    audioCodec = audioCodec,
    recordStatus = status.name,
    transcriptStatus = "NOT_REQUESTED",
    contextStatus = "NOT_REQUESTED",
)
