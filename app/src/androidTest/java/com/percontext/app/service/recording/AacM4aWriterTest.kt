package com.percontext.app.service.recording

import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlin.math.sin
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AacM4aWriterTest {
    @Test
    fun writesPcmAsReadableMonoAacM4a() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val output = File.createTempFile("pcm-writer-", ".m4a", context.cacheDir)
        try {
            ParcelFileDescriptor.open(
                output,
                ParcelFileDescriptor.MODE_READ_WRITE or
                    ParcelFileDescriptor.MODE_CREATE or
                    ParcelFileDescriptor.MODE_TRUNCATE,
            ).use { descriptor ->
                AacM4aWriter(
                    outputFileDescriptor = descriptor.fileDescriptor,
                    sampleRate = SAMPLE_RATE,
                    channelCount = 1,
                    bitRate = 64_000,
                ).use { writer ->
                    var offset = 0
                    while (offset < SAMPLE_RATE) {
                        val sampleCount = minOf(CHUNK_SIZE, SAMPLE_RATE - offset)
                        val samples = ShortArray(sampleCount) { index ->
                            val position = offset + index
                            (
                                sin(2.0 * Math.PI * TONE_HZ * position / SAMPLE_RATE) *
                                    Short.MAX_VALUE * 0.25
                                ).roundToInt().toShort()
                        }
                        writer.write(samples, sampleCount)
                        offset += sampleCount
                    }
                    writer.finish()
                }
            }

            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(output.absolutePath)
                assertEquals(1, extractor.trackCount)
                val format = extractor.getTrackFormat(0)
                assertEquals(MediaFormat.MIMETYPE_AUDIO_AAC, format.getString(MediaFormat.KEY_MIME))
                assertEquals(SAMPLE_RATE, format.getInteger(MediaFormat.KEY_SAMPLE_RATE))
                assertEquals(1, format.getInteger(MediaFormat.KEY_CHANNEL_COUNT))
                extractor.selectTrack(0)
                assertTrue(extractor.sampleTime >= 0L)
            } finally {
                extractor.release()
            }
        } finally {
            output.delete()
        }
    }

    private companion object {
        const val SAMPLE_RATE = 44_100
        const val CHUNK_SIZE = 2_048
        const val TONE_HZ = 440.0
    }
}
