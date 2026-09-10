package com.percontext.app.core.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

fun formatFileSize(bytes: Long?): String {
    if (bytes == null || bytes < 0L) return "大小未知"

    val unit = when {
        bytes < KIBIBYTE -> return "$bytes B"
        bytes < MEBIBYTE -> KIBIBYTE to "KB"
        bytes < GIBIBYTE -> MEBIBYTE to "MB"
        else -> GIBIBYTE to "GB"
    }
    val value = bytes.toDouble() / unit.first
    val pattern = if (value >= 10 || value % 1.0 == 0.0) "%.0f" else "%.1f"
    return "${String.format(Locale.ROOT, pattern, value)} ${unit.second}"
}

fun formatRecordDate(
    timestampMillis: Long,
    timeZone: TimeZone = TimeZone.getDefault(),
): String = SimpleDateFormat("yyyy年M月d日 HH:mm", Locale.SIMPLIFIED_CHINESE).run {
    this.timeZone = timeZone
    format(Date(timestampMillis))
}

private const val KIBIBYTE = 1_024L
private const val MEBIBYTE = KIBIBYTE * 1_024L
private const val GIBIBYTE = MEBIBYTE * 1_024L
