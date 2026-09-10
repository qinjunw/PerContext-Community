package com.percontext.app.service.recording

import com.percontext.app.data.file.PendingAudioRecording
import com.percontext.app.domain.model.RecordStatus
import com.percontext.app.domain.model.VoiceRecord
import com.percontext.app.domain.repository.VoiceRecordRepository
import java.io.FileDescriptor
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Test

class CompletedRecordingWriterTest {
    @Test
    fun `completed PCM recording publishes audio before adding Room record`() = runTest {
        val calls = mutableListOf<String>()
        val target = WriterPendingRecording(calls)
        val repository = WriterVoiceRecordRepository(calls)
        val writer = CompletedRecordingWriter(repository)

        val result = writer.store(
            target = target,
            startedAtMillis = 1_000L,
            stoppedAtMillis = 2_500L,
        )

        assertEquals(CompletedRecordingWriteResult.Stored, result)
        assertEquals(listOf("publish", "room"), calls)
        assertEquals(
            VoiceRecord(
                id = target.recordId,
                createdAtMillis = 1_000L,
                durationMillis = 1_500L,
                audioLocation = target.audioLocation,
                audioCodec = "AAC-LC/M4A",
                status = RecordStatus.RECORDED,
            ),
            repository.added.single(),
        )
    }

    @Test
    fun `publish failure discards pending audio and does not add Room record`() = runTest {
        val calls = mutableListOf<String>()
        val target = WriterPendingRecording(
            calls = calls,
            publishFailure = IllegalStateException("publish failed"),
        )
        val repository = WriterVoiceRecordRepository(calls)

        val result = CompletedRecordingWriter(repository).store(
            target = target,
            startedAtMillis = 1_000L,
            stoppedAtMillis = 2_500L,
        )

        assertEquals(CompletedRecordingWriteResult.PublishFailed, result)
        assertEquals(listOf("publish", "discard"), calls)
        assertEquals(emptyList<VoiceRecord>(), repository.added)
    }

    @Test
    fun `Room failure retains audio that was already published`() = runTest {
        val calls = mutableListOf<String>()
        val target = WriterPendingRecording(calls)
        val repository = WriterVoiceRecordRepository(
            calls = calls,
            addFailure = IllegalStateException("room failed"),
        )

        val result = CompletedRecordingWriter(repository).store(
            target = target,
            startedAtMillis = 1_000L,
            stoppedAtMillis = 2_500L,
        )

        assertEquals(CompletedRecordingWriteResult.RepositoryFailed, result)
        assertEquals(listOf("publish", "room"), calls)
    }

    @Test
    fun `MediaRecorder entry from Main runs publish and Room writer on IO`() = runBlocking {
        val mainDispatcher = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "recording-service-main")
        }.asCoroutineDispatcher()
        val ioDispatcher = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "recording-writer-io")
        }.asCoroutineDispatcher()
        val calls = ConcurrentLinkedQueue<String>()
        val target = WriterPendingRecording(
            calls = calls,
            onPublish = { calls += "publish@${threadName()}" },
        )
        val repository = WriterVoiceRecordRepository(
            calls = calls,
            onAdd = { calls += "room@${threadName()}" },
        )
        try {
            withContext(mainDispatcher) {
                calls += "entry@${threadName()}"
                val result = CompletedRecordingWriter(repository, ioDispatcher).store(
                    target = target,
                    startedAtMillis = 1_000L,
                    stoppedAtMillis = 2_500L,
                )
                calls += "result@${threadName()}"
                assertEquals(CompletedRecordingWriteResult.Stored, result)
            }

            assertEquals(
                listOf(
                    "entry@recording-service-main",
                    "publish",
                    "publish@recording-writer-io",
                    "room",
                    "room@recording-writer-io",
                    "result@recording-service-main",
                ),
                calls.toList(),
            )
        } finally {
            mainDispatcher.close()
            ioDispatcher.close()
        }
    }
}

private fun threadName(): String = Thread.currentThread().name.substringBefore(" @")

private class WriterPendingRecording(
    private val calls: MutableCollection<String>,
    private val publishFailure: Throwable? = null,
    private val onPublish: () -> Unit = {},
) : PendingAudioRecording {
    override val recordId = "record_writer"
    override val audioLocation = "content://record_writer"
    override val fileDescriptor = FileDescriptor()

    override fun publish() {
        calls += "publish"
        onPublish()
        publishFailure?.let { throw it }
    }

    override fun discard() {
        calls += "discard"
    }
}

private class WriterVoiceRecordRepository(
    private val calls: MutableCollection<String>,
    private val addFailure: Throwable? = null,
    private val onAdd: () -> Unit = {},
) : VoiceRecordRepository {
    override val records: Flow<List<VoiceRecord>> = MutableStateFlow(emptyList())
    val added = mutableListOf<VoiceRecord>()

    override suspend fun add(record: VoiceRecord) {
        calls += "room"
        onAdd()
        addFailure?.let { throw it }
        added += record
    }

    override suspend fun findById(id: String): VoiceRecord? = added.firstOrNull { it.id == id }

    override suspend fun delete(id: String): Boolean = false
}
