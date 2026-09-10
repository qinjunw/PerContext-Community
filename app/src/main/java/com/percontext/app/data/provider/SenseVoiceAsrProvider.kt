package com.percontext.app.data.provider

import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizerResult
import com.k2fsa.sherpa.onnx.OfflineSenseVoiceModelConfig
import com.percontext.app.core.media.PcmAudioDecoder
import com.percontext.app.core.media.PcmDecodeResult
import com.percontext.app.core.media.PcmWindowChunker
import com.percontext.app.domain.asr.AsrProvider
import com.percontext.app.domain.asr.TranscriptResult
import com.percontext.app.domain.asr.TranscriptSegment
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToLong

internal fun interface SenseVoiceRecognizerSessionFactory {
    fun create(modelFiles: SenseVoiceModelFiles): SenseVoiceRecognizerSession
}

internal interface SenseVoiceRecognizerSession : AutoCloseable {
    fun transcribeWindow(samples: FloatArray): TranscriptResult
}

class SenseVoiceAsrProvider internal constructor(
    private val audioDecoder: PcmAudioDecoder,
    private val modelStore: SenseVoiceModelStore,
    private val dispatcher: CoroutineDispatcher,
    private val sessionFactory: SenseVoiceRecognizerSessionFactory,
    private val windowSampleCount: Int,
) : AsrProvider {
    constructor(
        audioDecoder: PcmAudioDecoder,
        modelStore: SenseVoiceModelStore,
        dispatcher: CoroutineDispatcher = Dispatchers.Default,
    ) : this(
        audioDecoder = audioDecoder,
        modelStore = modelStore,
        dispatcher = dispatcher,
        sessionFactory = NativeSenseVoiceRecognizerSessionFactory,
        windowSampleCount = DEFAULT_WINDOW_SAMPLE_COUNT,
    )

    override suspend fun transcribe(audioLocation: String): TranscriptResult = withContext(dispatcher) {
        val transcriptionContext = currentCoroutineContext()
        transcriptionContext.ensureActive()
        val modelFiles = modelStore.requireReady()
        val session = sessionFactory.create(modelFiles)
        try {
            transcriptionContext.ensureActive()
            val transcriptChunks = mutableListOf<TranscriptResult>()
            val chunker = PcmWindowChunker(windowSampleCount) { window ->
                val offsetMillis = window.startSampleIndex * 1_000L /
                    PcmDecodeResult.TARGET_SAMPLE_RATE
                transcriptionContext.ensureActive()
                val result = session.transcribeWindow(window.samples)
                transcriptionContext.ensureActive()
                transcriptChunks += result.offsetBy(offsetMillis)
            }
            audioDecoder.decodeMono16Khz(audioLocation) { samples ->
                transcriptionContext.ensureActive()
                chunker.accept(samples)
            }
            transcriptionContext.ensureActive()
            chunker.finish()
            mergeTranscriptChunks(transcriptChunks)
        } finally {
            session.close()
        }
    }

    private companion object {
        const val WINDOW_DURATION_SECONDS = 30
        const val DEFAULT_WINDOW_SAMPLE_COUNT =
            PcmDecodeResult.TARGET_SAMPLE_RATE * WINDOW_DURATION_SECONDS
    }
}

private object NativeSenseVoiceRecognizerSessionFactory : SenseVoiceRecognizerSessionFactory {
    override fun create(modelFiles: SenseVoiceModelFiles): SenseVoiceRecognizerSession =
        NativeSenseVoiceRecognizerSession(modelFiles)
}

private class NativeSenseVoiceRecognizerSession(
    modelFiles: SenseVoiceModelFiles,
) : SenseVoiceRecognizerSession {
    private val recognizer = createRecognizer(modelFiles)

    override fun transcribeWindow(samples: FloatArray): TranscriptResult {
        val stream = recognizer.createStream()
        try {
            stream.acceptWaveform(samples, PcmDecodeResult.TARGET_SAMPLE_RATE)
            recognizer.decode(stream)
            return recognizer.getResult(stream).toDomainResult()
        } finally {
            stream.release()
        }
    }

    override fun close() {
        recognizer.release()
    }
}

private fun createRecognizer(modelFiles: SenseVoiceModelFiles): OfflineRecognizer =
    OfflineRecognizer(
        config = OfflineRecognizerConfig(
            featConfig = FeatureConfig(
                sampleRate = PcmDecodeResult.TARGET_SAMPLE_RATE,
                featureDim = 80,
                dither = 0f,
            ),
            modelConfig = OfflineModelConfig(
                senseVoice = OfflineSenseVoiceModelConfig(
                    model = modelFiles.model.absolutePath,
                    language = "zh",
                    useInverseTextNormalization = true,
                ),
                tokens = modelFiles.tokens.absolutePath,
                numThreads = THREAD_COUNT,
                provider = "cpu",
            ),
        ),
    )

private fun OfflineRecognizerResult.toDomainResult(): TranscriptResult = TranscriptResult(
    text = text,
    language = lang.takeIf(String::isNotBlank),
    segments = tokens.mapIndexedNotNull { index, token ->
        val startSeconds = timestamps.getOrNull(index) ?: return@mapIndexedNotNull null
        val endSeconds = durations.getOrNull(index)
            ?.let(startSeconds::plus)
            ?: timestamps.getOrNull(index + 1)
            ?: startSeconds
        TranscriptSegment(
            startMillis = (startSeconds * 1_000f).roundToLong(),
            endMillis = (endSeconds * 1_000f).roundToLong(),
            text = token,
        )
    },
    provider = PROVIDER_ID,
    model = MODEL_ID,
)

private const val THREAD_COUNT = 4
private const val PROVIDER_ID = "sherpa-onnx"
private const val MODEL_ID = "sensevoice-int8-2024-07-17"
