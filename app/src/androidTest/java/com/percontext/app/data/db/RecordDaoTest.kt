package com.percontext.app.data.db

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecordDaoTest {
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
    fun recordsAreObservedNewestFirst() = runTest {
        recordDao.insert(entity(id = "older", createdAtMillis = 10L))
        recordDao.insert(entity(id = "newer", createdAtMillis = 20L))

        val ids = recordDao.observeAll().first().map(RecordEntity::id)

        assertEquals(listOf("newer", "older"), ids)
    }

    @Test
    fun deletingRecordCascadesToTranscriptAndSegments() = runTest {
        recordDao.insert(entity(id = "record_1", createdAtMillis = 1L))
        transcriptDao.saveSuccessfulTranscriptIfAbsent(
            transcript = TranscriptEntity(
                id = "transcript_record_1",
                recordId = "record_1",
                rawText = "不可复活",
                language = "zh",
                provider = "sherpa-onnx",
                model = "sensevoice-int8-2024-07-17",
                createdAtMillis = 2L,
                updatedAtMillis = 2L,
            ),
            segments = listOf(
                TranscriptSegmentEntity(
                    id = "segment_1",
                    transcriptId = "transcript_record_1",
                    position = 0,
                    startMillis = 0L,
                    endMillis = 100L,
                    text = "不可复活",
                ),
            ),
        )

        assertEquals(1, recordDao.deleteById("record_1"))

        assertEquals(null, recordDao.findById("record_1"))
        assertEquals(null, transcriptDao.findByRecordId("record_1"))
        assertEquals(emptyList<TranscriptSegmentEntity>(), transcriptDao.findSegments("transcript_record_1"))
    }

    private fun entity(
        id: String,
        createdAtMillis: Long,
        transcriptStatus: String = "NOT_REQUESTED",
    ) = RecordEntity(
        id = id,
        createdAtMillis = createdAtMillis,
        durationMillis = 1_000L,
        audioLocation = "/recordings/$id.m4a",
        audioCodec = "AAC-LC/M4A",
        recordStatus = "RECORDED",
        transcriptStatus = transcriptStatus,
        contextStatus = "NOT_REQUESTED",
    )
}
