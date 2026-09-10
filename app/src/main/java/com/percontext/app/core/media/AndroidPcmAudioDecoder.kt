package com.percontext.app.core.media

import android.content.ContentResolver
import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import androidx.core.net.toUri
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max

class AndroidPcmAudioDecoder(
    context: Context,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : PcmAudioDecoder {
    private val applicationContext = context.applicationContext

    override suspend fun decodeMono16Khz(
        audioLocation: String,
        onSamples: (FloatArray) -> Unit,
    ): PcmDecodeResult = withContext(dispatcher) {
        decodeBlocking(audioLocation, onSamples)
    }

    private suspend fun decodeBlocking(
        audioLocation: String,
        onSamples: (FloatArray) -> Unit,
    ): PcmDecodeResult {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            val sourceName = setDataSource(extractor, audioLocation)
            val trackIndex = findAudioTrack(extractor)
            require(trackIndex >= 0) { "No audio track found in $sourceName" }
            extractor.selectTrack(trackIndex)
            val inputFormat = extractor.getTrackFormat(trackIndex)
            val mimeType = requireNotNull(inputFormat.getString(MediaFormat.KEY_MIME))
            val sourceSampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val sourceChannelCount = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

            codec = MediaCodec.createDecoderByType(mimeType)
            codec.configure(inputFormat, null, null, 0)
            codec.start()

            val bufferInfo = MediaCodec.BufferInfo()
            var inputEnded = false
            var outputEnded = false
            var outputFormat: MediaFormat? = null
            var resampler: StreamingLinearResampler? = null
            var outputSampleCount = 0L

            while (!outputEnded) {
                currentCoroutineContext().ensureActive()
                if (!inputEnded) {
                    val inputIndex = codec.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
                    if (inputIndex >= 0) {
                        val inputBuffer = requireNotNull(codec.getInputBuffer(inputIndex))
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(
                                inputIndex,
                                0,
                                0,
                                0L,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                            )
                            inputEnded = true
                        } else {
                            codec.queueInputBuffer(
                                inputIndex,
                                0,
                                sampleSize,
                                max(0L, extractor.sampleTime),
                                0,
                            )
                            extractor.advance()
                        }
                    }
                }

                when (val outputIndex = codec.dequeueOutputBuffer(bufferInfo, DEQUEUE_TIMEOUT_US)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> outputFormat = codec.outputFormat
                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    else -> if (outputIndex >= 0) {
                        if (bufferInfo.size > 0 &&
                            bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0
                        ) {
                            val format = outputFormat ?: codec.outputFormat
                            val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                            val channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                            val pcmEncoding = format.integerOrDefault(
                                key = PCM_ENCODING_KEY,
                                defaultValue = AudioFormat.ENCODING_PCM_16BIT,
                            )
                            val monoSamples = decodeMonoSamples(
                                buffer = requireNotNull(codec.getOutputBuffer(outputIndex)),
                                offset = bufferInfo.offset,
                                size = bufferInfo.size,
                                channelCount = channelCount,
                                pcmEncoding = pcmEncoding,
                            )
                            val activeResampler = resampler ?: StreamingLinearResampler(
                                sourceSampleRate = sampleRate,
                                targetSampleRate = PcmDecodeResult.TARGET_SAMPLE_RATE,
                            ).also { resampler = it }
                            val output = activeResampler.accept(monoSamples)
                            if (output.isNotEmpty()) {
                                currentCoroutineContext().ensureActive()
                                onSamples(output)
                                outputSampleCount += output.size
                            }
                        }
                        outputEnded = bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        codec.releaseOutputBuffer(outputIndex, false)
                    }
                }
            }

            resampler?.finish()?.takeIf(FloatArray::isNotEmpty)?.let { output ->
                currentCoroutineContext().ensureActive()
                onSamples(output)
                outputSampleCount += output.size
            }
            return PcmDecodeResult(
                sourceSampleRate = sourceSampleRate,
                sourceChannelCount = sourceChannelCount,
                outputSampleCount = outputSampleCount,
            )
        } finally {
            codec?.let { activeCodec ->
                runCatching { activeCodec.stop() }
                runCatching { activeCodec.release() }
            }
            runCatching { extractor.release() }
        }
    }

    private fun setDataSource(extractor: MediaExtractor, audioLocation: String): String {
        val uri = audioLocation.toUri()
        return if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            extractor.setDataSource(applicationContext, uri, null)
            uri.lastPathSegment ?: "content recording"
        } else {
            val source = if (uri.scheme == ContentResolver.SCHEME_FILE) {
                File(requireNotNull(uri.path))
            } else {
                File(audioLocation)
            }
            require(source.isFile) { "Audio file does not exist: $source" }
            extractor.setDataSource(source.absolutePath)
            source.name
        }
    }

    private fun findAudioTrack(extractor: MediaExtractor): Int =
        (0 until extractor.trackCount).firstOrNull { index ->
            extractor.getTrackFormat(index)
                .getString(MediaFormat.KEY_MIME)
                ?.startsWith("audio/") == true
        } ?: -1

    private fun decodeMonoSamples(
        buffer: ByteBuffer,
        offset: Int,
        size: Int,
        channelCount: Int,
        pcmEncoding: Int,
    ): FloatArray {
        require(channelCount > 0) { "PCM channel count must be positive" }
        val bytesPerSample = when (pcmEncoding) {
            AudioFormat.ENCODING_PCM_16BIT -> Short.SIZE_BYTES
            AudioFormat.ENCODING_PCM_FLOAT -> Float.SIZE_BYTES
            else -> error("Unsupported decoded PCM encoding: $pcmEncoding")
        }
        val frameSize = bytesPerSample * channelCount
        require(size % frameSize == 0) { "PCM output is not frame aligned" }
        val frames = size / frameSize
        val input = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN).apply {
            position(offset)
            limit(offset + size)
        }
        return FloatArray(frames) {
            var mixed = 0f
            repeat(channelCount) {
                mixed += when (pcmEncoding) {
                    AudioFormat.ENCODING_PCM_16BIT -> input.short / 32_768f
                    AudioFormat.ENCODING_PCM_FLOAT -> input.float.coerceIn(-1f, 1f)
                    else -> error("Unsupported decoded PCM encoding: $pcmEncoding")
                }
            }
            (mixed / channelCount).coerceIn(-1f, 1f)
        }
    }

    private fun MediaFormat.integerOrDefault(key: String, defaultValue: Int): Int =
        if (containsKey(key)) getInteger(key) else defaultValue

    private companion object {
        // Same platform key as MediaFormat.KEY_PCM_ENCODING, without referencing its API 24 field.
        const val PCM_ENCODING_KEY = "pcm-encoding"
        const val DEQUEUE_TIMEOUT_US = 10_000L
    }
}
