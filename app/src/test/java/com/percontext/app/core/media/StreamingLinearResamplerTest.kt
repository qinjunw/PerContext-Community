package com.percontext.app.core.media

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class StreamingLinearResamplerTest {
    @Test
    fun `same rate preserves samples across chunk boundaries`() {
        val resampler = StreamingLinearResampler(
            sourceSampleRate = 16_000,
            targetSampleRate = 16_000,
        )

        val output = collect(
            resampler.accept(floatArrayOf(-1f, -0.5f)),
            resampler.accept(floatArrayOf(0f, 0.5f)),
            resampler.finish(),
        )

        assertArrayEquals(floatArrayOf(-1f, -0.5f, 0f, 0.5f), output, 0.0001f)
    }

    @Test
    fun `downsampling produces the same output whether input is split or contiguous`() {
        val contiguous = StreamingLinearResampler(44_100, 16_000)
        val split = StreamingLinearResampler(44_100, 16_000)
        val input = FloatArray(441) { index -> index / 441f }

        val contiguousOutput = collect(contiguous.accept(input), contiguous.finish())
        val splitOutput = collect(
            split.accept(input.copyOfRange(0, 137)),
            split.accept(input.copyOfRange(137, 298)),
            split.accept(input.copyOfRange(298, input.size)),
            split.finish(),
        )

        assertArrayEquals(contiguousOutput, splitOutput, 0.0001f)
    }

    @Test
    fun `linear interpolation upsamples between adjacent source samples`() {
        val resampler = StreamingLinearResampler(
            sourceSampleRate = 2,
            targetSampleRate = 4,
        )

        val output = collect(
            resampler.accept(floatArrayOf(0f, 1f, 2f)),
            resampler.finish(),
        )

        assertArrayEquals(floatArrayOf(0f, 0.5f, 1f, 1.5f, 2f), output, 0.0001f)
    }

    private fun collect(vararg chunks: FloatArray): FloatArray =
        chunks.flatMap(FloatArray::asIterable).toFloatArray()
}
