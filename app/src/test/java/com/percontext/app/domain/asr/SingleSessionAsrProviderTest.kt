package com.percontext.app.domain.asr

import com.percontext.app.domain.recording.RecordingSession
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class SingleSessionAsrProviderTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `two requests use at most one provider session`() = runTest {
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        var calls = 0
        var activeSessions = 0
        var maximumActiveSessions = 0
        val provider = provider(
            recordingSession = MutableStateFlow(RecordingSession.Idle),
            delegate = AsrProvider {
                calls += 1
                activeSessions += 1
                maximumActiveSessions = maxOf(maximumActiveSessions, activeSessions)
                if (calls == 1) {
                    firstStarted.complete(Unit)
                    releaseFirst.await()
                }
                activeSessions -= 1
                transcriptResult()
            },
        )

        val first = async {
            provider.transcribe(temporaryFolder.newFile("first.m4a").absolutePath)
        }
        firstStarted.await()
        val second = async {
            provider.transcribe(temporaryFolder.newFile("second.m4a").absolutePath)
        }
        runCurrent()

        assertEquals(1, calls)
        assertEquals(1, maximumActiveSessions)

        releaseFirst.complete(Unit)
        first.await()
        second.await()

        assertEquals(2, calls)
        assertEquals(1, maximumActiveSessions)
    }

    @Test
    fun `request waits until recording has fully stopped`() = runTest {
        val recordingSession = MutableStateFlow<RecordingSession>(
            RecordingSession.Recording(startedAtMillis = 1L),
        )
        val providerStarted = CompletableDeferred<Unit>()
        val provider = provider(recordingSession) {
            providerStarted.complete(Unit)
            transcriptResult()
        }

        val request = async {
            provider.transcribe(temporaryFolder.newFile("record.m4a").absolutePath)
        }
        runCurrent()
        assertFalse(providerStarted.isCompleted)

        recordingSession.value = RecordingSession.Stopping
        runCurrent()
        assertFalse(providerStarted.isCompleted)

        recordingSession.value = RecordingSession.Idle
        request.await()

        assertEquals(Unit, providerStarted.await())
    }

    @Test
    fun `queued request yields to recording before starting next session`() = runTest {
        val recordingSession = MutableStateFlow<RecordingSession>(RecordingSession.Idle)
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        var calls = 0
        val provider = provider(recordingSession) {
            calls += 1
            if (calls == 1) {
                firstStarted.complete(Unit)
                releaseFirst.await()
            }
            transcriptResult()
        }

        val first = async {
            provider.transcribe(temporaryFolder.newFile("first.m4a").absolutePath)
        }
        firstStarted.await()
        val second = async {
            provider.transcribe(temporaryFolder.newFile("second.m4a").absolutePath)
        }
        recordingSession.value = RecordingSession.Starting
        releaseFirst.complete(Unit)
        first.await()
        runCurrent()

        assertEquals(1, calls)

        recordingSession.value = RecordingSession.Idle
        second.await()

        assertEquals(2, calls)
    }

    private fun provider(
        recordingSession: MutableStateFlow<RecordingSession>,
        delegate: AsrProvider,
    ) = SingleSessionAsrProvider(
        delegate = delegate,
        recordingSession = recordingSession,
    )
}

private fun transcriptResult() = TranscriptResult(
    text = "本地转写",
    language = "zh",
    segments = emptyList(),
    provider = "sherpa-onnx",
    model = "sensevoice-int8",
)
