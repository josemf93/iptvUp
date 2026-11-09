package com.iptvup.app.cast

import android.content.Context
import android.net.Uri
import android.widget.Toast
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.iptvup.app.core.diag.DiagLog

class CastController(private val context: Context) {

    private val castContext = CastContext.getSharedInstance(context.applicationContext)
    private val sessionManager = castContext.sessionManager

    private var currentUrl: String? = null
    private var currentTitle: String = ""
    private var currentContentType: String = "application/x-mpegURL"
    private var onCastStarted: (() -> Unit)? = null
    private var onCastEnded: (() -> Unit)? = null
    private var onCastError: (() -> Unit)? = null
    private var mediaClient: RemoteMediaClient? = null
    private var lastState: Int? = null
    private var errorReported = false

    private val mediaCallback = object : RemoteMediaClient.Callback() {
        override fun onStatusUpdated() {
            val client = mediaClient ?: return
            val state = client.playerState
            if (state != lastState) {
                lastState = state
                DiagLog.log("Chromecast: ${stateName(state)}")
            }
            if (state == MediaStatus.PLAYER_STATE_IDLE &&
                client.idleReason == MediaStatus.IDLE_REASON_ERROR
            ) {
                reportError("el receptor de la TV terminó con error")
            }
        }
    }

    private val listener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarted(session: CastSession, sessionId: String) {
            DiagLog.log("Chromecast: sesión iniciada con ${session.castDevice?.friendlyName}")
            loadCurrent()
            onCastStarted?.invoke()
        }

        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
            DiagLog.log("Chromecast: sesión reanudada")
            loadCurrent()
            onCastStarted?.invoke()
        }

        override fun onSessionEnded(session: CastSession, error: Int) {
            DiagLog.log("Chromecast: sesión terminada (código $error)")
            mediaClient?.unregisterCallback(mediaCallback)
            mediaClient = null
            onCastEnded?.invoke()
        }

        override fun onSessionStartFailed(session: CastSession, error: Int) {
            DiagLog.log("Chromecast: no se pudo iniciar la sesión (código $error)")
        }

        override fun onSessionResumeFailed(session: CastSession, error: Int) {
            DiagLog.log("Chromecast: no se pudo reanudar la sesión (código $error)")
        }

        override fun onSessionSuspended(session: CastSession, reason: Int) {
            DiagLog.log("Chromecast: sesión suspendida (motivo $reason)")
        }

        override fun onSessionStarting(session: CastSession) = Unit
        override fun onSessionEnding(session: CastSession) = Unit
        override fun onSessionResuming(session: CastSession, sessionId: String) = Unit
    }

    fun attach(
        onStarted: () -> Unit,
        onEnded: () -> Unit,
        onError: () -> Unit
    ) {
        onCastStarted = onStarted
        onCastEnded = onEnded
        onCastError = onError
        sessionManager.addSessionManagerListener(listener, CastSession::class.java)
        if (sessionManager.currentCastSession != null) {
            loadCurrent()
            onCastStarted?.invoke()
        }
    }

    fun load(url: String, title: String) {
        currentUrl = url
        currentTitle = title
        currentContentType = contentTypeFor(url)
        loadCurrent()
    }

    fun detach() {
        sessionManager.removeSessionManagerListener(listener, CastSession::class.java)
        mediaClient?.unregisterCallback(mediaCallback)
        mediaClient = null
        onCastStarted = null
        onCastEnded = null
        onCastError = null
    }

    private fun loadCurrent() {
        val url = currentUrl ?: return
        val session = sessionManager.currentCastSession ?: return
        val client = session.remoteMediaClient
        if (client == null) {
            DiagLog.log("Chromecast: la sesión no tiene cliente multimedia; no se envía el canal")
            return
        }
        if (mediaClient !== client) {
            mediaClient?.unregisterCallback(mediaCallback)
            client.registerCallback(mediaCallback)
            mediaClient = client
        }
        errorReported = false
        lastState = null
        val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_GENERIC).apply {
            putString("com.google.android.gms.cast.metadata.TITLE", currentTitle)
        }
        val mediaInfo = MediaInfo.Builder(url)
            .setStreamType(MediaInfo.STREAM_TYPE_LIVE)
            .setContentType(currentContentType)
            .setMetadata(metadata)
            .build()
        val uri = Uri.parse(url)
        DiagLog.log("Chromecast: cargando '$currentTitle' ($currentContentType, ${uri.scheme}://${uri.host})")
        client.load(mediaInfo).setResultCallback { result ->
            if (!result.status.isSuccess) {
                reportError("la TV rechazó la carga (código ${result.status.statusCode})")
            }
        }
    }

    private fun reportError(reason: String) {
        if (errorReported) return
        errorReported = true
        DiagLog.log("Chromecast: error, $reason")
        Toast.makeText(
            context,
            "La TV no puede reproducir este canal. Se sigue en el móvil.",
            Toast.LENGTH_LONG
        ).show()
        onCastError?.invoke()
    }

    private fun stateName(state: Int): String = when (state) {
        MediaStatus.PLAYER_STATE_IDLE -> "inactivo"
        MediaStatus.PLAYER_STATE_PLAYING -> "reproduciendo"
        MediaStatus.PLAYER_STATE_PAUSED -> "pausado"
        MediaStatus.PLAYER_STATE_BUFFERING -> "cargando"
        else -> "estado $state"
    }

    private fun contentTypeFor(url: String): String = when {
        url.contains(".m3u8", ignoreCase = true) -> "application/x-mpegURL"
        url.contains(".mpd", ignoreCase = true) -> "application/dash+xml"
        url.contains(".ts", ignoreCase = true) -> "video/mp2t"
        else -> "video/*"
    }
}
