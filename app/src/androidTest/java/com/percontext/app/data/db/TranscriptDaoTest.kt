package com.percontext.app.data.db

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TranscriptDaoTest {
    private lateinit var database: PerContextDatabase
    private lateinit var recordDao: RecordDao
    private lateinit var transcriptDao: TranscriptDao

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder<PerContextDatabase>(context)
            .setDriver(AndroidSQLiteDriver())
            .build()
        recordDao = database.recordDao()
        transcriptDao = database.transcriptDao()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun queueRequestUsesAtomicStatusTransition() = runTest {
        recordDao.insert(record())

        assertEquals(1, transcriptDao.queueIfRequestable("record_1"))
        assertEquals(0, transcriptDao.queueIfRequestable("record_1"))
        assertEquals("QUEUED", recordDao.findById("record_1")?.transcriptStatus)
    }

    @Test
    fun successfulTranscriptIsSavedOnceAndRemainsImmutable() = runTest {
        recordDao.insert(record())
        assertEquals(1, transcriptDao.updateStatus("record_1", "PROCESSING"))
        val transcript = transcript(text = "第一版")
        transcriptDao.saveSuccessfulTranscriptIfAbsent(
            transcript = transcript,
            segments = listOf(segment(id = "old_0", text = "旧")),
        )

        transcriptDao.saveSuccessfulTranscriptIfAbsent(
            transcript = transcript.copy(
                id = "replacement_transcript",
                rawText = "第二版",
                language = "en",
                provider = "replacement-provider",
                model = "replacement-model",
                updatedAtMillis = 20L,
            ),
            segments = listOf(segment(id = "new_0", text = "新")),
        )

        assertEquals("SUCCEEDED", recordDao.findById("record_1")?.transcriptStatus)
        assertEquals(0, transcriptDao.queueIfRequestable("record_1"))
        assertEquals("transcript_record_1", transcriptDao.findByRecordId("record_1")?.id)
        assertEquals("第一版", transcriptDao.findByRecordId("record_1")?.rawText)
        assertEquals("zh", transcriptDao.findByRecordId("record_1")?.language)
        assertEquals("sherpa-onnx", transcriptDao.findByRecordId("record_1")?.provider)
        assertEquals(
            "sensevoice-int8-2024-07-17",
            transcriptDao.findByRecordId("record_1")?.model,
        )
        assertEquals(
            listOf("旧"),
            transcriptDao.findSegments("transcript_record_1").map(TranscriptSegmentEntity::text),
        )
    }

    private fun record() = RecordEntity(
        id = "record_1",
        createdAtMillis = 1L,
        durationMillis = 1_000L,
        audioLocation = "/recordings/record_1.m4a",
        audioCodec = "AAC-LC/M4A",
        recordStatus = "RECORDED",
        transcriptStatus = "NOT_REQUESTED",
        contextStatus = "NOT_REQUESTED",
    )

    private fun transcript(text: String) = TranscriptEntity(
        id = "transcript_record_1",
        recordId = "record_1",
        rawText = text,
        language = "zh",
        provider = "sherpa-onnx",
        model = "sensevoice-int8-2024-07-17",
        createdAtMillis = 10L,
        updatedAtMillis = 10L,
    )

    private fun segment(id: String, text: String) = TranscriptSegmentEntity(
        id = id,
        transcriptId = "transcript_record_1",
        position = 0,
        startMillis = 0L,
        endMillis = 100L,
        text = text,
    )
}
