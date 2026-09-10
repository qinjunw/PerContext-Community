package com.percontext.app.service.recording

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Process
import androidx.annotation.RequiresApi
import java.io.FileDescriptor
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred

internal class PcmAacRecordingEngine(
    private val outputFileDescriptor: FileDescriptor,
    private val onInputLevel: (Float) -> Unit,
) : PcmRecordingEngine {
    private val stopRequested = AtomicBoolean(false)
    private val completion = CountDownLatch(1)
    private val terminal = CompletableDeferred<PcmRecordingTerminalResult>()
    private var audioRecord: AudioRecord? = null
    private var inputShutdown: PcmInputShutdownRequester? = null
    private var captureThread: Thread? = null
    @Volatile
    private var captureFailure: Throwable? = null

    override val terminalResult: Deferred<PcmRecordingTerminalResult> = terminal

    @SuppressLint("MissingPermission")
    @RequiresApi(Build.VERSION_CODES.O)
    override fun start() {
        check(audioRecord == null) { "PCM recording engine is already started" }
        val minimumBufferBytes = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        check(minimumBufferBytes > 0) { "PCM input format is unavailable" }
        val bufferSizeBytes = maxOf(minimumBufferBytes * 2, MINIMUM_BUFFER_BYTES)
        val recorder = AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.MIC)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(bufferSizeBytes)
            .build()
        check(recorder.state == AudioRecord.STATE_INITIALIZED) {
            "PCM input could not be initialized"
        }
        val shutdown = PcmInputShutdownRequester(
            stopInput = { recorder.stop() },
            releaseInput = { recorder.release() },
        )

        val writer = try {
            AacM4aWriter(
                outputFileDescriptor = outputFileDescriptor,
                sampleRate = recorder.sampleRate,
                channelCount = 1,
                bitRate = AAC_BIT_RATE,
            )
        } catch (error: Throwable) {
            shutdown.requestRelease()
            throw error
        }

        try {
            recorder.startRecording()
            check(recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                "PCM input did not start"
            }
        } catch (error: Throwable) {
            runCatching { writer.close() }
            shutdown.requestRelease()
            throw error
        }

        audioRecord = recorder
        inputShutdown = shutdown
        captureThread = Thread(
            {
                capture(
                    recorder = recorder,
                    shutdown = shutdown,
                    writer = writer,
                    bufferSizeSamples = bufferSizeBytes / Short.SIZE_BYTES,
                )
            },
            "PerContextPcmCapture",
        ).apply { start() }
    }

    override suspend fun stop() = stopBlocking()

    private fun stopBlocking() {
        checkNotNull(audioRecord) { "PCM recording engine is not started" }
        val shutdown = checkNotNull(inputShutdown) { "PCM input shutdown is not initialized" }
        val thread = checkNotNull(captureThread) { "PCM capture thread is not started" }
        stopRequested.set(true)
        PcmCaptureStopCoordinator(
            requestStop = shutdown::requestStop,
            awaitCompletion = { timeoutMillis ->
                completion.await(timeoutMillis, TimeUnit.MILLISECONDS)
            },
            forceRelease = {
                shutdown.requestRelease()
                thread.interrupt()
            },
            relinquishOwnership = {
                audioRecord = null
                inputShutdown = null
                captureThread = null
            },
            gracefulTimeoutMillis = STOP_TIMEOUT_MILLIS,
            forcedTimeoutMillis = FORCED_STOP_TIMEOUT_MILLIS,
        ).stop()
        captureFailure?.let { throw it }
    }

    override fun release() {
        val shutdown = inputShutdown ?: return
        stopRequested.set(true)
        shutdown.requestStop()
        shutdown.requestRelease()
        captureThread?.interrupt()
        audioRecord = null
        inputShutdown = null
        captureThread = null
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun capture(
        recorder: AudioRecord,
        shutdown: PcmInputShutdownRequester,
        writer: AacM4aWriter,
        bufferSizeSamples: Int,
    ) {
        val samples = ShortArray(bufferSizeSamples)
        val levelMeter = PcmInputLevelMeter()
        var capturedSamples = 0L
        try {
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
            while (!stopRequested.get()) {
                val sampleCount = recorder.read(
                    samples,
                    0,
                    samples.size,
                    AudioRecord.READ_BLOCKING,
                )
                when {
                    sampleCount > 0 -> {
                        capturedSamples += sampleCount
                        onInputLevel(levelMeter.update(samples, sampleCount))
                        writer.write(samples, sampleCount)
                    }

                    sampleCount < 0 && !stopRequested.get() -> {
                        error("PCM input read failed: $sampleCount")
                    }
                }
            }
            check(capturedSamples > 0L) { "PCM input produced no audio" }
            writer.finish()
        } catch (error: Throwable) {
            captureFailure = error
        } finally {
            runCatching { onInputLevel(0f) }
                .onFailure { if (captureFailure == null) captureFailure = it }
            shutdown.requestStop()
            shutdown.requestRelease()
            runCatching { writer.close() }
                .onFailure { if (captureFailure == null) captureFailure = it }
            completion.countDown()
            terminal.complete(
                captureFailure?.let(PcmRecordingTerminalResult::Failed)
                    ?: PcmRecordingTerminalResult.Completed,
            )
        }
    }

    private companion object {
        const val SAMPLE_RATE = 44_100
        const val AAC_BIT_RATE = 64_000
        const val MINIMUM_BUFFER_BYTES = 4_096
        const val STOP_TIMEOUT_MILLIS = 15_000L
        const val FORCED_STOP_TIMEOUT_MILLIS = 2_000L
    }
}
