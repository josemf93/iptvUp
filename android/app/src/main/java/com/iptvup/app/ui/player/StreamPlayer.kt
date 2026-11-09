package com.iptvup.app.ui.player

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

const val STREAM_MAX_AUTO_RETRIES = 3
const val STREAM_HARD_RESET_AFTER = 2
const val STALL_RECONNECT_MS = 8_000L
private const val STREAM_LOAD_RETRIES = 4

fun buildStreamPlayer(context: Context, okHttp: OkHttpClient): ExoPlayer {
    val streamClient = okHttp.newBuilder()
        .connectionPool(ConnectionPool())
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
    val dataSourceFactory = OkHttpDataSource.Factory(streamClient)
        .setUserAgent("IPTVUp/0.6")

    val mediaSourceFactory = DefaultMediaSourceFactory(context)
        .setDataSourceFactory(dataSourceFactory)
        .setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(STREAM_LOAD_RETRIES))

    val loadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(
            30_000,
            90_000,
            2_000,
            8_000
        )
        .build()

    val renderersFactory = DefaultRenderersFactory(context)
        .setEnableDecoderFallback(true)

    return ExoPlayer.Builder(context, renderersFactory)
        .setMediaSourceFactory(mediaSourceFactory)
        .setLoadControl(loadControl)
        .setHandleAudioBecomingNoisy(true)
        .build()
        .apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true
            )
        }
}
