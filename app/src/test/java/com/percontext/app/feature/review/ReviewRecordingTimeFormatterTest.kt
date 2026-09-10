package com.percontext.app.feature.review

import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

class ReviewRecordingTimeFormatterTest {
    @Test
    fun `recording time uses the explicit review zone`() {
        val recordedAt = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(2026, Calendar.AUGUST, 31, 23, 30)
        }.timeInMillis

        assertEquals("23:30", formatReviewRecordingTime(recordedAt, "UTC"))
        assertEquals("08:30", formatReviewRecordingTime(recordedAt, "Asia/Tokyo"))
    }
}
