package com.iptvup.app.core.diag

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Registro de diagnóstico de toda la app, visible en Ajustes → Diagnóstico.
 * Se guarda en un fichero interno que sobrevive a los reinicios y conserva las últimas
 * [MAX_LINES] líneas. No se borra por tiempo: solo con el botón Borrar o al superar el límite.
 */
object DiagLog {

    private const val MAX_LINES = 1000
    private const val FILE_NAME = "diagnostico.log"
    private const val TRACE_LINES = 12

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
        val line = "${formatter.format(Date())}  $message"
        lines.addLast(line)
        if (lines.size > MAX_LINES) {
            lines.removeFirst()
            rewrite()
        } else {
            append(line)
        }
    }

    /** Registra un error con su tipo, mensaje y las primeras líneas de la traza. */
    @Synchronized
    fun logError(message: String, throwable: Throwable) {
        val trace = throwable.stackTrace.take(TRACE_LINES).joinToString("\n") { "    at $it" }
        log("$message: ${throwable::class.java.simpleName}: ${throwable.message}\n$trace")
    }

    @Synchronized
    fun dump(): String = lines.joinToString("\n")

    @Synchronized
    fun clear() {
        lines.clear()
        file?.delete()
    }

    private fun append(line: String) {
        runCatching { file?.appendText(line + "\n") }
    }

    private fun rewrite() {
        runCatching { file?.writeText(lines.joinToString("\n", postfix = "\n")) }
    }
}
