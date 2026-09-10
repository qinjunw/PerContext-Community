package com.percontext.app.domain.dailycontext

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class LocalDayRange(
    val startMillis: Long,
    val endExclusiveMillis: Long,
)

fun localDayRange(dayKey: String, zoneId: String): LocalDayRange {
    val timeZone = TimeZone.getTimeZone(zoneId)
    require(timeZone.id == zoneId || zoneId == "GMT") { "Unknown time zone: $zoneId" }
    val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        isLenient = false
        this.timeZone = timeZone
    }
    val start = requireNotNull(formatter.parse(dayKey)) { "Invalid day: $dayKey" }
    val end = Calendar.getInstance(timeZone).apply {
        time = start
        add(Calendar.DAY_OF_MONTH, 1)
    }.timeInMillis
    return LocalDayRange(start.time, end)
}

fun dayKeyAt(epochMillis: Long, zoneId: String): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        timeZone = TimeZone.getTimeZone(zoneId)
    }.format(Date(epochMillis))

internal fun clockTimeAt(epochMillis: Long, zoneId: String): String =
    SimpleDateFormat("HH:mm", Locale.US).apply {
        timeZone = TimeZone.getTimeZone(zoneId)
    }.format(Date(epochMillis))
