package com.percontext.app.core.media

import kotlin.math.ceil
import kotlin.math.floor

internal class StreamingLinearResampler(
    sourceSampleRate: Int,
    targetSampleRate: Int,
) {
    private val sourceStep = sourceSampleRate.toDouble() / targetSampleRate
    private var nextSourcePosition = 0.0
    private var sourceSamplesSeen = 0L
    private var previousSample: Float? = null
    private var finished = false

    init {
        require(sourceSampleRate > 0) { "Source sample rate must be positive" }
        require(targetSampleRate > 0) { "Target sample rate must be positive" }
    }

    fun accept(samples: FloatArray): FloatArray {
        check(!finished) { "Cannot accept samples after finish" }
        if (samples.isEmpty()) return FloatArray(0)

        val chunkStart = sourceSamplesSeen
        val availableStart = if (previousSample == null) chunkStart else chunkStart - 1L
        val availableEnd = chunkStart + samples.lastIndex
        val estimatedSize = ceil(samples.size / sourceStep).toInt() + 2
        val output = FloatArray(estimatedSize)
        var outputSize = 0

        while (true) {
            val lowerIndex = floor(nextSourcePosition).toLong()
            val upperIndex = lowerIndex + 1L
            if (lowerIndex < availableStart || upperIndex > availableEnd) break

            val fraction = (nextSourcePosition - lowerIndex).toFloat()
            val lower = sampleAt(lowerIndex, chunkStart, samples)
            val upper = sampleAt(upperIndex, chunkStart, samples)
            output[outputSize++] = lower + (upper - lower) * fraction
            nextSourcePosition += sourceStep
        }

        sourceSamplesSeen += samples.size
        previousSample = samples.last()
        return output.copyOf(outputSize)
    }

    fun finish(): FloatArray {
        if (finished) return FloatArray(0)
        finished = true
        val lastSample = previousSample ?: return FloatArray(0)
        val lastIndex = sourceSamplesSeen - 1L
        return if (nextSourcePosition <= lastIndex + POSITION_EPSILON) {
            floatArrayOf(lastSample)
        } else {
            FloatArray(0)
        }
    }

    private fun sampleAt(
        globalIndex: Long,
        chunkStart: Long,
        samples: FloatArray,
    ): Float = if (globalIndex == chunkStart - 1L) {
        requireNotNull(previousSample)
    } else {
        samples[(globalIndex - chunkStart).toInt()]
    }

    private companion object {
        const val POSITION_EPSILON = 1e-9
    }
}
