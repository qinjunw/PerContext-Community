package com.percontext.app.data.repository

import com.percontext.app.data.db.TranscriptDao
import com.percontext.app.data.db.TranscriptEntity
import com.percontext.app.data.db.TranscriptSegmentEntity
import com.percontext.app.domain.asr.TranscriptResult
import com.percontext.app.domain.model.TranscriptStatus
import com.percontext.app.domain.repository.TranscriptRepository

class RoomTranscriptRepository(
    private val transcriptDao: TranscriptDao,
    private val clock: () -> Long = System::currentTimeMillis,
) : TranscriptRepository {
    override suspend fun queueIfRequestable(recordId: String): Boolean =
        transcriptDao.queueIfRequestable(recordId) == 1

    override suspend fun setStatus(recordId: String, status: TranscriptStatus): Boolean =
        transcriptDao.updateStatus(recordId, status.name) == 1

    override suspend fun saveSuccessful(recordId: String, result: TranscriptResult) {
        val transcriptId = "transcript_$recordId"
        val previous = transcriptDao.findByRecordId(recordId)
        val now = clock()
        val transcript = TranscriptEntity(
            id = transcriptId,
            recordId = recordId,
            rawText = result.text,
            language = result.language,
            provider = result.provider,
            model = result.model,
            createdAtMillis = previous?.createdAtMillis ?: now,
            updatedAtMillis = now,
        )
        val segments = result.segments.mapIndexed { index, segment ->
            TranscriptSegmentEntity(
                id = "${transcriptId}_$index",
                transcriptId = transcriptId,
                position = index,
                startMillis = segment.startMillis,
                endMillis = segment.endMillis,
                text = segment.text,
            )
        }
        transcriptDao.saveSuccessfulTranscriptIfAbsent(transcript, segments)
    }
}
