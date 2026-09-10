package com.percontext.app.core.media

internal data class PcmWindow(
    val startSampleIndex: Long,
    val samples: FloatArray,
)

internal class PcmWindowChunker(
    private val windowSampleCount: Int,
    private val onWindow: (PcmWindow) -> Unit,
) {
    private var buffer: FloatArray
    private var bufferedSampleCount = 0
    private var emittedSampleCount = 0L
    private var finished = false

    init {
        require(windowSampleCount > 0) { "Window sample count must be positive" }
        buffer = FloatArray(windowSampleCount)
    }

    fun accept(samples: FloatArray) {
        check(!finished) { "Cannot accept PCM after finishing" }
        var sourceOffset = 0
        while (sourceOffset < samples.size) {
            val copyCount = minOf(
                windowSampleCount - bufferedSampleCount,
                samples.size - sourceOffset,
            )
            samples.copyInto(
                destination = buffer,
                destinationOffset = bufferedSampleCount,
                startIndex = sourceOffset,
                endIndex = sourceOffset + copyCount,
            )
            bufferedSampleCount += copyCount
            sourceOffset += copyCount
            if (bufferedSampleCount == windowSampleCount) emitFullWindow()
        }
    }

    fun finish() {
        check(!finished) { "PCM windowing has already finished" }
        finished = true
        if (bufferedSampleCount > 0) {
            emit(buffer.copyOf(bufferedSampleCount))
            bufferedSampleCount = 0
        }
    }

    private fun emitFullWindow() {
        val completedWindow = buffer
        buffer = FloatArray(windowSampleCount)
        bufferedSampleCount = 0
        emit(completedWindow)
    }

    private fun emit(samples: FloatArray) {
        onWindow(
            PcmWindow(
                startSampleIndex = emittedSampleCount,
                samples = samples,
            ),
        )
        emittedSampleCount += samples.size
    }
}
