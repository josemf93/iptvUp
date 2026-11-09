package com.iptvup.app.core.ui.components

import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.iptvup.app.core.ui.theme.Primary
import com.iptvup.app.core.ui.theme.TextPrimary
import com.iptvup.app.core.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** Acciones de reordenado por arrastre. [onOver] se llama cuando el dedo entra en otro grupo. */
data class GroupReorder(
    val onStart: () -> Unit,
    val onOver: (dragged: String, target: String) -> Unit,
    val onEnd: () -> Unit,
    val onCancel: () -> Unit
)

private const val EDGE_PX = 140f
private const val MAX_AUTOSCROLL_STEP_PX = 7f

@Composable
fun GroupGrid(
    groups: List<String>,
    counts: Map<String, Int>,
    minCardWidth: Dp,
    onSelect: (String) -> Unit,
    reorder: GroupReorder? = null
) {
    val gridState = rememberLazyGridState()
    val currentReorder by rememberUpdatedState(reorder)
    var dragging by remember { mutableStateOf<String?>(null) }
    var fingerPos by remember { mutableStateOf(Offset.Zero) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var blockedTarget by remember { mutableStateOf<String?>(null) }

    /** Posición del dedo limitada al bloque de grupos, para que el destino no se pierda fuera de la lista. */
    fun pointerInViewport(): Offset {
        val info = gridState.layoutInfo
        val maxX = (info.viewportSize.width - 1).coerceAtLeast(1).toFloat()
        val maxY = (info.viewportSize.height - 1).coerceAtLeast(1).toFloat()
        return Offset(
            fingerPos.x.coerceIn(0f, maxX),
            fingerPos.y.coerceIn(0f, maxY)
        )
    }

    /** Mantiene la tarjeta arrastrada dentro del bloque de grupos, sin invadir cabecera ni barra inferior. */
    fun clampDraggedCard(dragged: String) {
        val info = gridState.layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.key == dragged } ?: return
        val viewportHeight = info.viewportSize.height.toFloat()
        val viewportWidth = info.viewportSize.width.toFloat()
        val top = item.offset.y + dragOffset.y
        val bottom = top + item.size.height
        val left = item.offset.x + dragOffset.x
        val right = left + item.size.width
        val correctionX = when {
            left < 0f -> -left
            right > viewportWidth -> viewportWidth - right
            else -> 0f
        }
        val correctionY = when {
            top < 0f -> -top
            bottom > viewportHeight -> viewportHeight - bottom
            else -> 0f
        }
        if (correctionX != 0f || correctionY != 0f) {
            dragOffset += Offset(correctionX, correctionY)
        }
    }

    fun checkTarget(dragged: String) {
        val reorderNow = currentReorder ?: return
        val items = gridState.layoutInfo.visibleItemsInfo
        val draggedInfo = items.firstOrNull { it.key == dragged } ?: return
        val pointer = pointerInViewport()
        val direct = items.firstOrNull { item ->
            pointer.x >= item.offset.x && pointer.x < item.offset.x + item.size.width &&
                pointer.y >= item.offset.y && pointer.y < item.offset.y + item.size.height
        }
        // Si el dedo está en un hueco o en el borde, se elige la tarjeta más cercana.
        val over = direct ?: items
            .filter { it.key != dragged }
            .minByOrNull { item ->
                val dx = item.offset.x + item.size.width / 2f - pointer.x
                val dy = item.offset.y + item.size.height / 2f - pointer.y
                dx * dx + dy * dy
            }
        val target = over?.key as? String
        if (over == null || target == null || target == dragged) {
            blockedTarget = null
            return
        }
        // Tras reordenar, la tarjeta arrastrada ocupa el hueco del destino: no volver a intentarlo hasta salir de él.
        if (target == blockedTarget) return
        blockedTarget = target
        clampDraggedCard(dragged)
        reorderNow.onOver(dragged, target)
        dragOffset += Offset(
            (draggedInfo.offset.x - over.offset.x).toFloat(),
            (draggedInfo.offset.y - over.offset.y).toFloat()
        )
    }

    LaunchedEffect(dragging) {
        val current = dragging ?: return@LaunchedEffect
        while (true) {
            delay(16)
            val viewport = gridState.layoutInfo.viewportSize.height.toFloat()
            val y = pointerInViewport().y
            val step = when {
                y < EDGE_PX -> -((EDGE_PX - y) / 20f).coerceAtMost(MAX_AUTOSCROLL_STEP_PX)
                y > viewport - EDGE_PX -> ((y - (viewport - EDGE_PX)) / 20f).coerceAtMost(MAX_AUTOSCROLL_STEP_PX)
                else -> 0f
            }
            if (step != 0f) {
                var consumed = 0f
                gridState.scroll(MutatePriority.Default) {
                    consumed = scrollBy(step)
                }
                dragOffset += Offset(0f, consumed)
                clampDraggedCard(current)
            }
            checkTarget(current)
        }
    }

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Adaptive(minSize = minCardWidth),
        contentPadding = PaddingValues(20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
    ) {
        items(groups, key = { it }) { group ->
            val isDragged = dragging == group
            val dragModifier = if (reorder != null) {
                Modifier.pointerInput(group) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { offset ->
                            val item = gridState.layoutInfo.visibleItemsInfo
                                .firstOrNull { it.key == group }
                            if (item != null) {
                                fingerPos = Offset(
                                    item.offset.x + offset.x,
                                    item.offset.y + offset.y
                                )
                                dragOffset = Offset.Zero
                                blockedTarget = null
                                dragging = group
                                currentReorder?.onStart?.invoke()
                            }
                        },
                        onDrag = { change, amount ->
                            change.consume()
                            fingerPos += amount
                            dragOffset += amount
                            clampDraggedCard(group)
                            checkTarget(group)
                        },
                        onDragEnd = {
                            dragging = null
                            dragOffset = Offset.Zero
                            blockedTarget = null
                            currentReorder?.onEnd?.invoke()
                        },
                        onDragCancel = {
                            dragging = null
                            dragOffset = Offset.Zero
                            blockedTarget = null
                            currentReorder?.onCancel?.invoke()
                        }
                    )
                }
            } else {
                Modifier
            }
            FocusableCard(
                onClick = { onSelect(group) },
                modifier = Modifier
                    .fillMaxWidth()
                    .zIndex(if (isDragged) 1f else 0f)
                    .then(
                        if (isDragged) {
                            Modifier.offset {
                                IntOffset(dragOffset.x.roundToInt(), dragOffset.y.roundToInt())
                            }
                        } else {
                            Modifier
                        }
                    )
                    .then(dragModifier)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Primary.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = groupIconFor(group),
                            contentDescription = null,
                            tint = Primary
                        )
                    }
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
                        text = "${counts[group] ?: 0} canales",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
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
