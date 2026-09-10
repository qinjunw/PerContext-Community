package com.percontext.app.service.recording

import kotlin.math.log10
import kotlin.math.sqrt

internal class PcmInputLevelMeter(
    private val noiseFloorDb: Float = -55f,
    private val fullScaleDb: Float = -12f,
    private val attack: Float = 0.75f,
    private val release: Float = 0.35f,
) {
    private var currentLevel = 0f

    fun update(samples: ShortArray, sampleCount: Int): Float {
        val count = sampleCount.coerceIn(0, samples.size)
        if (count == 0) return currentLevel

        var squaredSum = 0.0
        repeat(count) { index ->
            val sample = samples[index].toDouble()
            squaredSum += sample * sample
        }
        val rms = sqrt(squaredSum / count) / Short.MAX_VALUE
        val target = if (rms <= 0.0) {
            0f
        } else {
            val db = (20.0 * log10(rms)).toFloat()
            ((db - noiseFloorDb) / (fullScaleDb - noiseFloorDb)).coerceIn(0f, 1f)
        }
        val smoothing = if (target > currentLevel) attack else release
        currentLevel += (target - currentLevel) * smoothing
        return currentLevel.coerceIn(0f, 1f)
    }

    fun reset() {
        currentLevel = 0f
    }
}
