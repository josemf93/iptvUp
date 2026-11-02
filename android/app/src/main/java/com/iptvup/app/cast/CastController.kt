package com.iptvup.app.cast

import android.content.Context
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener

class CastController(context: Context) {

    private val castContext = CastContext.getSharedInstance(context.applicationContext)
    private val sessionManager = castContext.sessionManager

    private var currentUrl: String? = null
    private var currentTitle: String = ""
    private var currentContentType: String = "application/x-mpegURL"
    private var onCastStarted: (() -> Unit)? = null
    private var onCastEnded: (() -> Unit)? = null

    private val listener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarted(session: CastSession, sessionId: String) {
            loadCurrent()
            onCastStarted?.invoke()
        }

        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
            loadCurrent()
            onCastStarted?.invoke()
        }

        override fun onSessionEnded(session: CastSession, error: Int) {
            onCastEnded?.invoke()
        }

        override fun onSessionStarting(session: CastSession) = Unit
        override fun onSessionStartFailed(session: CastSession, error: Int) = Unit
        override fun onSessionResumeFailed(session: CastSession, error: Int) = Unit
        override fun onSessionEnding(session: CastSession) = Unit
        override fun onSessionResuming(session: CastSession, sessionId: String) = Unit
        override fun onSessionSuspended(session: CastSession, reason: Int) = Unit
    }

    fun attach(
        onStarted: () -> Unit,
        onEnded: () -> Unit
    ) {
        onCastStarted = onStarted
        onCastEnded = onEnded
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
        onCastStarted = null
        onCastEnded = null
    }

    private fun loadCurrent() {
        val url = currentUrl ?: return
        val session = sessionManager.currentCastSession ?: return
        val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_GENERIC).apply {
            putString("com.google.android.gms.cast.metadata.TITLE", currentTitle)
        }
        val mediaInfo = MediaInfo.Builder(url)
            .setStreamType(MediaInfo.STREAM_TYPE_LIVE)
            .setContentType(currentContentType)
            .setMetadata(metadata)
            .build()
        session.remoteMediaClient?.load(mediaInfo)
    }

    private fun contentTypeFor(url: String): String = when {
        url.contains(".m3u8", ignoreCase = true) -> "application/x-mpegURL"
        url.contains(".mpd", ignoreCase = true) -> "application/dash+xml"
        url.contains(".ts", ignoreCase = true) -> "video/mp2t"
        else -> "video/*"
    }
}
