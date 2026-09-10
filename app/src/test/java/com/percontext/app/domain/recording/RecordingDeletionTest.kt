package com.percontext.app.domain.recording

import com.percontext.app.domain.asr.TranscriptionCanceller
import com.percontext.app.domain.model.RecordStatus
import com.percontext.app.domain.model.TranscriptStatus
import com.percontext.app.domain.model.VoiceRecord
import com.percontext.app.domain.repository.VoiceRecordRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingDeletionTest {
    @Test
    fun `delete cancels transcription before removing record`() = runTest {
        val events = mutableListOf<String>()
        val records = DeletionVoiceRecordRepository(setOf("record_1"), events)
        val delete = DeleteRecordingUseCase(
            voiceRecordRepository = records,
            transcriptionCanceller = TranscriptionCanceller { events += "cancel:$it" },
        )

        assertTrue(delete("record_1"))
        assertEquals(listOf("cancel:record_1", "delete:record_1"), events)
    }

    @Test
    fun `cancellation failure leaves record untouched`() = runTest {
        val events = mutableListOf<String>()
        val records = DeletionVoiceRecordRepository(setOf("record_1"), events)
        val delete = DeleteRecordingUseCase(
            voiceRecordRepository = records,
            transcriptionCanceller = TranscriptionCanceller {
                events += "cancel:$it"
                error("cancel failed")
            },
        )

        val result = runCatching { delete("record_1") }

        assertTrue(result.isFailure)
        assertEquals(listOf("cancel:record_1"), events)
        assertTrue(records.contains("record_1"))
    }

}

private class DeletionVoiceRecordRepository(
    initialIds: Set<String>,
    private val events: MutableList<String> = mutableListOf(),
) : VoiceRecordRepository {
    private val ids = initialIds.toMutableSet()
    override val records: Flow<List<VoiceRecord>> = emptyFlow()

    fun contains(id: String): Boolean = id in ids

    override suspend fun add(record: VoiceRecord) {
        ids += record.id
    }

    override suspend fun findById(id: String): VoiceRecord? =
        if (id in ids) deletionRecord(id) else null

    override suspend fun delete(id: String): Boolean {
        events += "delete:$id"
        return ids.remove(id)
    }
}

private fun deletionRecord(id: String) = VoiceRecord(
    id = id,
    createdAtMillis = 1L,
    durationMillis = 1L,
    audioLocation = "content://media/$id",
    audioCodec = "AAC-LC/M4A",
    status = RecordStatus.RECORDED,
)
