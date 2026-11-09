package com.iptvup.app.ui.live

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iptvup.app.core.ui.components.TvIconButton
import com.iptvup.app.core.ui.components.ChannelLogo
import com.iptvup.app.core.ui.components.TvIconButton
import com.iptvup.app.core.ui.components.EmptyState
import com.iptvup.app.core.ui.components.TvIconButton
import com.iptvup.app.core.ui.components.ErrorBanner
import com.iptvup.app.core.ui.components.TvIconButton
import com.iptvup.app.core.ui.components.GroupGrid
import com.iptvup.app.core.ui.components.TvIconButton
import com.iptvup.app.core.ui.components.GroupReorder
import com.iptvup.app.core.ui.components.TvIconButton
import com.iptvup.app.core.ui.components.GroupVisibilityDialog
import com.iptvup.app.core.ui.components.TvIconButton
import com.iptvup.app.core.ui.components.NowNextText
import com.iptvup.app.core.ui.components.TvIconButton
import com.iptvup.app.core.ui.components.QualityBadge
import com.iptvup.app.core.ui.components.TvIconButton
import com.iptvup.app.core.ui.components.SearchDialog
import com.iptvup.app.core.ui.components.TvIconButton
import com.iptvup.app.core.ui.theme.Accent
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
import com.iptvup.app.core.ui.util.formatTime
import com.iptvup.app.core.ui.components.TvIconButton
import com.iptvup.app.core.ui.util.isTelevision
import com.iptvup.app.core.ui.components.TvIconButton
import com.iptvup.app.core.ui.util.progressFraction
import com.iptvup.app.core.ui.components.TvIconButton
import com.iptvup.app.data.repository.HiddenRepository

@Composable
fun LiveScreen(
    onPlay: (String) -> Unit,
    viewModel: LiveViewModel = hiltViewModel()
) {
    if (isTelevision()) {
        LiveTvLayout(onPlay = onPlay, viewModel = viewModel)
        return
    }
    val channelsState by viewModel.channels.collectAsStateWithLifecycle()
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    val allGroups by viewModel.allGroups.collectAsStateWithLifecycle()
    val groupCounts by viewModel.groupCounts.collectAsStateWithLifecycle()
    val hidden by viewModel.hidden.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val selectedGroup by viewModel.selectedGroup.collectAsStateWithLifecycle()
    val channels = channelsState.items
    val channelsLoaded = channelsState.group == selectedGroup
    val syncing by viewModel.syncing.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val ready by viewModel.ready.collectAsStateWithLifecycle()

    var showGroupsDialog by remember { mutableStateOf(false) }
    var showSearchDialog by remember { mutableStateOf(false) }
    var confirmRefresh by remember { mutableStateOf(false) }
    val isTv = isTelevision()

    val hiddenGroups = remember(hidden) {
        hidden.filter { it.kind == HiddenRepository.KIND_GROUP }
            .map { it.itemKey }
            .toSet()
    }
    val visibleGroups = remember(groups, query) {
        if (query.isBlank()) {
            groups
        } else {
            groups.filter { it.contains(query, ignoreCase = true) }
        }
    }

    BackHandler(enabled = selectedGroup != null) {
        viewModel.setQuery("")
        viewModel.selectGroup(null)
    }

    val showingGroupBrowser = selectedGroup == null
    val filtersActive = query.isNotBlank() || selectedGroup != null

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
                        TvIconButton(onClick = { confirmRefresh = true }) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = "Actualizar lista",
                                tint = TextSecondary
                            )
                        }
                    }
                }

                error?.let { message ->
                    Spacer(Modifier.height(10.dp))
                    ErrorBanner(
                        message = message,
                        onRetry = viewModel::sync
                    )
                }

                if (selectedGroup != null) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TvIconButton(
                            onClick = {
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
                        Text(
                            text = selectedGroup.orEmpty(),
                            color = TextPrimary,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (isTv) {
                            TvIconButton(onClick = { showSearchDialog = true }) {
                                Icon(
                                    imageVector = Icons.Rounded.Search,
                                    contentDescription = "Buscar",
                                    tint = if (query.isNotBlank()) Primary else TextSecondary
                                )
                            }
                        }
                        TvIconButton(onClick = { showGroupsDialog = true }) {
                            Icon(
                                imageVector = Icons.Rounded.Visibility,
                                contentDescription = "Gestionar grupos",
                                tint = TextSecondary
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                if (!isTv) {
                    ChannelSearchField(
                        query = query,
                        onQueryChange = viewModel::setQuery
                    )
                }

                if (selectedGroup == null) {
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Grupos",
                            color = TextPrimary,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        if (isTv) {
                            TvIconButton(onClick = { showSearchDialog = true }) {
                                Icon(
                                    imageVector = Icons.Rounded.Search,
                                    contentDescription = "Buscar",
                                    tint = if (query.isNotBlank()) Primary else TextSecondary
                                )
                            }
                        }
                        TvIconButton(onClick = { showGroupsDialog = true }) {
                            Icon(
                                imageVector = Icons.Rounded.Visibility,
                                contentDescription = "Gestionar grupos",
                                tint = TextSecondary
                            )
                        }
                    }
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

            if (!ready) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Primary)
                }
            } else if (showingGroupBrowser) {
                if (visibleGroups.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        EmptyState(
                            title = if (query.isBlank()) "No hay grupos" else "Sin resultados",
                            message = if (query.isBlank()) {
                                "Actualiza la lista para descargar los canales del perfil."
                            } else {
                                "Prueba con otro nombre de grupo."
                            },
                            actionLabel = if (query.isBlank()) {
                                "Actualizar lista"
                            } else {
                                "Quitar búsqueda"
                            },
                            onAction = {
                                if (query.isBlank()) {
                                    viewModel.sync()
                                } else {
                                    viewModel.setQuery("")
                                }
                            }
                        )
                    }
                } else {
                    GroupGrid(
                        groups = visibleGroups,
                        counts = groupCounts,
                        minCardWidth = if (isTv) 220.dp else 150.dp,
                        onSelect = { group ->
                            viewModel.setQuery("")
                            viewModel.selectGroup(group)
                        },
                        reorder = GroupReorder(
                            onStart = viewModel::beginGroupMove,
                            onOver = viewModel::moveGroupTo,
                            onEnd = viewModel::endGroupMove,
                            onCancel = viewModel::cancelGroupMove
                        )
                    )
                }
            } else if (!channelsLoaded) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Primary)
                }
            } else if (channels.isEmpty() && !syncing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyState(
                        title = if (filtersActive) "Sin resultados" else "No hay canales",
                        message = if (filtersActive) {
                            "Prueba con otra búsqueda o cambia el filtro de grupo."
                        } else {
                            "Actualiza la lista para descargar los canales del perfil."
                        },
                        actionLabel = when {
                            query.isNotBlank() -> "Quitar búsqueda"
                            selectedGroup != null -> "Volver a grupos"
                            filtersActive -> "Quitar filtros"
                            else -> "Actualizar lista"
                        },
                        onAction = {
                            when {
                                query.isNotBlank() -> viewModel.setQuery("")
                                selectedGroup != null -> viewModel.selectGroup(null)
                                filtersActive -> {
                                    viewModel.setQuery("")
                                    viewModel.selectGroup(null)
                                }
                                else -> viewModel.sync()
                            }
                        }
                    )
                }
            } else {
                ChannelList(
                    channels = channels,
                    onPlay = onPlay,
                    onFavorite = { viewModel.toggleFavorite(it.channel) }
                )
            }
        }
    }

    if (showGroupsDialog) {
        GroupVisibilityDialog(
            groups = allGroups,
            hiddenGroups = hiddenGroups,
            onSave = { hidden ->
                viewModel.saveHiddenGroups(hidden)
                showGroupsDialog = false
            },
            onDismiss = { showGroupsDialog = false }
        )
    }

    if (showSearchDialog) {
        SearchDialog(
            initialQuery = query,
            onQueryChange = viewModel::setQuery,
            onDismiss = { showSearchDialog = false }
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

@Composable
private fun ChannelSearchField(
    query: String,
    onQueryChange: (String) -> Unit
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        placeholder = { Text("Buscar canal o grupo") },
        leadingIcon = {
            Icon(Icons.Rounded.Search, contentDescription = null)
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                TvIconButton(onClick = { onQueryChange("") }) {
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
}

@Composable
private fun ChannelList(
    channels: List<LiveChannelUi>,
    onPlay: (String) -> Unit,
    onFavorite: (LiveChannelUi) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
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
                onFavorite = { onFavorite(item) }
            )
        }
    }
}

@Composable
private fun ChannelRow(
    item: LiveChannelUi,
    onPlay: () -> Unit,
    onFavorite: () -> Unit
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
        TvIconButton(onClick = onFavorite) {
            Icon(
                imageVector = if (item.favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                contentDescription = if (item.favorite) "Quitar de favoritos" else "Añadir a favoritos",
                tint = if (item.favorite) Accent else TextSecondary
            )
        }
    }
}
