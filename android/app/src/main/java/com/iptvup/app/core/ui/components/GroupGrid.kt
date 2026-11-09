package com.iptvup.app.core.ui.components

import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChildCare
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Newspaper
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.SportsSoccer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.iptvup.app.core.ui.theme.Primary
import com.iptvup.app.core.ui.theme.TextPrimary
import com.iptvup.app.core.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private const val EDGE_PX = 140f
private const val MAX_AUTOSCROLL_STEP_PX = 14f

/**
 * Lista de grupos. Con [onReorder] activo, mantener pulsado un grupo y arrastrarlo lo reordena:
 * la tarjeta sigue al dedo en una capa propia, la lista hace scroll en los bordes y el nuevo
 * orden se entrega al soltar. El orden de arrastre vive aquí, de forma síncrona, para que las
 * demás tarjetas se reacomoden sin retraso.
 */
@Composable
fun GroupGrid(
    groups: List<String>,
    counts: Map<String, Int>,
    minCardWidth: Dp,
    onSelect: (String) -> Unit,
    listMode: Boolean = false,
    onReorder: ((List<String>) -> Unit)? = null
) {
    val gridState = rememberLazyGridState()
    val density = LocalDensity.current
    val currentGroups by rememberUpdatedState(groups)
    val currentOnReorder by rememberUpdatedState(onReorder)
    var order by remember { mutableStateOf(groups) }
    var dragKey by remember { mutableStateOf<String?>(null) }
    var fingerPos by remember { mutableStateOf(Offset.Zero) }
    var grabOffset by remember { mutableStateOf(Offset.Zero) }
    var dragSize by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(groups) {
        if (dragKey == null) order = groups
    }

    fun overlayTopLeft(): Offset {
        val viewport = gridState.layoutInfo.viewportSize
        val maxX = (viewport.width - dragSize.width).coerceAtLeast(0).toFloat()
        val maxY = (viewport.height - dragSize.height).coerceAtLeast(0).toFloat()
        val raw = fingerPos - grabOffset
        return Offset(raw.x.coerceIn(0f, maxX), raw.y.coerceIn(0f, maxY))
    }

    fun moveIfOverOther(key: String) {
        val topLeft = overlayTopLeft()
        val centerX = topLeft.x + dragSize.width / 2f
        val centerY = topLeft.y + dragSize.height / 2f
        val target = gridState.layoutInfo.visibleItemsInfo.firstOrNull { item ->
            val itemKey = item.key as? String ?: return@firstOrNull false
            // Solo se acepta si la capa ya refleja el orden actual; si no, el dato es antiguo.
            itemKey != key &&
                item.index == order.indexOf(itemKey) &&
                centerX >= item.offset.x && centerX < item.offset.x + item.size.width &&
                centerY >= item.offset.y && centerY < item.offset.y + item.size.height
        }?.key as? String ?: return
        val from = order.indexOf(key)
        val to = order.indexOf(target)
        if (from < 0 || to < 0 || from == to) return
        order = order.toMutableList().apply {
            removeAt(from)
            add(to, key)
        }
    }

    fun endDrag() {
        if (dragKey == null) return
        dragKey = null
        if (order != currentGroups) currentOnReorder?.invoke(order)
    }

    fun cancelDrag() {
        dragKey = null
        order = currentGroups
    }

    LaunchedEffect(dragKey) {
        val key = dragKey ?: return@LaunchedEffect
        while (true) {
            delay(16)
            val viewport = gridState.layoutInfo.viewportSize.height.toFloat()
            val y = fingerPos.y.coerceIn(0f, viewport)
            val step = when {
                y < EDGE_PX -> -((EDGE_PX - y) / 10f).coerceAtMost(MAX_AUTOSCROLL_STEP_PX)
                y > viewport - EDGE_PX -> ((y - (viewport - EDGE_PX)) / 10f).coerceAtMost(MAX_AUTOSCROLL_STEP_PX)
                else -> 0f
            }
            if (step != 0f) {
                gridState.scroll(MutatePriority.Default) { scrollBy(step) }
            }
            moveIfOverOther(key)
        }
    }

    Box(modifier = Modifier.fillMaxSize().clipToBounds()) {
        LazyVerticalGrid(
            state = gridState,
            columns = if (listMode) GridCells.Fixed(1) else GridCells.Adaptive(minSize = minCardWidth),
            contentPadding = PaddingValues(20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(order, key = { it }) { group ->
                val isDragged = dragKey == group
                val dragModifier = if (onReorder != null) {
                    Modifier.pointerInput(group) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { offset ->
                                val item = gridState.layoutInfo.visibleItemsInfo
                                    .firstOrNull { it.key == group }
                                if (item != null) {
                                    dragSize = item.size
                                    grabOffset = offset
                                    fingerPos = Offset(item.offset.x.toFloat(), item.offset.y.toFloat()) + offset
                                    dragKey = group
                                }
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                fingerPos += amount
                            },
                            onDragEnd = { endDrag() },
                            onDragCancel = { cancelDrag() }
                        )
                    }
                } else {
                    Modifier
                }
                GroupCard(
                    group = group,
                    count = counts[group] ?: 0,
                    listMode = listMode,
                    onClick = { onSelect(group) },
                    modifier = Modifier
                        .animateItem()
                        .graphicsLayer { alpha = if (isDragged) 0f else 1f }
                        .then(dragModifier)
                )
            }
        }

        dragKey?.let { key ->
            Box(
                modifier = Modifier
                    .zIndex(1f)
                    .offset {
                        val topLeft = overlayTopLeft()
                        IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt())
                    }
                    .size(
                        width = with(density) { dragSize.width.toDp() },
                        height = with(density) { dragSize.height.toDp() }
                    )
            ) {
                GroupCard(
                    group = key,
                    count = counts[key] ?: 0,
                    listMode = listMode,
                    onClick = {},
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun GroupCard(
    group: String,
    count: Int,
    listMode: Boolean,
    onClick: () -> Unit,
    modifier: Modifier
) {
    if (listMode) {
        FocusableCard(
            onClick = onClick,
            modifier = modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GroupIcon(group)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = group,
                        color = TextPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "$count canales",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    } else {
        FocusableCard(
            onClick = onClick,
            modifier = modifier.fillMaxWidth().aspectRatio(1f)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                GroupIcon(group)
                Spacer(Modifier.height(12.dp))
                Text(
                    text = group,
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "$count canales",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun GroupIcon(group: String) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Primary.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = groupIconFor(group),
            contentDescription = null,
            tint = Primary
        )
    }
}

private fun groupIconFor(name: String): ImageVector {
    val value = name.lowercase()
    return when {
        listOf("deporte", "sport", "futbol", "motor").any { it in value } ->
            Icons.Rounded.SportsSoccer
        listOf("cine", "pelic", "movie", "estreno").any { it in value } ->
            Icons.Rounded.Movie
        listOf("infantil", "kids", "nino", "dibujo").any { it in value } ->
            Icons.Rounded.ChildCare
        listOf("musica", "music").any { it in value } ->
            Icons.Rounded.MusicNote
        listOf("noticia", "news", "informa").any { it in value } ->
            Icons.Rounded.Newspaper
        listOf("juego", "game", "esport").any { it in value } ->
            Icons.Rounded.SportsEsports
        else -> Icons.Rounded.LiveTv
    }
}
