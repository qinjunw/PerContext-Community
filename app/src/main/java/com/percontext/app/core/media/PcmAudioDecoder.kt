package com.percontext.app.core.media

interface PcmAudioDecoder {
    suspend fun decodeMono16Khz(
        audioLocation: String,
        onSamples: (FloatArray) -> Unit,
    ): PcmDecodeResult
}

data class PcmDecodeResult(
    val sourceSampleRate: Int,
    val sourceChannelCount: Int,
    val outputSampleCount: Long,
) {
    val outputDurationMillis: Long
        get() = outputSampleCount * 1_000L / TARGET_SAMPLE_RATE

    companion object {
        const val TARGET_SAMPLE_RATE = 16_000
    }
}
