package com.iptvup.app.core.stream

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.iptvup.app.core.diag.DiagLog

/**
 * Streams que no reproduce ExoPlayer (motor Ace Stream, acestream://, sop://...).
 * Se abren con otra app instalada, como hace WisePlay.
 */
object ExternalStreams {

    private val SCHEMES = setOf("acestream", "sop", "magnet")

    fun isExternal(url: String): Boolean {
        val value = url.trim()
        val scheme = value.substringBefore("://", "").lowercase()
        return scheme in SCHEMES || value.contains("/ace/getstream", ignoreCase = true)
    }

    fun open(context: Context, url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url.trim()))
        val chooser = Intent.createChooser(intent, "Abrir canal con").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(chooser) }
            .onSuccess { DiagLog.log("Canal externo abierto (${Uri.parse(url).scheme}://${Uri.parse(url).host})") }
            .onFailure {
                DiagLog.logError("No hay ninguna app para abrir el canal externo", it)
                Toast.makeText(
                    context,
                    "No tienes una app para abrir este canal. Instala Ace Stream o VLC.",
                    Toast.LENGTH_LONG
                ).show()
            }
    }
}
