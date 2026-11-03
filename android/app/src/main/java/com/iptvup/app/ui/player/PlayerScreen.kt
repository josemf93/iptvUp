package com.iptvup.app.ui.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.os.Build
import android.util.Rational
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.iptvup.app.core.ui.components.TvIconButton
import com.iptvup.app.cast.CastButton
import com.iptvup.app.core.ui.components.TvIconButton
import com.iptvup.app.cast.CastController
import com.iptvup.app.core.ui.components.TvIconButton
import com.iptvup.app.core.pip.PipState
import com.iptvup.app.core.ui.components.TvIconButton
import com.iptvup.app.core.ui.util.isTelevision
import com.iptvup.app.core.ui.components.TvIconButton
import com.iptvup.app.core.ui.theme.Accent
import com.iptvup.app.core.ui.components.TvIconButton
import com.iptvup.app.core.ui.theme.Danger
import com.iptvup.app.core.ui.components.TvIconButton
import com.iptvup.app.core.ui.theme.Primary
import com.iptvup.app.core.ui.components.TvIconButton
import com.iptvup.app.core.ui.theme.SurfaceDark
import com.iptvup.app.core.ui.components.TvIconButton
import com.iptvup.app.core.ui.theme.SurfaceElevated
import com.iptvup.app.core.ui.components.TvIconButton
import com.iptvup.app.core.ui.theme.TextPrimary
import com.iptvup.app.core.ui.components.TvIconButton
import com.iptvup.app.core.ui.theme.TextSecondary
import com.iptvup.app.core.ui.components.TvIconButton
import com.iptvup.app.data.identity.ChannelQuality
import com.iptvup.app.core.ui.components.TvIconButton
import com.iptvup.app.core.ui.util.formatTime
import com.iptvup.app.core.ui.components.TvIconButton
import com.iptvup.app.core.ui.util.progressFraction
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun PlayerScreen(
    onBack: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val channel by viewModel.channel.collectAsStateWithLifecycle()
    val loaded by viewModel.loaded.collectAsStateWithLifecycle()
    val epg by viewModel.epg.collectAsStateWithLifecycle()
    val epgPrograms by viewModel.epgPrograms.collectAsStateWithLifecycle()
    val favorite by viewModel.favorite.collectAsStateWithLifecycle()
    val canFallback by viewModel.canFallback.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    var isPlaying by remember { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(true) }
    var playerError by remember { mutableStateOf<String?>(null) }
    var controlsVisible by remember { mutableStateOf(true) }
    var interactionTick by remember { mutableIntStateOf(0) }
    var isFullscreen by remember { mutableStateOf(false) }
    var epgExpanded by remember { mutableStateOf(false) }
    var gestureHint by remember { mutableStateOf<String?>(null) }
    var gestureHintTick by remember { mutableIntStateOf(0) }
    var userPaused by remember { mutableStateOf(false) }
    var showConnecting by remember { mutableStateOf(false) }
    var connectionCountdown by remember { mutableIntStateOf(0) }
    var autoRetries by remember { mutableIntStateOf(0) }
    var reconnectTick by remember { mutableIntStateOf(0) }
    var inBackground by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val pipMode by PipState.isInPip.collectAsStateWithLifecycle()
    val focusRequester = remember { FocusRequester() }
    val surfaceFocusRequester = remember { FocusRequester() }
    val view = LocalView.current
    val isTv = isTelevision()

    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }
    val maxVolume = remember(audioManager) {
        audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 0
    }

    val player = remember { viewModel.createPlayer(context) }

    val castController = remember(context, isTv) {
        if (isTv) {
            null
        } else {
            runCatching { CastController(context) }.getOrNull()
        }
    }

    val enterPip: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching {
                activity?.enterPictureInPictureMode(
                    PictureInPictureParams.Builder()
                        .setAspectRatio(Rational(16, 9))
                        .build()
                )
            }
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                if (playing) userPaused = false
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                buffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) autoRetries = 0
            }

            override fun onPlayerError(error: PlaybackException) {
                if (autoRetries < STREAM_MAX_AUTO_RETRIES) {
                    autoRetries++
                    reconnectTick++
                } else {
                    playerError = error.message ?: "No se pudo reproducir el canal"
                }
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    LaunchedEffect(channel?.streamUrl) {
        val url = channel?.streamUrl ?: return@LaunchedEffect
        playerError = null
        autoRetries = 0
        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()
        player.playWhenReady = true
    }

    LaunchedEffect(reconnectTick) {
        if (reconnectTick == 0) return@LaunchedEffect
        delay(1_000L * autoRetries)
        if (inBackground) return@LaunchedEffect
        player.prepare()
        player.playWhenReady = true
    }

    LaunchedEffect(buffering, channel?.id) {
        if (!buffering || userPaused || inBackground) return@LaunchedEffect
        delay(STALL_RECONNECT_MS)
        if (buffering && !userPaused && !inBackground && playerError == null &&
            autoRetries < STREAM_MAX_AUTO_RETRIES
        ) {
            autoRetries++
            reconnectTick++
        }
    }

    DisposableEffect(Unit) {
        onDispose { player.release() }
    }

    DisposableEffect(lifecycleOwner, player) {
        var resumeOnStart = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    inBackground = true
                    resumeOnStart = true
                    player.stop()
                }

                Lifecycle.Event.ON_START -> {
                    if (resumeOnStart) {
                        resumeOnStart = false
                        inBackground = false
                        if (player.currentMediaItem != null) {
                            player.prepare()
                            player.playWhenReady = !userPaused
                        }
                    }
                }

                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(castController, player) {
        castController?.attach(
            onStarted = {
                if (player.isPlaying) player.pause()
            },
            onEnded = {
                runCatching { player.play() }
            }
        )
        onDispose { castController?.detach() }
    }

    LaunchedEffect(channel?.streamUrl, castController) {
        val current = channel ?: return@LaunchedEffect
        castController?.load(current.streamUrl, current.name)
    }

    DisposableEffect(activity) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            activity?.window?.let { window ->
                val params = window.attributes
                params.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                window.attributes = params
            }
        }
    }

    LaunchedEffect(pipMode) {
        if (!pipMode) {
            controlsVisible = true
        }
    }

    DisposableEffect(activity, isTv) {
        if (!isTv && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val builder = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                builder.setAutoEnterEnabled(true)
            }
            activity?.setPictureInPictureParams(builder.build())
        }
        onDispose {
            if (!isTv && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                activity?.setPictureInPictureParams(
                    PictureInPictureParams.Builder()
                        .setAutoEnterEnabled(false)
                        .build()
                )
            }
        }
    }

    DisposableEffect(isFullscreen, activity) {
        val window = activity?.window
        activity?.requestedOrientation = if (isFullscreen) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, view)
            if (isFullscreen) {
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            if (window != null) {
                WindowCompat.getInsetsController(window, view)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    LaunchedEffect(controlsVisible, isPlaying, interactionTick, epgExpanded) {
        if (controlsVisible && isPlaying && !epgExpanded) {
            delay(4_000L)
            controlsVisible = false
        }
    }

    LaunchedEffect(gestureHintTick) {
        if (gestureHint != null) {
            delay(900L)
            gestureHint = null
        }
    }

    LaunchedEffect(controlsVisible, channel?.id) {
        if (controlsVisible && channel != null) {
            runCatching { focusRequester.requestFocus() }
        } else if (!controlsVisible) {
            runCatching { surfaceFocusRequester.requestFocus() }
        }
    }

    LaunchedEffect(channel?.id) {
        controlsVisible = true
        userPaused = false
        showConnecting = false
        connectionCountdown = 0
        interactionTick++
    }

    LaunchedEffect(channel?.id, isPlaying, userPaused, inBackground, canFallback, playerError) {
        if (isPlaying || userPaused || inBackground) {
            showConnecting = false
            connectionCountdown = 0
            return@LaunchedEffect
        }

        delay(10_000L)
        if (isPlaying || userPaused || inBackground) {
            showConnecting = false
            connectionCountdown = 0
            return@LaunchedEffect
        }

        showConnecting = true
        if (canFallback) {
            for (remaining in 5 downTo 1) {
                connectionCountdown = remaining
                delay(1_000L)
                if (isPlaying || userPaused || inBackground) {
                    showConnecting = false
                    connectionCountdown = 0
                    return@LaunchedEffect
                }
            }
            connectionCountdown = 0
            showConnecting = false
            if (!viewModel.fallbackToNext()) {
                playerError = playerError ?: "No se pudo reproducir el canal"
            }
        } else {
            delay(5_000L)
            if (!isPlaying && !userPaused && !inBackground) {
                playerError = playerError ?: "No se pudo reproducir el canal"
            }
            showConnecting = false
        }
    }

    BackHandler {
        if (epgExpanded) {
            epgExpanded = false
        } else {
            onBack()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent { event ->
                if (event.key == Key.Back) {
                    false
                } else if (event.type == KeyEventType.KeyDown) {
                    val isLeft = event.key == Key.DirectionLeft
                    val isRight = event.key == Key.DirectionRight
                    when {
                        !controlsVisible && !epgExpanded && (isLeft || isRight) -> {
                            viewModel.changeChannel(if (isRight) 1 else -1)
                            true
                        }

                        !controlsVisible && !epgExpanded -> {
                            controlsVisible = true
                            interactionTick++
                            true
                        }

                        else -> {
                            interactionTick++
                            false
                        }
                    }
                } else {
                    false
                }
            }
    ) {
        if (channel == null) {
            if (!loaded) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Primary
                )
            } else {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Canal no disponible",
                        color = TextPrimary,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onBack) {
                        Text("Volver")
                    }
                }
            }
        } else {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        this.player = player
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                    }
                },
                update = { view -> view.player = player },
                modifier = Modifier.fillMaxSize()
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .focusRequester(surfaceFocusRequester)
                    .focusable()
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            var totalX = 0f
                            var totalY = 0f
                            var lastPosition = down.position
                            var mode: String? = null
                            val onRightSide = down.position.x > size.width / 2f
                            val startVolume = audioManager
                                ?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
                            val startBrightness = activity?.window?.attributes
                                ?.screenBrightness
                                ?.takeIf { it >= 0f } ?: 0.5f

                            var dragging = true
                            while (dragging) {
                                val event = awaitPointerEvent()
                                val change = event.changes
                                    .firstOrNull { it.id == down.id } ?: break
                                val delta = change.position - lastPosition
                                lastPosition = change.position
                                totalX += delta.x
                                totalY += delta.y
                                if (mode == null &&
                                    (abs(totalX) > 20f || abs(totalY) > 20f)
                                ) {
                                    mode = if (abs(totalY) > abs(totalX)) {
                                        "vertical"
                                    } else {
                                        "horizontal"
                                    }
                                }
                                if (mode == "vertical") {
                                    change.consume()
                                    val height = size.height.coerceAtLeast(1).toFloat()
                                    if (onRightSide) {
                                        if (maxVolume > 0) {
                                            val deltaVolume =
                                                (-totalY / (height * 0.6f)) * maxVolume
                                            val newVolume = (startVolume + deltaVolume)
                                                .roundToInt()
                                                .coerceIn(0, maxVolume)
                                            audioManager?.setStreamVolume(
                                                AudioManager.STREAM_MUSIC,
                                                newVolume,
                                                0
                                            )
                                            gestureHint =
                                                "Volumen ${newVolume * 100 / maxVolume}%"
                                        }
                                    } else {
                                        val deltaBrightness = -totalY / (height * 0.6f)
                                        val newBrightness =
                                            (startBrightness + deltaBrightness)
                                                .coerceIn(0.02f, 1f)
                                        activity?.window?.let { window ->
                                            val params = window.attributes
                                            params.screenBrightness = newBrightness
                                            window.attributes = params
                                        }
                                        gestureHint =
                                            "Brillo ${(newBrightness * 100).roundToInt()}%"
                                    }
                                    gestureHintTick++
                                } else if (mode == "horizontal") {
                                    change.consume()
                                }

                                if (!change.pressed) {
                                    if (mode == null) {
                                        controlsVisible = !controlsVisible
                                        interactionTick++
                                    } else if (!epgExpanded &&
                                        mode == "horizontal" &&
                                        abs(totalX) > size.width * 0.12f &&
                                        abs(totalX) > abs(totalY)
                                    ) {
                                        viewModel.changeChannel(if (totalX < 0) 1 else -1)
                                    }
                                    dragging = false
                                }
                            }
                        }
                    }
            )

            if (controlsVisible && !pipMode) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.82f), Color.Transparent)
                            )
                        )
                        .windowInsetsPadding(
                            WindowInsets.safeDrawing.only(
                                WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                            )
                        )
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TvIconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Volver",
                                tint = Color.White
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = channel?.name.orEmpty(),
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val quality = channel?.let { ChannelQuality.label(it.name) }
                            Text(
                                text = listOfNotNull(channel?.groupName, quality)
                                    .joinToString(" · "),
                                color = Color.White.copy(alpha = 0.65f),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1
                            )
                        }
                        if (!isTv && castController != null) {
                            CastButton(
                                modifier = Modifier
                                    .size(44.dp)
                                    .padding(start = 4.dp)
                            )
                        }
                        if (!isTv && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            TvIconButton(
                                onClick = {
                                    enterPip()
                                    controlsVisible = true
                                    interactionTick++
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PictureInPictureAlt,
                                    contentDescription = "Picture-in-Picture",
                                    tint = Color.White
                                )
                            }
                        }
                        TvIconButton(
                            onClick = {
                                viewModel.toggleFavorite()
                                controlsVisible = true
                                interactionTick++
                            }
                        ) {
                            Icon(
                                imageVector = if (favorite) {
                                    Icons.Rounded.Star
                                } else {
                                    Icons.Rounded.StarBorder
                                },
                                contentDescription = if (favorite) {
                                    "Quitar de favoritos"
                                } else {
                                    "Añadir a favoritos"
                                },
                                tint = if (favorite) Accent else Color.White
                            )
                        }
                    }
                }
            }

            if (controlsVisible && !pipMode) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(78.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.48f))
                        .focusRequester(focusRequester)
                        .clickable {
                            if (player.isPlaying) {
                                player.pause()
                                userPaused = true
                            } else {
                                player.play()
                                userPaused = false
                            }
                            controlsVisible = true
                            interactionTick++
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (isPlaying) "Pausar" else "Reproducir",
                        tint = Color.White,
                        modifier = Modifier.size(42.dp)
                    )
                }
            }

            if (buffering && playerError == null && !showConnecting && autoRetries == 0) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.White
                )
            }

            if (autoRetries > 0 && playerError == null && !showConnecting &&
                !pipMode && !inBackground
            ) {
                Row(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = 0.72f))
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = Primary,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "Reconectando…",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            playerError?.let { message ->
                if (!showConnecting && (!canFallback || userPaused)) {
                    Card(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceDark)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "No se pudo reproducir",
                                color = TextPrimary,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = message,
                                color = Danger,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(Modifier.height(14.dp))
                            Button(
                                onClick = {
                                    playerError = null
                                    userPaused = false
                                    showConnecting = false
                                    connectionCountdown = 0
                                    autoRetries = 0
                                    player.prepare()
                                    player.playWhenReady = true
                                    controlsVisible = true
                                    interactionTick++
                                }
                            ) {
                                Text("Reintentar")
                            }
                        }
                    }
                }
            }

            if (showConnecting && !pipMode) {
                Card(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp),
                            color = Primary,
                            strokeWidth = 3.dp
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "Intentando conectar...",
                            color = TextPrimary,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(4.dp))
                        if (canFallback) {
                            Text(
                                text = "Cambiando al siguiente canal en " +
                                    "${connectionCountdown}s",
                                color = TextSecondary,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { connectionCountdown / 5f },
                                modifier = Modifier.fillMaxWidth(),
                                color = Primary,
                                trackColor = SurfaceElevated
                            )
                        } else {
                            Text(
                                text = "Esperando respuesta del canal...",
                                color = TextSecondary,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            if (controlsVisible && !pipMode) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))
                            )
                        )
                        .windowInsetsPadding(
                            WindowInsets.safeDrawing.only(
                                WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal
                            )
                        )
                        .padding(horizontal = 20.dp, vertical = 22.dp)
                ) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    epgExpanded = true
                                    controlsVisible = true
                                    interactionTick++
                                }
                                .padding(vertical = 6.dp, horizontal = 4.dp)
                        ) {
                            Text(
                                text = epg.now?.title ?: "Sin información de EPG",
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            epg.now?.let { now ->
                                Spacer(Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = {
                                        progressFraction(
                                            now = System.currentTimeMillis(),
                                            start = now.startMs,
                                            end = now.endMs
                                        )
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(5.dp),
                                    color = Accent,
                                    trackColor = SurfaceElevated
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = "${formatTime(now.startMs)} - ${formatTime(now.endMs)}" +
                                        (epg.next?.let { "  ·  Después: ${it.title}" } ?: ""),
                                    color = Color.White.copy(alpha = 0.72f),
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Toca para ver la guía completa",
                                color = Accent,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        if (!isTv) {
                            TvIconButton(
                                onClick = {
                                    isFullscreen = !isFullscreen
                                    controlsVisible = true
                                    interactionTick++
                                },
                                modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
                            ) {
                                Icon(
                                    imageVector = if (isFullscreen) {
                                        Icons.Rounded.FullscreenExit
                                    } else {
                                        Icons.Rounded.Fullscreen
                                    },
                                    contentDescription = if (isFullscreen) {
                                        "Salir de pantalla completa"
                                    } else {
                                        "Pantalla completa"
                                    },
                                    tint = Color.White
                                )
                            }
                        }
                    }
                }
            }

            if (epgExpanded && !pipMode) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.55f))
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) {
                            epgExpanded = false
                        }
                )
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .fillMaxHeight(0.64f)
                        .padding(12.dp)
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { },
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = channel?.name.orEmpty(),
                                    color = TextPrimary,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Guía completa del canal",
                                    color = TextSecondary,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            TvIconButton(onClick = { epgExpanded = false }) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Cerrar guía",
                                    tint = Color.White
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        if (epgPrograms.isEmpty()) {
                            Text(
                                text = "Sin información de EPG para este canal.",
                                color = TextSecondary,
                                style = MaterialTheme.typography.bodySmall
                            )
                        } else {
                            val nowMs = System.currentTimeMillis()
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                            ) {
                                items(epgPrograms, key = { it.id }) { program ->
                                    val isCurrent = nowMs >= program.startMs &&
                                        nowMs < program.endMs
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(
                                                if (isCurrent) {
                                                    Accent.copy(alpha = 0.18f)
                                                } else {
                                                    Color.Transparent
                                                }
                                            )
                                            .clickable { }
                                            .padding(horizontal = 10.dp, vertical = 8.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = "${formatTime(program.startMs)} - " +
                                                    formatTime(program.endMs),
                                                color = if (isCurrent) Accent else TextSecondary,
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                            if (isCurrent) {
                                                Text(
                                                    text = "  ·  Ahora",
                                                    color = Accent,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                        }
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            text = program.title,
                                            color = TextPrimary,
                                            style = MaterialTheme.typography.titleSmall,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        val description = program.description
                                        if (!description.isNullOrBlank()) {
                                            Spacer(Modifier.height(2.dp))
                                            Text(
                                                text = description,
                                                color = TextSecondary,
                                                style = MaterialTheme.typography.bodySmall,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    HorizontalDivider(
                                        color = SurfaceElevated.copy(alpha = 0.5f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            gestureHint?.let { hint ->
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = 0.78f))
                        .padding(horizontal = 22.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = hint,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

private fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
