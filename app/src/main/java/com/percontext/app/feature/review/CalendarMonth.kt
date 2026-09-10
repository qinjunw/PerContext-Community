package com.percontext.app.feature.review

import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

data class CalendarMonth(
    val year: Int,
    val month: Int,
) {
    val title: String = String.format(Locale.CHINA, "%d 年 %d 月", year, month + 1)

    fun cells(): List<CalendarDayCell> {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.US).apply {
            clear()
            set(year, month, 1)
        }
        val mondayOffset = (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7
        val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        return List(42) { index ->
            val day = index - mondayOffset + 1
            if (day in 1..daysInMonth) {
                CalendarDayCell(
                    day = day,
                    dayKey = String.format(
                        Locale.US,
                        "%04d-%02d-%02d",
                        year,
                        month + 1,
                        day,
                    ),
                )
            } else {
                CalendarDayCell(day = null, dayKey = null)
            }
        }
    }

    fun shifted(delta: Int): CalendarMonth {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.US).apply {
            clear()
            set(year, month, 1)
            add(Calendar.MONTH, delta)
        }
        return CalendarMonth(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH))
    }

    fun firstDayKey(): String = String.format(Locale.US, "%04d-%02d-01", year, month + 1)

    companion object {
        fun at(epochMillis: Long, zoneId: String): CalendarMonth {
            val calendar = Calendar.getInstance(TimeZone.getTimeZone(zoneId)).apply {
                timeInMillis = epochMillis
            }
            return CalendarMonth(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH))
        }
    }
}

data class CalendarDayCell(
    val day: Int?,
    val dayKey: String?,
)
