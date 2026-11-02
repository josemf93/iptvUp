package com.iptvup.app.core.ui.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatTime(ms: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ms))

fun formatDateTime(ms: Long): String =
    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(ms))

fun formatDay(ms: Long): String =
    SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(Date(ms))

fun formatLastSync(ms: Long?): String =
    if (ms == null || ms <= 0L) "Nunca" else formatDateTime(ms)

fun progressFraction(now: Long, start: Long, end: Long): Float {
    if (end <= start) return 0f
    return ((now - start).toFloat() / (end - start).toFloat()).coerceIn(0f, 1f)
}
