package com.percontext.app.feature.review

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalendarMonthTest {
    @Test
    fun `august 2026 uses monday first grid and 31 local days`() {
        val cells = CalendarMonth(2026, 7).cells()

        assertEquals(42, cells.size)
        assertNull(cells[4].day)
        assertEquals(1, cells[5].day)
        assertEquals("2026-08-01", cells[5].dayKey)
        assertEquals(31, cells[35].day)
    }

    @Test
    fun `month shifting crosses year boundary`() {
        assertEquals(CalendarMonth(2027, 0), CalendarMonth(2026, 11).shifted(1))
        assertEquals(CalendarMonth(2025, 11), CalendarMonth(2026, 0).shifted(-1))
    }
}
