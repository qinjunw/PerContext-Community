package com.percontext.app.core.media

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.percontext.app.data.file.MediaStoreAudioFileStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteOrder
import java.util.UUID
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.roundToInt

@RunWith(AndroidJUnit4::class)
class AndroidPcmAudioDecoderTest {
    @Test
    fun decodesSyntheticM4aToStreamedMono16KhzPcm() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val source = createSyntheticM4a(context)
        var receivedSamples = 0L
        var chunkCount = 0
        var peak = 0f

        try {
            val result = AndroidPcmAudioDecoder(context).decodeMono16Khz(
                source.absolutePath,
            ) { samples ->
                receivedSamples += samples.size
                chunkCount += 1
                samples.forEach { sample -> peak = maxOf(peak, abs(sample)) }
            }

            assertEquals(SOURCE_SAMPLE_RATE, result.sourceSampleRate)
            assertEquals(1, result.sourceChannelCount)
            assertEquals(result.outputSampleCount, receivedSamples)
            assertTrue("Decoder should stream multiple chunks", chunkCount > 1)
            assertTrue(
                "Decoded duration should remain close to the generated fixture",
                result.outputDurationMillis in 1_000L..1_400L,
            )
            assertTrue("Decoded PCM should contain audible samples", peak > 0.01f)
        } finally {
            source.delete()
        }
    }

    @Test
    fun decodesPublishedMediaStoreContentUri() = runBlocking {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val source = createSyntheticM4a(context)
        val store = MediaStoreAudioFileStore(context.contentResolver)
        val pending = store.createPendingRecording("record_${UUID.randomUUID()}")
        val duplicate = ParcelFileDescriptor.dup(pending.fileDescriptor)
        source.inputStream().use { input ->
            ParcelFileDescriptor.AutoCloseOutputStream(duplicate).use { output ->
                input.copyTo(output)
            }
        }
        pending.publish()
        val audioLocation = pending.audioLocation
        var receivedSamples = 0L

        try {
            val result = AndroidPcmAudioDecoder(context).decodeMono16Khz(
                audioLocation,
            ) { samples ->
                receivedSamples += samples.size
            }

            assertEquals(result.outputSampleCount, receivedSamples)
            assertTrue(result.outputDurationMillis in 1_000L..1_400L)
        } finally {
            store.deleteOwnedFile(audioLocation)
            source.delete()
        }
    }

    private fun createSyntheticM4a(context: Context): File {
        val output = File.createTempFile("decoder-fixture-", ".m4a", context.cacheDir)
        var completed = false
        try {
            val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            var codecStarted = false
            try {
                val muxer = MediaMuxer(
                    output.absolutePath,
                    MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4,
                )
                var muxerStarted = false
                try {
                    val format = MediaFormat.createAudioFormat(
                        MediaFormat.MIMETYPE_AUDIO_AAC,
                        SOURCE_SAMPLE_RATE,
                        1,
                    ).apply {
                        setInteger(
                            MediaFormat.KEY_AAC_PROFILE,
                            MediaCodecInfo.CodecProfileLevel.AACObjectLC,
                        )
                        setInteger(MediaFormat.KEY_BIT_RATE, 64_000)
                        setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 4_096)
                    }
                    codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                    codec.start()
                    codecStarted = true

                    val bufferInfo = MediaCodec.BufferInfo()
                    val totalSamples = SOURCE_SAMPLE_RATE * FIXTURE_DURATION_MILLIS / 1_000
                    var submittedSamples = 0
                    var inputEnded = false
                    var outputEnded = false
                    var trackIndex = -1
                    val deadlineMillis = SystemClock.elapsedRealtime() + ENCODE_TIMEOUT_MILLIS

                    while (!outputEnded) {
                        check(SystemClock.elapsedRealtime() < deadlineMillis) {
                            "AAC encoder did not finish the synthetic fixture"
                        }
                        if (!inputEnded) {
                            val inputIndex = codec.dequeueInputBuffer(CODEC_TIMEOUT_MICROS)
                            if (inputIndex >= 0) {
                                val inputBuffer =
                                    requireNotNull(codec.getInputBuffer(inputIndex)).apply {
                                        clear()
                                        order(ByteOrder.LITTLE_ENDIAN)
                                    }
                                val sampleCount = min(
                                    inputBuffer.remaining() / Short.SIZE_BYTES,
                                    totalSamples - submittedSamples,
                                )
                                val presentationTimeMicros =
                                    submittedSamples * 1_000_000L / SOURCE_SAMPLE_RATE
                                if (sampleCount == 0) {
                                    codec.queueInputBuffer(
                                        inputIndex,
                                        0,
                                        0,
                                        presentationTimeMicros,
                                        MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                                    )
                                    inputEnded = true
                                } else {
                                    repeat(sampleCount) { offset ->
                                        val samplePosition = submittedSamples + offset
                                        val sample = (
                                            sin(
                                                2.0 * Math.PI * TONE_HZ * samplePosition /
                                                    SOURCE_SAMPLE_RATE,
                                            ) * Short.MAX_VALUE * 0.25
                                            ).roundToInt().toShort()
                                        inputBuffer.putShort(sample)
                                    }
                                    codec.queueInputBuffer(
                                        inputIndex,
                                        0,
                                        sampleCount * Short.SIZE_BYTES,
                                        presentationTimeMicros,
                                        0,
                                    )
                                    submittedSamples += sampleCount
                                }
                            }
                        }

                        when (
                            val outputIndex = codec.dequeueOutputBuffer(
                                bufferInfo,
                                CODEC_TIMEOUT_MICROS,
                            )
                        ) {
                            MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                            MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                                check(!muxerStarted) { "AAC encoder changed output format twice" }
                                trackIndex = muxer.addTrack(codec.outputFormat)
                                muxer.start()
                                muxerStarted = true
                            }

                            else -> if (outputIndex >= 0) {
                                val outputBuffer =
                                    requireNotNull(codec.getOutputBuffer(outputIndex))
                                if (
                                    bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                                ) {
                                    bufferInfo.size = 0
                                }
                                if (bufferInfo.size > 0) {
                                    check(muxerStarted) {
                                        "AAC data arrived before the output format"
                                    }
                                    outputBuffer.position(bufferInfo.offset)
                                    outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                                    muxer.writeSampleData(trackIndex, outputBuffer, bufferInfo)
                                }
                                outputEnded =
                                    bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                                codec.releaseOutputBuffer(outputIndex, false)
                            }
                        }
                    }
                } finally {
                    try {
                        if (muxerStarted) muxer.stop()
                    } finally {
                        muxer.release()
                    }
                }
            } finally {
                try {
                    if (codecStarted) codec.stop()
                } finally {
                    codec.release()
                }
            }
            completed = true
            return output
        } finally {
            if (!completed) output.delete()
        }
    }

    private companion object {
        const val SOURCE_SAMPLE_RATE = 44_100
        const val FIXTURE_DURATION_MILLIS = 1_200
        const val TONE_HZ = 440.0
        const val CODEC_TIMEOUT_MICROS = 10_000L
        const val ENCODE_TIMEOUT_MILLIS = 10_000L
    }
}
