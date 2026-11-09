package com.iptvup.app.core.diag

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Registro del reproductor para ver el diagnóstico desde Ajustes sin logcat.
 * Se guarda en un fichero interno y conserva las últimas [MAX_LINES] líneas entre reinicios.
 * No se borra solo por tiempo: solo al pulsar Borrar en Ajustes o al superar el límite de líneas.
 */
object DiagLog {

    private const val MAX_LINES = 500
    private const val FILE_NAME = "diagnostico.log"

    private val lines = ArrayDeque<String>()
    private var file: File? = null
    private val formatter = SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault())

    @Synchronized
    fun init(context: Context) {
        val stored = File(context.filesDir, FILE_NAME)
        file = stored
        if (stored.exists()) {
            runCatching { stored.readLines() }
                .getOrDefault(emptyList())
                .takeLast(MAX_LINES)
                .forEach { lines.addLast(it) }
        }
    }

    @Synchronized
    fun log(message: String) {
        lines.addLast("${formatter.format(Date())}  $message")
        while (lines.size > MAX_LINES) lines.removeFirst()
        save()
    }

    @Synchronized
    fun dump(): String = lines.joinToString("\n")

    @Synchronized
    fun clear() {
        lines.clear()
        file?.delete()
    }

    private fun save() {
        runCatching { file?.writeText(lines.joinToString("\n", postfix = "\n")) }
    }
}
