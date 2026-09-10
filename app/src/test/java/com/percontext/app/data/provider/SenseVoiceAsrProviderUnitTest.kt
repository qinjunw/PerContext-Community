package com.percontext.app.data.provider

import com.percontext.app.core.media.PcmAudioDecoder
import com.percontext.app.core.media.PcmDecodeResult
import com.percontext.app.domain.asr.TranscriptResult
import com.percontext.app.domain.asr.TranscriptSegment
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class SenseVoiceAsrProviderUnitTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `provider reuses one session and bounds every recognition window`() = runTest {
        val session = FakeRecognizerSession(
            results = ArrayDeque(
                listOf(
                    transcript("第一段", 10L),
                    transcript("第二段", 20L),
                ),
            ),
        )
        var sessionCount = 0
        val provider = SenseVoiceAsrProvider(
            audioDecoder = FakePcmAudioDecoder(
                chunks = listOf(
                    FloatArray(7) { it.toFloat() },
                    FloatArray(13) { (it + 7).toFloat() },
                ),
            ),
            modelStore = readyModelStore(),
            dispatcher = UnconfinedTestDispatcher(testScheduler),
            sessionFactory = SenseVoiceRecognizerSessionFactory {
                sessionCount += 1
                session
            },
            windowSampleCount = 16,
        )

        val result = provider.transcribe(temporaryFolder.newFile("record.m4a").absolutePath)

        assertEquals(1, sessionCount)
        assertEquals(listOf(16, 4), session.receivedWindows.map(FloatArray::size))
        assertEquals("第一段\n第二段", result.text)
        assertEquals(listOf(10L, 21L), result.segments.map { it.startMillis })
        assertTrue(session.closed)
    }

    @Test
    fun `provider closes session when a window fails`() = runTest {
        val session = FakeRecognizerSession(
            results = ArrayDeque(listOf(transcript("unused", 0L))),
            failure = IllegalStateException("native decode failed"),
        )
        val provider = SenseVoiceAsrProvider(
            audioDecoder = FakePcmAudioDecoder(listOf(FloatArray(4))),
            modelStore = readyModelStore(),
            dispatcher = UnconfinedTestDispatcher(testScheduler),
            sessionFactory = SenseVoiceRecognizerSessionFactory { session },
            windowSampleCount = 4,
        )

        val result = runCatching {
            provider.transcribe(temporaryFolder.newFile("failure.m4a").absolutePath)
        }

        assertTrue(result.isFailure)
        assertTrue(session.closed)
    }

    @Test
    fun `provider closes session when decoder fails`() = runTest {
        val session = FakeRecognizerSession(
            results = ArrayDeque(listOf(transcript("unused", 0L))),
        )
        val provider = SenseVoiceAsrProvider(
            audioDecoder = FakePcmAudioDecoder(
                chunks = emptyList(),
                failure = IllegalStateException("media decode failed"),
            ),
            modelStore = readyModelStore(),
            dispatcher = UnconfinedTestDispatcher(testScheduler),
            sessionFactory = SenseVoiceRecognizerSessionFactory { session },
            windowSampleCount = 4,
        )

        val result = runCatching {
            provider.transcribe(temporaryFolder.newFile("decoder-failure.m4a").absolutePath)
        }

        assertTrue(result.isFailure)
        assertTrue(session.closed)
    }

    @Test
    fun `provider closes session when transcription is cancelled`() = runTest {
        val decoderBlocked = CompletableDeferred<Unit>()
        val session = FakeRecognizerSession(
            results = ArrayDeque(listOf(transcript("第一段", 0L))),
        )
        val provider = SenseVoiceAsrProvider(
            audioDecoder = FakePcmAudioDecoder(
                chunks = listOf(FloatArray(4)),
                afterChunks = {
                    decoderBlocked.complete(Unit)
                    awaitCancellation()
                },
            ),
            modelStore = readyModelStore(),
            dispatcher = UnconfinedTestDispatcher(testScheduler),
            sessionFactory = SenseVoiceRecognizerSessionFactory { session },
            windowSampleCount = 4,
        )

        val request = launch {
            provider.transcribe(temporaryFolder.newFile("cancelled.m4a").absolutePath)
        }
        decoderBlocked.await()
        request.cancelAndJoin()

        assertTrue(session.closed)
    }

    private fun readyModelStore(): SenseVoiceModelStore {
        val directory = temporaryFolder.newFolder()
        directory.resolve("model.int8.onnx").writeBytes(byteArrayOf(1))
        directory.resolve("tokens.txt").writeBytes(byteArrayOf(1))
        return SenseVoiceModelStore(directory)
    }

    private fun transcript(text: String, startMillis: Long) = TranscriptResult(
        text = text,
        language = "zh",
        segments = listOf(
            TranscriptSegment(startMillis, startMillis + 1L, text),
        ),
        provider = "sherpa-onnx",
        model = "sensevoice-int8-2024-07-17",
    )
}

private class FakePcmAudioDecoder(
    private val chunks: List<FloatArray>,
    private val failure: Throwable? = null,
    private val afterChunks: suspend () -> Unit = {},
) : PcmAudioDecoder {
    override suspend fun decodeMono16Khz(
        audioLocation: String,
        onSamples: (FloatArray) -> Unit,
    ): PcmDecodeResult {
        chunks.forEach(onSamples)
        failure?.let { throw it }
        afterChunks()
        return PcmDecodeResult(
            sourceSampleRate = PcmDecodeResult.TARGET_SAMPLE_RATE,
            sourceChannelCount = 1,
            outputSampleCount = chunks.sumOf { it.size.toLong() },
        )
    }
}

private class FakeRecognizerSession(
    private val results: ArrayDeque<TranscriptResult>,
    private val failure: Throwable? = null,
) : SenseVoiceRecognizerSession {
    val receivedWindows = mutableListOf<FloatArray>()
    var closed = false

    override fun transcribeWindow(samples: FloatArray): TranscriptResult {
        receivedWindows += samples
        failure?.let { throw it }
        return results.removeFirst()
    }

    override fun close() {
        closed = true
    }
}
