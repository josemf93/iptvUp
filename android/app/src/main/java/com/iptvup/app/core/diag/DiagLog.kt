package com.iptvup.app.core.diag

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Registro en memoria de lo que pasa en el reproductor, para ver el diagnóstico desde Ajustes sin logcat. */
object DiagLog {

    private const val MAX_LINES = 300
    private val lines = ArrayDeque<String>()
    private val formatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    @Synchronized
    fun log(message: String) {
        lines.addLast("${formatter.format(Date())}  $message")
        while (lines.size > MAX_LINES) lines.removeFirst()
    }

    @Synchronized
    fun dump(): String = lines.joinToString("\n")

    @Synchronized
    fun clear() {
        lines.clear()
    }
}
