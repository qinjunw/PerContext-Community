package com.percontext.app.asr

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.percontext.app.PerContextApplication
import com.percontext.app.core.media.AndroidPcmAudioDecoder
import com.percontext.app.data.provider.SenseVoiceAsrProvider
import com.percontext.app.data.provider.SenseVoiceModelStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SenseVoiceAsrProviderTest {
    @Test
    fun transcribesLatestRecordedM4aEntirelyOnAndroid() = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(
            "Manual SenseVoice test. Pass -e runManualAsr true.",
            arguments.getString(ARG_RUN_MANUAL_ASR).toBoolean(),
        )
        val application = ApplicationProvider.getApplicationContext<PerContextApplication>()
        val context = application.applicationContext
        val modelStore = SenseVoiceModelStore.create(context)
        require(modelStore.isReady()) {
            "SenseVoice model pack is not installed in the app models directory"
        }
        val record = application.container.records.first().firstOrNull()
        requireNotNull(record) {
            "Record at least one audio item before running the manual SenseVoice test"
        }
        val provider = SenseVoiceAsrProvider(
            audioDecoder = AndroidPcmAudioDecoder(context),
            modelStore = modelStore,
        )

        val result = provider.transcribe(record.audioLocation)

        arguments.getString(ARG_EXPECTED_TEXT)?.let { expectedText ->
            assertEquals(expectedText, result.text)
        } ?: assertTrue("SenseVoice result must not be blank", result.text.isNotBlank())
        assertEquals("sherpa-onnx", result.provider)
        assertEquals("sensevoice-int8-2024-07-17", result.model)
        assertTrue("SenseVoice should return timestamped tokens", result.segments.isNotEmpty())
        assertTrue(
            "Transcript segments must have non-negative durations",
            result.segments.all { segment -> segment.endMillis >= segment.startMillis },
        )
        assertTrue(
            "Transcript segment start timestamps must be ordered",
            result.segments.zipWithNext().all { (previous, next) ->
                next.startMillis >= previous.startMillis
            },
        )
    }

    private companion object {
        const val ARG_RUN_MANUAL_ASR = "runManualAsr"
        const val ARG_EXPECTED_TEXT = "expectedText"
    }
}
