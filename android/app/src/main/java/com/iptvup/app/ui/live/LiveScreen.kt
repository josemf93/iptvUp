package com.iptvup.app.ui.live

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iptvup.app.core.ui.components.ChannelLogo
import com.iptvup.app.core.ui.components.EmptyState
import com.iptvup.app.core.ui.components.ErrorBanner
import com.iptvup.app.core.ui.components.GroupVisibilityDialog
import com.iptvup.app.core.ui.components.NowNextText
import com.iptvup.app.core.ui.components.QualityBadge
import com.iptvup.app.core.ui.theme.Accent
import com.iptvup.app.core.ui.theme.Danger
import com.iptvup.app.core.ui.theme.Primary
import com.iptvup.app.core.ui.theme.SurfaceDark
import com.iptvup.app.core.ui.theme.SurfaceElevated
import com.iptvup.app.core.ui.theme.TextPrimary
import com.iptvup.app.core.ui.theme.TextSecondary
import com.iptvup.app.core.ui.util.formatTime
import com.iptvup.app.core.ui.util.progressFraction
import com.iptvup.app.data.repository.HiddenRepository
import kotlinx.coroutines.launch

@Composable
fun LiveScreen(
    onPlay: (String) -> Unit,
    viewModel: LiveViewModel = hiltViewModel()
) {
    val channels by viewModel.channels.collectAsStateWithLifecycle()
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    val allGroups by viewModel.allGroups.collectAsStateWithLifecycle()
    val hidden by viewModel.hidden.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val selectedGroup by viewModel.selectedGroup.collectAsStateWithLifecycle()
    val syncing by viewModel.syncing.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    var showGroupsDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val hiddenGroups = remember(hidden) {
        hidden.filter { it.kind == HiddenRepository.KIND_GROUP }
            .map { it.itemKey }
            .toSet()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "TV en directo",
                            color = TextPrimary,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${channels.size} canales visibles",
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (syncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = Primary,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = status.ifBlank { "Actualizando..." },
                            color = TextSecondary,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else {
                        IconButton(onClick = viewModel::sync) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = "Actualizar lista",
                                tint = TextSecondary
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = viewModel::setQuery,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Buscar canal o grupo") },
                    leadingIcon = {
                        Icon(Icons.Rounded.Search, contentDescription = null)
                    },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setQuery("") }) {
                                Icon(Icons.Rounded.Clear, contentDescription = "Limpiar")
                            }
                        }
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = SurfaceDark,
                        unfocusedContainerColor = SurfaceDark,
                        focusedBorderColor = Primary,
                        unfocusedBorderColor = TextSecondary.copy(alpha = 0.25f),
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = Primary
                    )
                )

                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LazyRow(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            FilterChip(
                                selected = selectedGroup == null,
                                onClick = { viewModel.selectGroup(null) },
                                label = { Text("Todos") }
                            )
                        }
                        items(groups, key = { it }) { group ->
                            FilterChip(
                                selected = selectedGroup == group,
                                onClick = { viewModel.selectGroup(group) },
                                label = {
                                    Text(
                                        text = group,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            )
                        }
                    }
                    IconButton(onClick = { showGroupsDialog = true }) {
                        Icon(
                            imageVector = Icons.Rounded.Tune,
                            contentDescription = "Gestionar grupos",
                            tint = TextSecondary
                        )
                    }
                }

                error?.let { message ->
                    Spacer(Modifier.height(10.dp))
                    ErrorBanner(
                        message = message,
                        onRetry = viewModel::sync
                    )
                }
            }

            HorizontalDivider(color = SurfaceElevated)
            if (syncing) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                    color = Primary,
                    trackColor = SurfaceElevated
                )
            }

            if (channels.isEmpty() && !syncing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyState(
                        title = "No hay canales",
                        message = if (query.isNotBlank() || selectedGroup != null) {
                            "Prueba con otra búsqueda o quita el filtro de grupo."
                        } else {
                            "Actualiza la lista para descargar los canales del perfil."
                        },
                        actionLabel = if (query.isNotBlank() || selectedGroup != null) {
                            "Quitar filtros"
                        } else {
                            "Actualizar lista"
                        },
                        onAction = {
                            if (query.isNotBlank() || selectedGroup != null) {
                                viewModel.setQuery("")
                                viewModel.selectGroup(null)
                            } else {
                                viewModel.sync()
                            }
                        }
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 10.dp,
                        bottom = 24.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(channels, key = { it.channel.id }) { item ->
                        ChannelRow(
                            item = item,
                            onPlay = { onPlay(item.channel.id) },
                            onFavorite = { viewModel.toggleFavorite(item.channel) },
                            onHide = {
                                viewModel.toggleChannelHidden(item.channel)
                                scope.launch {
                                    val result = snackbarHostState.showSnackbar(
                                        message = "Canal oculto: ${item.channel.name}",
                                        actionLabel = "Deshacer",
                                        duration = SnackbarDuration.Short
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        viewModel.toggleChannelHidden(item.channel)
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        )
    }

    if (showGroupsDialog) {
        GroupVisibilityDialog(
            groups = allGroups,
            hiddenGroups = hiddenGroups,
            onToggle = viewModel::toggleGroupHidden,
            onDismiss = { showGroupsDialog = false }
        )
    }
}

@Composable
private fun ChannelRow(
    item: LiveChannelUi,
    onPlay: () -> Unit,
    onFavorite: () -> Unit,
    onHide: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .then(
                if (focused) {
                    Modifier.border(2.dp, Primary, RoundedCornerShape(16.dp))
                } else {
                    Modifier
                }
            )
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceDark)
            .clickable(onClick = onPlay)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ChannelLogo(
            name = item.channel.name,
            logoUrl = item.channel.logoUrl
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
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
                Text(
                    text = item.channel.groupName,
                    color = TextSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            Spacer(Modifier.height(3.dp))
            NowNextText(
                now = item.epg.now?.let {
                    "${formatTime(it.startMs)} · ${it.title}"
                },
                next = item.epg.next?.let {
                    "${formatTime(it.startMs)} · ${it.title}"
                }
            )
            item.epg.now?.let { now ->
                Spacer(Modifier.height(6.dp))
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
                        .height(3.dp),
                    color = Accent,
                    trackColor = SurfaceElevated
                )
            }
        }
        Spacer(Modifier.width(6.dp))
        IconButton(onClick = onFavorite) {
            Icon(
                imageVector = if (item.favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                contentDescription = if (item.favorite) "Quitar de favoritos" else "Añadir a favoritos",
                tint = if (item.favorite) Accent else TextSecondary
            )
        }
        IconButton(onClick = onHide) {
            Icon(
                imageVector = Icons.Rounded.VisibilityOff,
                contentDescription = "Ocultar canal",
                tint = TextSecondary
            )
        }
    }
}
