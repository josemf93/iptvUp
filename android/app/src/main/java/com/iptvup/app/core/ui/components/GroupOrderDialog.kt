package com.iptvup.app.core.ui.components

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.iptvup.app.core.ui.theme.TextPrimary
import com.iptvup.app.core.ui.theme.TextSecondary
import kotlin.math.roundToInt

private val ROW_HEIGHT = 56.dp

@Composable
fun GroupOrderDialog(
    groups: List<String>,
    onSave: (List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var ordered by remember(groups) { mutableStateOf(groups) }
    var draggingGroup by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var rowHeightPx by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Orden de los grupos") },
        text = {
            Column {
                Text(
                    text = "Mantén pulsado el asa y arrastra para cambiar el orden.",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                ) {
                    itemsIndexed(ordered, key = { _, group -> group }) { index, group ->
                        val isDragging = draggingGroup == group
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(ROW_HEIGHT)
                                .zIndex(if (isDragging) 1f else 0f)
                                .offset {
                                    IntOffset(0, if (isDragging) dragOffset.roundToInt() else 0)
                                }
                                .onSizeChanged { rowHeightPx = it.height.toFloat() },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = group,
                                color = TextPrimary,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Box(
                                modifier = Modifier
                                    .size(ROW_HEIGHT)
                                    .pointerInput(group) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = {
                                                draggingGroup = group
                                                dragOffset = 0f
                                            },
                                            onDragEnd = {
                                                draggingGroup = null
                                                dragOffset = 0f
                                            },
                                            onDragCancel = {
                                                draggingGroup = null
                                                dragOffset = 0f
                                            },
                                            onDrag = { change, amount ->
                                                change.consume()
                                                dragOffset += amount.y
                                                val current = ordered.indexOf(group)
                                                val threshold = rowHeightPx / 2f
                                                if (dragOffset > threshold &&
                                                    current < ordered.lastIndex
                                                ) {
                                                    ordered = ordered.swapped(current, current + 1)
                                                    dragOffset -= rowHeightPx
                                                } else if (dragOffset < -threshold && current > 0) {
                                                    ordered = ordered.swapped(current, current - 1)
                                                    dragOffset += rowHeightPx
                                                }
                                            }
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.DragHandle,
                                    contentDescription = "Arrastrar $group",
                                    tint = TextSecondary
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(ordered)
                    onDismiss()
                }
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

private fun List<String>.swapped(a: Int, b: Int): List<String> =
    toMutableList().also { list ->
        val item = list[a]
        list[a] = list[b]
        list[b] = item
    }
