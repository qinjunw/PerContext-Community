package com.percontext.app.data.repository

import com.percontext.app.data.db.RecordDao
import com.percontext.app.data.db.RecordEntity
import com.percontext.app.data.db.RecordListRow
import com.percontext.app.data.file.FileAudioFileStore
import java.io.File
import com.percontext.app.domain.model.RecordStatus
import com.percontext.app.domain.model.TranscriptStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RoomVoiceRecordRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `maps persisted records without losing fact status`() = runTest {
        val dao = FakeRecordDao(
            RecordEntity(
                id = "record_1",
                createdAtMillis = 10L,
                durationMillis = 20L,
                audioLocation = "/recordings/record_1.m4a",
                audioCodec = "AAC",
                recordStatus = "RECORDED",
                transcriptStatus = "NOT_REQUESTED",
                contextStatus = "NOT_REQUESTED",
            ),
        )
        val repository = RoomVoiceRecordRepository(
            recordDao = dao,
            audioFileStore = FileAudioFileStore(temporaryFolder.newFolder("recordings")),
        )

        val record = repository.records.first().single()

        assertEquals("record_1", record.id)
        assertEquals(RecordStatus.RECORDED, record.status)
        assertEquals(20L, record.durationMillis)
    }

    @Test
    fun `delete removes database row and owned audio`() = runTest {
        val recordingsDirectory = temporaryFolder.newFolder("recordings")
        val fileStore = FileAudioFileStore(recordingsDirectory)
        val pending = fileStore.createPendingRecording("record_1")
        val audio = File(pending.audioLocation).apply { writeBytes(byteArrayOf(1)) }
        pending.publish()
        val dao = FakeRecordDao(entity(audio.absolutePath))
        val repository = RoomVoiceRecordRepository(dao, fileStore)

        val deleted = repository.delete("record_1")

        assertTrue(deleted)
        assertFalse(audio.exists())
        assertNull(dao.findById("record_1"))
    }

    @Test
    fun `delete removes database row without touching an outside file`() = runTest {
        val recordingsDirectory = temporaryFolder.newFolder("recordings")
        val outside = temporaryFolder.newFile("outside.m4a").apply { writeBytes(byteArrayOf(1)) }
        val dao = FakeRecordDao(entity(outside.absolutePath))
        val repository = RoomVoiceRecordRepository(dao, FileAudioFileStore(recordingsDirectory))

        val deleted = repository.delete("record_1")

        assertTrue(deleted)
        assertTrue(outside.exists())
        assertNull(dao.findById("record_1"))
    }

    @Test
    fun `database failure leaves audio untouched`() = runTest {
        val recordingsDirectory = temporaryFolder.newFolder("recordings")
        val fileStore = FileAudioFileStore(recordingsDirectory)
        val pending = fileStore.createPendingRecording("record_1")
        val audio = File(pending.audioLocation).apply { writeBytes(byteArrayOf(1, 2, 3)) }
        pending.publish()
        val dao = FakeRecordDao(entity(audio.absolutePath)).apply {
            deleteFailure = IllegalStateException("database unavailable")
        }
        val repository = RoomVoiceRecordRepository(dao, fileStore)

        val failure = runCatching { repository.delete("record_1") }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertTrue(audio.isFile)
        assertEquals(byteArrayOf(1, 2, 3).toList(), audio.readBytes().toList())
        assertEquals("record_1", dao.findById("record_1")?.id)
    }

    @Test
    fun `maps owned audio file size into the domain record`() = runTest {
        val recordingsDirectory = temporaryFolder.newFolder("recordings")
        val fileStore = FileAudioFileStore(recordingsDirectory)
        val pending = fileStore.createPendingRecording("record_1")
        val audio = File(pending.audioLocation).apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
        pending.publish()
        val repository = RoomVoiceRecordRepository(
            recordDao = FakeRecordDao(entity(audio.absolutePath)),
            audioFileStore = fileStore,
        )

        assertEquals(4L, repository.records.first().single().fileSizeBytes)
    }

    private fun entity(audioLocation: String) = RecordEntity(
        id = "record_1",
        createdAtMillis = 10L,
        durationMillis = 20L,
        audioLocation = audioLocation,
        audioCodec = "AAC",
        recordStatus = "RECORDED",
        transcriptStatus = "NOT_REQUESTED",
        contextStatus = "NOT_REQUESTED",
    )
}

private class FakeRecordDao(
    vararg initial: RecordEntity,
) : RecordDao {
    private val state = MutableStateFlow(initial.toList())
    var deleteFailure: Throwable? = null

    override fun observeAll(): Flow<List<RecordEntity>> = state

    override fun observeAllWithTranscripts(): Flow<List<RecordListRow>> = state.map { records ->
        records.map { record -> RecordListRow(record = record, transcriptText = null) }
    }

    override suspend fun insert(record: RecordEntity) {
        state.value = state.value.filterNot { it.id == record.id } + record
    }

    override suspend fun findById(id: String): RecordEntity? = state.value.firstOrNull { it.id == id }

    override suspend fun findWithTranscriptById(id: String): RecordListRow? =
        findById(id)?.let { record -> RecordListRow(record = record, transcriptText = null) }

    override suspend fun deleteById(id: String): Int {
        deleteFailure?.let { throw it }
        val previousSize = state.value.size
        state.value = state.value.filterNot { it.id == id }
        return previousSize - state.value.size
    }
}
