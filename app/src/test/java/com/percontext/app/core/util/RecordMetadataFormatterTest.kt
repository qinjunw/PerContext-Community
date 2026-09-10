package com.percontext.app.core.util

import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

class RecordMetadataFormatterTest {
    @Test
    fun `formats file size with one useful decimal`() {
        assertEquals("0 B", formatFileSize(0L))
        assertEquals("1 KB", formatFileSize(1_024L))
        assertEquals("1.5 KB", formatFileSize(1_536L))
        assertEquals("1.5 MB", formatFileSize(1_572_864L))
        assertEquals("大小未知", formatFileSize(null))
    }

    @Test
    fun `formats record date to minute without seconds`() {
        assertEquals(
            "1970年1月1日 00:00",
            formatRecordDate(0L, TimeZone.getTimeZone("UTC")),
        )
    }
}
