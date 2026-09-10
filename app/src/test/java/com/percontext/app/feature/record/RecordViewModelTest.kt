package com.percontext.app.feature.record

import com.percontext.app.core.media.PlaybackController
import com.percontext.app.core.media.PlaybackState
import com.percontext.app.domain.model.RecordStatus
import com.percontext.app.domain.model.VoiceRecord
import com.percontext.app.domain.recording.RecordingFeatureGateway
import com.percontext.app.domain.recording.RecordingSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class RecordViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `unavailable transcription does not block recording controls`() = runTest {
        val gateway = FakeRecordingFeatureGateway(transcriptionAvailable = false)
        val viewModel = RecordViewModel(gateway, FakePlaybackController())
        val message = backgroundScope.async(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.messages.first()
        }

        viewModel.startRecording()
        viewModel.requestTranscription(record())

        assertEquals(1, gateway.startCount)
        assertTrue(gateway.transcriptionRequests.isEmpty())
        assertEquals("SenseVoice 本地模型尚未安装或不可用", message.await())
    }

    @Test
    fun `delete stops playback before delegating to feature gateway`() = runTest {
        val events = mutableListOf<String>()
        val gateway = FakeRecordingFeatureGateway(events = events)
        val playback = FakePlaybackController(events)
        val viewModel = RecordViewModel(gateway, playback)

        viewModel.delete(record())

        assertEquals(listOf("playback-stop:record_1", "delete:record_1"), events)
    }

    @Test
    fun `available transcription delegates by record id`() = runTest {
        val gateway = FakeRecordingFeatureGateway(transcriptionAvailable = true)
        val viewModel = RecordViewModel(gateway, FakePlaybackController())

        viewModel.requestTranscription(record())

        assertEquals(listOf("record_1"), gateway.transcriptionRequests)
    }

    @Test
    fun `recording input level is exposed to the record screen state`() = runTest {
        val gateway = FakeRecordingFeatureGateway()
        val viewModel = RecordViewModel(gateway, FakePlaybackController())
        val updatedState = backgroundScope.async(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.first { it.recordingInputLevel == 0.7f }
        }

        gateway.inputLevel.value = 0.7f

        assertEquals(0.7f, updatedState.await().recordingInputLevel)
    }

}

private class FakeRecordingFeatureGateway(
    private val transcriptionAvailable: Boolean = true,
    private val events: MutableList<String> = mutableListOf(),
) : RecordingFeatureGateway {
    private val mutableRecords = MutableStateFlow<List<VoiceRecord>>(emptyList())
    override val records: Flow<List<VoiceRecord>> = mutableRecords
    override val recordingSession: StateFlow<RecordingSession> =
        MutableStateFlow(RecordingSession.Idle)
    val inputLevel = MutableStateFlow(0f)
    override val recordingInputLevel: StateFlow<Float> = inputLevel
    var startCount = 0
    val transcriptionRequests = mutableListOf<String>()

    override fun startRecording() {
        startCount += 1
    }

    override fun stopRecording() = Unit

    override fun isTranscriptionAvailable(): Boolean = transcriptionAvailable

    override suspend fun requestTranscription(recordId: String): Boolean {
        transcriptionRequests += recordId
        return true
    }

    override suspend fun deleteRecording(recordId: String): Boolean {
        events += "delete:$recordId"
        return true
    }
}

private class FakePlaybackController(
    private val events: MutableList<String> = mutableListOf(),
) : PlaybackController {
    override val state: StateFlow<PlaybackState> = MutableStateFlow(PlaybackState())

    override fun toggle(record: VoiceRecord) = Unit

    override fun seekTo(recordId: String, fraction: Float) = Unit

    override fun stop(recordId: String) {
        events += "playback-stop:$recordId"
    }

    override fun release() = Unit
}

private fun record() = VoiceRecord(
    id = "record_1",
    createdAtMillis = 1L,
    durationMillis = 1_000L,
    audioLocation = "content://media/record_1",
    audioCodec = "AAC-LC/M4A",
    status = RecordStatus.RECORDED,
)

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val dispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
