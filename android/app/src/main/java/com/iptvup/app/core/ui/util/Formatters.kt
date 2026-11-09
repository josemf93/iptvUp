package com.iptvup.app.core.ui.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val timeFormat: ThreadLocal<SimpleDateFormat> = ThreadLocal.withInitial {
    SimpleDateFormat("HH:mm", Locale.getDefault())
}

fun formatTime(ms: Long): String = timeFormat.get()!!.format(Date(ms))

fun formatDateTime(ms: Long): String =
    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(ms))

fun formatDay(ms: Long): String =
    SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(Date(ms))

private val todayFormat: ThreadLocal<SimpleDateFormat> = ThreadLocal.withInitial {
    SimpleDateFormat("EEEE, d 'de' MMMM", Locale("es", "ES"))
}

/** Fecha de hoy en español, por ejemplo "Lunes 9 de noviembre". */
fun formatToday(): String = todayFormat.get()!!.format(Date())
    .replaceFirstChar { it.titlecase(Locale("es", "ES")) }

fun formatLastSync(ms: Long?): String =
    if (ms == null || ms <= 0L) "Nunca" else formatDateTime(ms)

fun progressFraction(now: Long, start: Long, end: Long): Float {
    if (end <= start) return 0f
    return ((now - start).toFloat() / (end - start).toFloat()).coerceIn(0f, 1f)
}
