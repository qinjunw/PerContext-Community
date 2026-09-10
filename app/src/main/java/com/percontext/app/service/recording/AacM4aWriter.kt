package com.percontext.app.service.recording

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
import android.os.SystemClock
import androidx.annotation.RequiresApi
import java.io.FileDescriptor
import java.nio.ByteOrder
import kotlin.math.min

@RequiresApi(Build.VERSION_CODES.O)
internal class AacM4aWriter(
    outputFileDescriptor: FileDescriptor,
    private val sampleRate: Int,
    channelCount: Int,
    bitRate: Int,
) : AutoCloseable {
    private lateinit var codec: MediaCodec
    private lateinit var muxer: MediaMuxer
    private val bufferInfo = MediaCodec.BufferInfo()
    private var codecStarted = false
    private var muxerStarted = false
    private var trackIndex = -1
    private var submittedSamples = 0L
    private var finished = false
    private var closed = false

    init {
        try {
            codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            muxer = MediaMuxer(
                outputFileDescriptor,
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4,
            )
            val format = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_AAC,
                sampleRate,
                channelCount,
            ).apply {
                setInteger(
                    MediaFormat.KEY_AAC_PROFILE,
                    MediaCodecInfo.CodecProfileLevel.AACObjectLC,
                )
                setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, MAX_INPUT_BYTES)
            }
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            codecStarted = true
        } catch (error: Throwable) {
            releaseResources()
            throw error
        }
    }

    fun write(samples: ShortArray, sampleCount: Int) {
        check(!closed && !finished) { "AAC writer is not accepting PCM" }
        require(sampleCount in 0..samples.size) { "Invalid PCM sample count" }

        var offset = 0
        while (offset < sampleCount) {
            drainOutput(waitForData = false)
            val inputIndex = codec.dequeueInputBuffer(CODEC_TIMEOUT_MICROS)
            if (inputIndex < 0) continue

            val inputBuffer = requireNotNull(codec.getInputBuffer(inputIndex)).apply {
                clear()
                order(ByteOrder.LITTLE_ENDIAN)
            }
            val acceptedSamples = min(
                inputBuffer.remaining() / Short.SIZE_BYTES,
                sampleCount - offset,
            )
            repeat(acceptedSamples) { index -> inputBuffer.putShort(samples[offset + index]) }
            codec.queueInputBuffer(
                inputIndex,
                0,
                acceptedSamples * Short.SIZE_BYTES,
                presentationTimeMicros(submittedSamples),
                0,
            )
            submittedSamples += acceptedSamples
            offset += acceptedSamples
        }
        drainOutput(waitForData = false)
    }

    fun finish() {
        check(!closed) { "AAC writer is closed" }
        if (finished) return

        val deadlineMillis = SystemClock.elapsedRealtime() + FINISH_TIMEOUT_MILLIS
        var inputEnded = false
        while (!inputEnded) {
            check(SystemClock.elapsedRealtime() < deadlineMillis) {
                "AAC encoder did not accept end of stream"
            }
            drainOutput(waitForData = false)
            val inputIndex = codec.dequeueInputBuffer(CODEC_TIMEOUT_MICROS)
            if (inputIndex >= 0) {
                codec.queueInputBuffer(
                    inputIndex,
                    0,
                    0,
                    presentationTimeMicros(submittedSamples),
                    MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                )
                inputEnded = true
            }
        }

        var outputEnded = false
        while (!outputEnded) {
            check(SystemClock.elapsedRealtime() < deadlineMillis) {
                "AAC encoder did not finish"
            }
            outputEnded = drainOutput(waitForData = true)
        }
        check(muxerStarted) { "AAC encoder produced no output format" }
        finished = true
    }

    private fun drainOutput(waitForData: Boolean): Boolean {
        while (true) {
            val outputIndex = codec.dequeueOutputBuffer(
                bufferInfo,
                if (waitForData) CODEC_TIMEOUT_MICROS else 0L,
            )
            when (outputIndex) {
                MediaCodec.INFO_TRY_AGAIN_LATER -> return false
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    check(!muxerStarted) { "AAC encoder changed output format twice" }
                    trackIndex = muxer.addTrack(codec.outputFormat)
                    muxer.start()
                    muxerStarted = true
                }

                else -> if (outputIndex >= 0) {
                    val outputBuffer = requireNotNull(codec.getOutputBuffer(outputIndex))
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                        bufferInfo.size = 0
                    }
                    if (bufferInfo.size > 0) {
                        check(muxerStarted) { "AAC data arrived before output format" }
                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(trackIndex, outputBuffer, bufferInfo)
                    }
                    val reachedEnd =
                        bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    codec.releaseOutputBuffer(outputIndex, false)
                    if (reachedEnd) return true
                }
            }
        }
    }

    private fun presentationTimeMicros(samplePosition: Long): Long =
        samplePosition * MICROS_PER_SECOND / sampleRate

    override fun close() {
        if (closed) return
        closed = true
        releaseResources()?.let { throw it }
    }

    private fun releaseResources(): Throwable? {
        var firstFailure: Throwable? = null
        if (this::codec.isInitialized) {
            if (codecStarted) {
                runCatching { codec.stop() }
                    .onFailure { if (firstFailure == null) firstFailure = it }
            }
            runCatching { codec.release() }
                .onFailure { if (firstFailure == null) firstFailure = it }
            codecStarted = false
        }
        if (this::muxer.isInitialized) {
            if (muxerStarted) {
                runCatching { muxer.stop() }
                    .onFailure { if (firstFailure == null) firstFailure = it }
            }
            runCatching { muxer.release() }
                .onFailure { if (firstFailure == null) firstFailure = it }
            muxerStarted = false
        }
        return firstFailure
    }

    private companion object {
        const val MAX_INPUT_BYTES = 8_192
        const val CODEC_TIMEOUT_MICROS = 10_000L
        const val FINISH_TIMEOUT_MILLIS = 10_000L
        const val MICROS_PER_SECOND = 1_000_000L
    }
}
