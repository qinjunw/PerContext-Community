package com.percontext.app.data.repository

import com.percontext.app.data.db.TranscriptDao
import com.percontext.app.data.db.TranscriptEntity
import com.percontext.app.data.db.TranscriptSegmentEntity
import com.percontext.app.domain.asr.TranscriptResult
import com.percontext.app.domain.asr.TranscriptSegment
import com.percontext.app.domain.model.TranscriptStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomTranscriptRepositoryTest {
    @Test
    fun `queue request is accepted only while status is requestable`() = runTest {
        val dao = FakeTranscriptDao()
        val repository = RoomTranscriptRepository(dao)

        assertTrue(repository.queueIfRequestable("record_1"))
        assertFalse(repository.queueIfRequestable("record_1"))
        assertEquals("QUEUED", dao.status)
    }

    @Test
    fun `successful snapshot cannot be queued again`() = runTest {
        val dao = FakeTranscriptDao()
        val repository = RoomTranscriptRepository(dao)
        assertTrue(repository.setStatus("record_1", TranscriptStatus.SUCCEEDED))

        assertFalse(repository.queueIfRequestable("record_1"))
        assertEquals("SUCCEEDED", dao.status)
    }

    @Test
    fun `successful result maps text metadata segments and status`() = runTest {
        val dao = FakeTranscriptDao()
        val repository = RoomTranscriptRepository(dao, clock = { 100L })

        assertTrue(repository.setStatus("record_1", TranscriptStatus.PROCESSING))
        repository.saveSuccessful(
            recordId = "record_1",
            result = TranscriptResult(
                text = "测试文本",
                language = "zh",
                segments = listOf(TranscriptSegment(10L, 20L, "测")),
                provider = "sherpa-onnx",
                model = "sensevoice-int8-2024-07-17",
            ),
        )

        assertEquals("SUCCEEDED", dao.status)
        assertEquals("测试文本", dao.transcript?.rawText)
        assertEquals("zh", dao.transcript?.language)
        assertEquals(10L, dao.segments.single().startMillis)
        assertEquals("测", dao.segments.single().text)
    }

    @Test
    fun `later successful result cannot overwrite the first snapshot`() = runTest {
        val dao = FakeTranscriptDao()
        val repository = RoomTranscriptRepository(dao, clock = { 100L })
        val first = TranscriptResult(
            text = "第一版",
            language = "zh",
            segments = listOf(TranscriptSegment(10L, 20L, "第一段")),
            provider = "sherpa-onnx",
            model = "sensevoice-int8-2024-07-17",
        )
        repository.saveSuccessful("record_1", first)

        repository.saveSuccessful(
            recordId = "record_1",
            result = TranscriptResult(
                text = "第二版",
                language = "en",
                segments = listOf(TranscriptSegment(30L, 40L, "replacement")),
                provider = "replacement-provider",
                model = "replacement-model",
            ),
        )

        assertEquals("第一版", dao.transcript?.rawText)
        assertEquals("zh", dao.transcript?.language)
        assertEquals("sherpa-onnx", dao.transcript?.provider)
        assertEquals("sensevoice-int8-2024-07-17", dao.transcript?.model)
        assertEquals(listOf("第一段"), dao.segments.map { it.text })
        assertEquals("SUCCEEDED", dao.status)
    }
}

private class FakeTranscriptDao : TranscriptDao {
    var status: String = "NOT_REQUESTED"
    var transcript: TranscriptEntity? = null
    var segments: List<TranscriptSegmentEntity> = emptyList()

    override suspend fun queueIfRequestable(recordId: String): Int {
        if (recordId != "record_1" || status !in setOf("NOT_REQUESTED", "FAILED")) return 0
        status = "QUEUED"
        return 1
    }

    override suspend fun updateStatus(recordId: String, status: String): Int {
        this.status = status
        return if (recordId == "record_1") 1 else 0
    }

    override suspend fun insertTranscriptIfAbsent(transcript: TranscriptEntity): Long {
        if (this.transcript != null) return -1L
        this.transcript = transcript
        return 1L
    }

    override suspend fun insertSegments(segments: List<TranscriptSegmentEntity>) {
        this.segments = segments
    }

    override suspend fun findByRecordId(recordId: String): TranscriptEntity? =
        transcript?.takeIf { it.recordId == recordId }

    override suspend fun findSegments(transcriptId: String): List<TranscriptSegmentEntity> =
        segments.filter { it.transcriptId == transcriptId }
}
