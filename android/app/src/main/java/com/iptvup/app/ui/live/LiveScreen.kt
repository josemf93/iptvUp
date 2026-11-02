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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChildCare
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Newspaper
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.SportsSoccer
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iptvup.app.core.ui.components.ChannelLogo
import com.iptvup.app.core.ui.components.EmptyState
import com.iptvup.app.core.ui.components.ErrorBanner
import com.iptvup.app.core.ui.components.FocusableCard
import com.iptvup.app.core.ui.components.GroupVisibilityDialog
import com.iptvup.app.core.ui.components.NowNextText
import com.iptvup.app.core.ui.components.QualityBadge
import com.iptvup.app.core.ui.theme.Accent
import com.iptvup.app.core.ui.theme.Primary
import com.iptvup.app.core.ui.theme.SurfaceDark
import com.iptvup.app.core.ui.theme.SurfaceElevated
import com.iptvup.app.core.ui.theme.TextPrimary
import com.iptvup.app.core.ui.theme.TextSecondary
import com.iptvup.app.core.ui.util.formatTime
import com.iptvup.app.core.ui.util.isTelevision
import com.iptvup.app.core.ui.util.progressFraction
import com.iptvup.app.data.repository.HiddenRepository

@Composable
fun LiveScreen(
    onPlay: (String) -> Unit,
    viewModel: LiveViewModel = hiltViewModel()
) {
    val channels by viewModel.channels.collectAsStateWithLifecycle()
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    val allGroups by viewModel.allGroups.collectAsStateWithLifecycle()
    val groupCounts by viewModel.groupCounts.collectAsStateWithLifecycle()
    val hidden by viewModel.hidden.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val selectedGroup by viewModel.selectedGroup.collectAsStateWithLifecycle()
    val syncing by viewModel.syncing.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    var showGroupsDialog by remember { mutableStateOf(false) }
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

    BackHandler(enabled = isTv && selectedGroup != null) {
        viewModel.setQuery("")
        viewModel.selectGroup(null)
    }

    val showingGroupBrowser = isTv && selectedGroup == null
    val filtersActive = query.isNotBlank() ||
        (!isTv && selectedGroup != null) ||
        (isTv && selectedGroup != null)

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
                            text = if (showingGroupBrowser) {
                                "${groups.size} grupos · ${channels.size} canales"
                            } else {
                                "${channels.size} canales visibles"
                            },
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

                error?.let { message ->
                    Spacer(Modifier.height(10.dp))
                    ErrorBanner(
                        message = message,
                        onRetry = viewModel::sync
                    )
                }

                if (isTv && selectedGroup != null) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
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
                        IconButton(onClick = { showGroupsDialog = true }) {
                            Icon(
                                imageVector = Icons.Rounded.Tune,
                                contentDescription = "Gestionar grupos",
                                tint = TextSecondary
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                ChannelSearchField(
                    query = query,
                    onQueryChange = viewModel::setQuery
                )

                if (isTv && selectedGroup == null) {
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Grupos",
                            color = TextPrimary,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { showGroupsDialog = true }) {
                            Icon(
                                imageVector = Icons.Rounded.Tune,
                                contentDescription = "Gestionar grupos",
                                tint = TextSecondary
                            )
                        }
                    }
                }

                if (!isTv) {
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

            if (showingGroupBrowser) {
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
                    TvGroupGrid(
                        groups = visibleGroups,
                        counts = groupCounts,
                        onSelect = { group ->
                            viewModel.setQuery("")
                            viewModel.selectGroup(group)
                        }
                    )
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
                            isTv && selectedGroup != null -> "Volver a grupos"
                            filtersActive -> "Quitar filtros"
                            else -> "Actualizar lista"
                        },
                        onAction = {
                            when {
                                query.isNotBlank() -> viewModel.setQuery("")
                                isTv && selectedGroup != null -> viewModel.selectGroup(null)
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
            onToggle = viewModel::toggleGroupHidden,
            onShowAll = viewModel::showAllGroups,
            onHideAll = viewModel::hideAllGroups,
            onDismiss = { showGroupsDialog = false }
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
        placeholder = { Text("Buscar canal o grupo") },
        leadingIcon = {
            Icon(Icons.Rounded.Search, contentDescription = null)
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
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
private fun TvGroupGrid(
    groups: List<String>,
    counts: Map<String, Int>,
    onSelect: (String) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 220.dp),
        contentPadding = PaddingValues(20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        gridItems(groups, key = { it }) { group ->
            FocusableCard(
                onClick = { onSelect(group) },
                modifier = Modifier.fillMaxWidth()
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
