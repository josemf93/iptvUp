package com.iptvup.app.ui.live

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.iptvup.app.core.ui.components.ChannelLogo
import com.iptvup.app.core.ui.components.EmptyState
import com.iptvup.app.core.ui.components.ErrorBanner
import com.iptvup.app.core.ui.components.GroupVisibilityDialog
import com.iptvup.app.core.ui.components.QualityBadge
import com.iptvup.app.core.ui.components.SearchDialog
import com.iptvup.app.core.ui.components.TvIconButton
import com.iptvup.app.core.ui.theme.Accent
import com.iptvup.app.core.ui.theme.Primary
import com.iptvup.app.core.ui.theme.SurfaceDark
import com.iptvup.app.core.ui.theme.SurfaceElevated
import com.iptvup.app.core.ui.theme.TextPrimary
import com.iptvup.app.core.ui.theme.TextSecondary
import com.iptvup.app.core.ui.util.formatTime
import com.iptvup.app.data.local.EpgProgramEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val PREVIEW_DELAY_MS = 700L
private const val GROUP_MOVE_SCROLL_MS = 120

/** Desplaza la lista al grupo en movimiento con una animación más corta que la estándar. */
private suspend fun LazyListState.quickScrollToItem(index: Int) {
    val visible = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
    if (visible == null) {
        scrollToItem(index)
    } else {
        animateScrollBy(
            (visible.offset - layoutInfo.viewportStartOffset).toFloat(),
            tween(GROUP_MOVE_SCROLL_MS)
        )
    }
}

/** Diseño de TV: grupos o canales en el centro, vista previa y EPG a la derecha. */
@Composable
fun LiveTvLayout(
    onPlay: (String) -> Unit,
    viewModel: LiveViewModel = hiltViewModel()
) {
    val channelsState by viewModel.channels.collectAsStateWithLifecycle()
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    val allGroups by viewModel.allGroups.collectAsStateWithLifecycle()
    val groupCounts by viewModel.groupCounts.collectAsStateWithLifecycle()
    val hidden by viewModel.hidden.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val selectedGroup by viewModel.selectedGroup.collectAsStateWithLifecycle()
    val focusedChannel by viewModel.focusedChannel.collectAsStateWithLifecycle()
    val focusedPrograms by viewModel.focusedPrograms.collectAsStateWithLifecycle()
    val syncing by viewModel.syncing.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val ready by viewModel.ready.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewPlayer = remember { viewModel.createPreviewPlayer(context) }
    val previewUrl = focusedChannel?.streamUrl
    val currentUrl by rememberUpdatedState(previewUrl)

    DisposableEffect(Unit) {
        onDispose { previewPlayer.release() }
    }

    DisposableEffect(lifecycleOwner, previewPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> previewPlayer.stop()
                Lifecycle.Event.ON_START -> currentUrl?.let { url -> startPreview(previewPlayer, url) }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(previewUrl) {
        previewPlayer.stop()
        if (previewUrl != null) {
            delay(PREVIEW_DELAY_MS)
            startPreview(previewPlayer, previewUrl)
        }
    }

    var showVisibility by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var confirmRefresh by remember { mutableStateOf(false) }
    var moving by remember { mutableStateOf<String?>(null) }
    var moveTick by remember { mutableIntStateOf(0) }
    val movingRequester = remember { FocusRequester() }
    val groupsListState = rememberLazyListState()

    val visibleGroups = remember(groups, query) {
        if (query.isBlank()) groups else groups.filter { it.contains(query, ignoreCase = true) }
    }
    val hiddenGroups = remember(hidden) {
        hidden.filter { it.kind == com.iptvup.app.data.repository.HiddenRepository.KIND_GROUP }
            .map { it.itemKey }
            .toSet()
    }

    BackHandler(enabled = moving != null || selectedGroup != null) {
        val movingNow = moving
        if (movingNow != null) {
            viewModel.cancelGroupMove()
            moving = null
        } else {
            viewModel.focusChannel(null)
            viewModel.setQuery("")
            viewModel.selectGroup(null)
        }
    }

    LaunchedEffect(moveTick) {
        val movingNow = moving ?: return@LaunchedEffect
        if (moveTick == 0) return@LaunchedEffect
        val index = visibleGroups.indexOf(movingNow).coerceAtLeast(0)
        groupsListState.quickScrollToItem(index)
        runCatching { movingRequester.requestFocus() }
    }

    Row(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(0.6f)
                .fillMaxHeight()
                .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selectedGroup != null) {
                    TvIconButton(
                        onClick = {
                            viewModel.focusChannel(null)
                            viewModel.setQuery("")
                            viewModel.selectGroup(null)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Volver a grupos",
                            tint = TextSecondary
                        )
                    }
                }
                Text(
                    text = selectedGroup ?: "TV en directo",
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (syncing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = Primary,
                        strokeWidth = 2.dp
                    )
                } else {
                    TvIconButton(onClick = { confirmRefresh = true }) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Actualizar lista",
                            tint = TextSecondary
                        )
                    }
                }
                TvIconButton(onClick = { showVisibility = true }) {
                    Icon(
                        imageVector = Icons.Rounded.Visibility,
                        contentDescription = "Gestionar grupos",
                        tint = TextSecondary
                    )
                }
                TvIconButton(onClick = { showSearch = true }) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = "Buscar",
                        tint = if (query.isNotBlank()) Primary else TextSecondary
                    )
                }
            }

            if (syncing) {
                Text(
                    text = status.ifBlank { "Actualizando..." },
                    color = TextSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            error?.let { message ->
                Spacer(Modifier.height(8.dp))
                ErrorBanner(message = message, onRetry = viewModel::sync)
            }

            Spacer(Modifier.height(10.dp))

            when {
                !ready -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Primary)
                }

                selectedGroup == null -> {
                    if (visibleGroups.isEmpty()) {
                        EmptyState(
                            title = if (query.isBlank()) "No hay grupos" else "Sin resultados",
                            message = if (query.isBlank()) {
                                "Actualiza la lista para descargar los canales del perfil."
                            } else {
                                "Prueba con otro nombre de grupo."
                            },
                            actionLabel = if (query.isBlank()) null else "Quitar búsqueda",
                            onAction = if (query.isBlank()) null else ({ viewModel.setQuery("") })
                        )
                    } else {
                        LazyColumn(
                            state = groupsListState,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(visibleGroups, key = { it }) { group ->
                                val isMoving = moving == group
                                TvGroupRow(
                                    group = group,
                                    count = groupCounts[group] ?: 0,
                                    isMoving = isMoving,
                                    requester = if (isMoving) movingRequester else null,
                                    onSelect = {
                                        viewModel.setQuery("")
                                        viewModel.focusChannel(null)
                                        viewModel.selectGroup(group)
                                    },
                                    onStartMove = {
                                        moving = group
                                        viewModel.beginGroupMove()
                                    },
                                    onMove = { direction ->
                                        viewModel.moveGroup(group, direction)
                                        moveTick++
                                    },
                                    onEndMove = {
                                        viewModel.endGroupMove()
                                        moving = null
                                    }
                                )
                            }
                        }
                    }
                }

                channelsState.items.isEmpty() && channelsState.group == selectedGroup -> EmptyState(
                    title = if (query.isNotBlank()) "Sin resultados" else "Sin canales en este grupo",
                    message = if (query.isNotBlank()) {
                        "Prueba con otro canal o quita la búsqueda."
                    } else {
                        "No hay canales visibles en este grupo."
                    },
                    actionLabel = if (query.isNotBlank()) "Quitar búsqueda" else null,
                    onAction = if (query.isNotBlank()) ({ viewModel.setQuery("") }) else null
                )

                channelsState.group != selectedGroup -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Primary)
                }

                else -> LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(channelsState.items, key = { it.channel.id }) { item ->
                        TvChannelRow(
                            item = item,
                            onPlay = { onPlay(item.channel.id) },
                            onFocus = { viewModel.focusChannel(item.channel) },
                            onFavorite = { viewModel.toggleFavorite(item.channel) }
                        )
                    }
                }
            }
        }

        HorizontalDivider(
            modifier = Modifier
                .fillMaxHeight()
                .width(1.dp),
            color = SurfaceElevated
        )

        Column(
            modifier = Modifier
                .weight(0.4f)
                .fillMaxHeight()
                .padding(20.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                if (focusedChannel != null) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                useController = false
                                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                player = previewPlayer
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        text = "Selecciona un canal para previsualizarlo",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            focusedChannel?.let { channel ->
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = channel.name,
                        color = TextPrimary,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    QualityBadge(channel.name)
                }
                Text(
                    text = channel.groupName,
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = "Programación",
                color = TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            if (focusedChannel == null) {
                Text(
                    text = "Sin canal seleccionado.",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            } else if (focusedPrograms.isEmpty()) {
                Text(
                    text = "Sin información de EPG para este canal.",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                val nowMs = System.currentTimeMillis()
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(focusedPrograms, key = { it.id }) { program ->
                        TvProgramRow(program = program, nowMs = nowMs)
                    }
                }
            }
        }
    }

    if (showVisibility) {
        GroupVisibilityDialog(
            groups = allGroups,
            hiddenGroups = hiddenGroups,
            onToggle = viewModel::toggleGroupHidden,
            onShowAll = viewModel::showAllGroups,
            onHideAll = viewModel::hideAllGroups,
            onDismiss = { showVisibility = false }
        )
    }

    if (showSearch) {
        SearchDialog(
            initialQuery = query,
            onQueryChange = viewModel::setQuery,
            onDismiss = { showSearch = false }
        )
    }

    if (confirmRefresh) {
        AlertDialog(
            onDismissRequest = { confirmRefresh = false },
            title = { Text("Actualizar lista") },
            text = { Text("¿Desea actualizar la lista de canales?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmRefresh = false
                        viewModel.sync()
                    }
                ) {
                    Text("Sí")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRefresh = false }) {
                    Text("No")
                }
            }
        )
    }
}

private fun startPreview(player: ExoPlayer, url: String) {
    player.setMediaItem(MediaItem.fromUri(url))
    player.prepare()
    player.playWhenReady = true
}

private const val HOLD_TO_MOVE_MS = 1_000L

private class GroupHold {
    var job: Job? = null
    var moved = false
}

/**
 * Teclas de un grupo: mantener OK un segundo activa el modo mover (no depende del flag de
 * pulsación larga, que muchos mandos no envían); arriba/abajo lo desplazan y un OK nuevo lo guarda.
 */
private fun Modifier.groupMoveKeys(
    isMoving: Boolean,
    hold: GroupHold,
    scope: CoroutineScope,
    onStartMove: () -> Unit,
    onMove: (Int) -> Unit,
    onEndMove: () -> Unit
): Modifier = onPreviewKeyEvent { event ->
    val isCenter = event.key == Key.DirectionCenter || event.key == Key.Enter
    val isDown = event.type == KeyEventType.KeyDown
    val isRepeat = event.nativeKeyEvent.repeatCount > 0
    when {
        isMoving && isDown && event.key == Key.DirectionUp -> {
            onMove(-1)
            true
        }

        isMoving && isDown && event.key == Key.DirectionDown -> {
            onMove(1)
            true
        }

        isMoving && isCenter -> {
            if (isDown && !isRepeat) onEndMove()
            hold.moved = false
            true
        }

        !isMoving && isCenter && isDown -> {
            if (isRepeat) {
                hold.moved
            } else {
                hold.job?.cancel()
                hold.job = scope.launch {
                    delay(HOLD_TO_MOVE_MS)
                    hold.moved = true
                    onStartMove()
                }
                false
            }
        }

        !isMoving && isCenter && event.type == KeyEventType.KeyUp -> {
            hold.job?.cancel()
            hold.job = null
            val consumed = hold.moved
            hold.moved = false
            consumed
        }

        else -> false
    }
}

@Composable
private fun TvGroupRow(
    group: String,
    count: Int,
    isMoving: Boolean,
    requester: FocusRequester?,
    onSelect: () -> Unit,
    onStartMove: () -> Unit,
    onMove: (Int) -> Unit,
    onEndMove: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val shape = RoundedCornerShape(14.dp)
    val hold = remember { GroupHold() }
    val scope = rememberCoroutineScope()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (isMoving) Primary.copy(alpha = 0.25f) else SurfaceDark)
            .then(if (focused || isMoving) Modifier.border(2.dp, Primary, shape) else Modifier)
            .then(if (requester != null) Modifier.focusRequester(requester) else Modifier)
            .groupMoveKeys(
                isMoving = isMoving,
                hold = hold,
                scope = scope,
                onStartMove = onStartMove,
                onMove = onMove,
                onEndMove = onEndMove
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onSelect
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = group,
                color = TextPrimary,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "$count canales",
                color = TextSecondary,
                style = MaterialTheme.typography.labelSmall
            )
        }
        if (isMoving) {
            Icon(
                imageVector = Icons.Rounded.SwapVert,
                contentDescription = "Mover grupo",
                tint = Primary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun TvChannelRow(
    item: LiveChannelUi,
    onPlay: () -> Unit,
    onFocus: () -> Unit,
    onFavorite: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { if (it.isFocused) onFocus() }
            .clip(shape)
            .background(SurfaceDark)
            .then(if (focused) Modifier.border(2.dp, Primary, shape) else Modifier)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onPlay
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ChannelLogo(
            name = item.channel.name,
            logoUrl = item.channel.logoUrl,
            modifier = Modifier.size(44.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = item.channel.name,
            color = TextPrimary,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        QualityBadge(item.channel.name)
        TvIconButton(onClick = onFavorite) {
            Icon(
                imageVector = if (item.favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                contentDescription = if (item.favorite) "Quitar de favoritos" else "Añadir a favoritos",
                tint = if (item.favorite) Accent else TextSecondary
            )
        }
    }
}

@Composable
private fun TvProgramRow(program: EpgProgramEntity, nowMs: Long) {
    val isCurrent = nowMs >= program.startMs && nowMs < program.endMs
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isCurrent) Accent.copy(alpha = 0.18f) else Color.Transparent)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "${formatTime(program.startMs)} - ${formatTime(program.endMs)}",
            color = if (isCurrent) Accent else TextSecondary,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.width(120.dp)
        )
        Text(
            text = program.title,
            color = TextPrimary,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
