package com.percontext.app.domain.asr

import com.percontext.app.domain.model.RecordStatus
import com.percontext.app.domain.model.TranscriptStatus
import com.percontext.app.domain.model.VoiceRecord
import com.percontext.app.domain.repository.TranscriptRepository
import com.percontext.app.domain.repository.VoiceRecordRepository
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TranscribeRecordUseCaseTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `successful transcription moves from processing to persisted success`() = runTest {
        val audioFile = temporaryFolder.newFile("record.m4a")
        val transcriptRepository = FakeTranscriptRepository()
        val expected = transcriptResult()
        val useCase = TranscribeRecordUseCase(
            voiceRecordRepository = FakeVoiceRecordRepository(record(audioFile)),
            transcriptRepository = transcriptRepository,
            asrProvider = AsrProvider { expected },
        )

        useCase("record_1")

        assertEquals(listOf(TranscriptStatus.PROCESSING), transcriptRepository.statuses)
        assertEquals("record_1", transcriptRepository.savedRecordId)
        assertEquals(expected, transcriptRepository.savedResult)
        assertTrue(audioFile.exists())
    }

    @Test
    fun `provider failure records failed status and keeps source audio`() = runTest {
        val audioFile = temporaryFolder.newFile("record.m4a")
        val transcriptRepository = FakeTranscriptRepository()
        val useCase = TranscribeRecordUseCase(
            voiceRecordRepository = FakeVoiceRecordRepository(record(audioFile)),
            transcriptRepository = transcriptRepository,
            asrProvider = AsrProvider { error("model unavailable") },
        )

        val result = runCatching { useCase("record_1") }

        assertTrue(result.isFailure)
        assertEquals(
            listOf(TranscriptStatus.PROCESSING, TranscriptStatus.FAILED),
            transcriptRepository.statuses,
        )
        assertTrue(audioFile.exists())
    }

    @Test
    fun `missing record fails before changing status`() = runTest {
        val transcriptRepository = FakeTranscriptRepository()
        val useCase = TranscribeRecordUseCase(
            voiceRecordRepository = FakeVoiceRecordRepository(null),
            transcriptRepository = transcriptRepository,
            asrProvider = AsrProvider { transcriptResult() },
        )

        val result = runCatching { useCase("missing") }

        assertTrue(result.isFailure)
        assertTrue(transcriptRepository.statuses.isEmpty())
    }

    @Test
    fun `cancellation returns status to not requested and keeps source audio`() = runTest {
        val audioFile = temporaryFolder.newFile("record.m4a")
        val transcriptRepository = FakeTranscriptRepository()
        val providerStarted = CompletableDeferred<Unit>()
        val useCase = TranscribeRecordUseCase(
            voiceRecordRepository = FakeVoiceRecordRepository(record(audioFile)),
            transcriptRepository = transcriptRepository,
            asrProvider = AsrProvider {
                providerStarted.complete(Unit)
                awaitCancellation()
            },
        )

        val request = launch { useCase("record_1") }
        providerStarted.await()
        request.cancelAndJoin()

        assertEquals(
            listOf(TranscriptStatus.PROCESSING, TranscriptStatus.NOT_REQUESTED),
            transcriptRepository.statuses,
        )
        assertTrue(audioFile.exists())
    }

    private fun record(audioFile: File) = VoiceRecord(
        id = "record_1",
        createdAtMillis = 1L,
        durationMillis = 2L,
        audioLocation = audioFile.absolutePath,
        audioCodec = "AAC-LC/M4A",
        status = RecordStatus.RECORDED,
    )
}

private fun transcriptResult() = TranscriptResult(
    text = "本地转写",
    language = "zh",
    segments = emptyList(),
    provider = "sherpa-onnx",
    model = "sensevoice-int8",
)

private class FakeVoiceRecordRepository(
    private val record: VoiceRecord?,
) : VoiceRecordRepository {
    override val records: Flow<List<VoiceRecord>> = emptyFlow()

    override suspend fun add(record: VoiceRecord) = Unit

    override suspend fun findById(id: String): VoiceRecord? = record?.takeIf { it.id == id }

    override suspend fun delete(id: String): Boolean = false
}

private class FakeTranscriptRepository : TranscriptRepository {
    val statuses = mutableListOf<TranscriptStatus>()
    var savedRecordId: String? = null
    var savedResult: TranscriptResult? = null

    override suspend fun queueIfRequestable(recordId: String): Boolean = false

    override suspend fun setStatus(recordId: String, status: TranscriptStatus): Boolean {
        statuses += status
        return true
    }

    override suspend fun saveSuccessful(recordId: String, result: TranscriptResult) {
        savedRecordId = recordId
        savedResult = result
    }
}
