package com.iptvup.app.ui.epg

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.iptvup.app.core.ui.components.ChannelLogo
import com.iptvup.app.core.ui.components.EmptyState
import com.iptvup.app.core.ui.components.ErrorBanner
import com.iptvup.app.core.ui.components.GroupGrid
import com.iptvup.app.core.ui.components.GroupVisibilityDialog
import com.iptvup.app.core.ui.components.NowNextText
import com.iptvup.app.core.ui.components.QualityBadge
import com.iptvup.app.core.ui.components.SearchDialog
import com.iptvup.app.core.ui.components.SyncOptionsDialog
import com.iptvup.app.core.ui.theme.Accent
import com.iptvup.app.core.ui.theme.Primary
import com.iptvup.app.core.ui.theme.SurfaceDark
import com.iptvup.app.core.ui.theme.SurfaceElevated
import com.iptvup.app.core.ui.theme.TextPrimary
import com.iptvup.app.core.ui.theme.TextSecondary
import com.iptvup.app.core.ui.util.formatDay
import com.iptvup.app.core.ui.util.formatTime
import com.iptvup.app.core.ui.util.isTelevision
import com.iptvup.app.core.ui.util.progressFraction
import kotlinx.coroutines.delay

@Composable
fun EpgScreen(
    onPlay: (String) -> Unit,
    viewModel: EpgViewModel = hiltViewModel()
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    val allGroups by viewModel.allGroups.collectAsStateWithLifecycle()
    val hiddenGroupKeys by viewModel.hiddenGroupKeys.collectAsStateWithLifecycle()
    val groupCounts by viewModel.groupCounts.collectAsStateWithLifecycle()
    val selectedGroup by viewModel.selectedGroup.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val syncing by viewModel.syncing.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val television = isTelevision()
    var showGroupsDialog by remember { mutableStateOf(false) }
    var showSearchDialog by remember { mutableStateOf(false) }
    var showSyncDialog by remember { mutableStateOf(false) }

    val visibleGroups = remember(groups, query) {
        if (query.isBlank()) {
            groups
        } else {
            groups.filter { it.contains(query, ignoreCase = true) }
        }
    }
    val showingGroupBrowser = selectedGroup == null

    BackHandler(enabled = selectedGroup != null) {
        viewModel.setQuery("")
        viewModel.selectGroup(null)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Guía EPG",
                        color = TextPrimary,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (showingGroupBrowser) {
                            "${groups.size} grupos · ${ui.channels.size} canales con EPG"
                        } else {
                            "${ui.channels.size} canales con EPG"
                        },
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (television) {
                    IconButton(onClick = { showSearchDialog = true }) {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = "Buscar",
                            tint = if (query.isNotBlank()) Primary else TextSecondary
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
                    IconButton(onClick = { showSyncDialog = true }) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Actualizar guía",
                            tint = TextSecondary
                        )
                    }
                }
            }

            if (!television) {
                Spacer(Modifier.height(10.dp))
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
            }
            error?.let { message ->
                Spacer(Modifier.height(8.dp))
                ErrorBanner(message = message, onRetry = viewModel::sync)
            }

            if (selectedGroup != null) {
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
                }
            }
        }

        HorizontalDivider(color = SurfaceElevated)

        if (showingGroupBrowser) {
            if (visibleGroups.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyState(
                        title = if (query.isBlank()) "No hay grupos" else "Sin resultados",
                        message = if (query.isBlank()) {
                            "Actualiza el perfil para descargar los canales y su EPG."
                        } else {
                            "Prueba con otro nombre de grupo."
                        },
                        actionLabel = if (query.isBlank()) "Actualizar EPG" else "Quitar búsqueda",
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
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    GroupGrid(
                        groups = visibleGroups,
                        counts = groupCounts,
                        minCardWidth = if (television) 220.dp else 150.dp,
                        onSelect = { group ->
                            viewModel.setQuery("")
                            viewModel.selectGroup(group)
                        }
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (ui.channels.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        EmptyState(
                            title = if (query.isNotBlank()) "Sin resultados" else "Sin EPG en este grupo",
                            message = if (query.isNotBlank()) {
                                "Prueba con otro canal o quita la búsqueda."
                            } else {
                                "No hay programas de EPG para los canales visibles de este grupo."
                            },
                            actionLabel = if (query.isNotBlank()) "Quitar búsqueda" else "Volver a grupos",
                            onAction = {
                                if (query.isNotBlank()) {
                                    viewModel.setQuery("")
                                } else {
                                    viewModel.selectGroup(null)
                                }
                            }
                        )
                    }
                } else if (television) {
                    EpgTvList(ui = ui, onPlay = onPlay)
                } else {
                    EpgTimeline(ui = ui, onPlay = onPlay)
                }
            }
        }
    }

    if (showGroupsDialog) {
        GroupVisibilityDialog(
            groups = allGroups,
            hiddenGroups = hiddenGroupKeys,
            onToggle = viewModel::toggleGroupHidden,
            onShowAll = viewModel::showAllGroups,
            onHideAll = viewModel::hideAllGroups,
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

    if (showSyncDialog) {
        SyncOptionsDialog(
            onSyncFiltered = {
                showSyncDialog = false
                viewModel.sync()
            },
            onSyncAll = {
                showSyncDialog = false
                viewModel.syncAll()
            },
            onDismiss = { showSyncDialog = false }
        )
    }
}

@Composable
private fun EpgTvList(
    ui: EpgUi,
    onPlay: (String) -> Unit
) {
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
        items(ui.channels, key = { it.channel.id }) { item ->
            EpgTvRow(item = item, onPlay = { onPlay(item.channel.id) })
        }
    }
}

@Composable
private fun EpgTvRow(
    item: EpgChannelUi,
    onPlay: () -> Unit
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
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ChannelLogo(name = item.channel.name, logoUrl = item.channel.logoUrl)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.channel.name,
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
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
            Spacer(Modifier.height(4.dp))
            NowNextText(
                now = item.now?.let { "${formatTime(it.startMs)} · ${it.title}" },
                next = item.next?.let { "${formatTime(it.startMs)} · ${it.title}" }
            )
            item.now?.let { now ->
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = {
                        progressFraction(System.currentTimeMillis(), now.startMs, now.endMs)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                    color = Accent,
                    trackColor = SurfaceElevated
                )
            }
        }
    }
}

@Composable
private fun EpgTimeline(
    ui: EpgUi,
    onPlay: (String) -> Unit
) {
    val hourWidth = 180.dp
    val hours = 6
    val totalWidth = hourWidth * hours.toFloat()
    val channelWidth = 150.dp
    val scrollState = rememberScrollState()
    var now by remember { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(60_000L)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(channelWidth)
                    .padding(horizontal = 12.dp)
            ) {
                Text(
                    text = "Canal",
                    color = TextSecondary,
                    style = MaterialTheme.typography.labelMedium
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(scrollState)
            ) {
                Box(
                    modifier = Modifier
                        .width(totalWidth)
                        .fillMaxHeight()
                ) {
                    for (hour in 0..hours) {
                        Text(
                            text = formatTime(ui.windowStart + hour * 3_600_000L),
                            color = TextSecondary,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier
                                .offset(x = hourWidth * hour.toFloat())
                                .padding(start = 8.dp, top = 12.dp)
                        )
                    }
                }
            }
        }
        HorizontalDivider(color = SurfaceElevated)

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(ui.channels, key = { it.channel.id }) { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(84.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .width(channelWidth)
                            .fillMaxHeight()
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ChannelLogo(
                            name = item.channel.name,
                            logoUrl = item.channel.logoUrl,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.channel.name,
                                color = TextPrimary,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            QualityBadge(item.channel.name)
                        }
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .horizontalScroll(scrollState)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(totalWidth)
                                .fillMaxHeight()
                                .background(SurfaceDark)
                        ) {
                            if (item.programs.isEmpty()) {
                                Text(
                                    text = "Sin información de EPG",
                                    color = TextSecondary,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                            item.programs.forEach { program ->
                                val start = maxOf(program.startMs, ui.windowStart)
                                val end = minOf(program.endMs, ui.windowEnd)
                                if (end > start) {
                                    val offset = hourWidth * (
                                        (start - ui.windowStart).toFloat() / 3_600_000f
                                        )
                                    val width = (
                                        hourWidth * ((end - start).toFloat() / 3_600_000f)
                                        ).coerceAtLeast(22.dp)
                                    val isCurrent = program.startMs <= now && program.endMs > now
                                    Box(
                                        modifier = Modifier
                                            .offset(x = offset)
                                            .width(width)
                                            .fillMaxHeight()
                                            .padding(2.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (isCurrent) {
                                                    Accent.copy(alpha = 0.16f)
                                                } else {
                                                    SurfaceElevated
                                                }
                                            )
                                            .clickable { onPlay(item.channel.id) }
                                            .padding(horizontal = 6.dp, vertical = 6.dp)
                                    ) {
                                        Column {
                                            Text(
                                                text = program.title,
                                                color = TextPrimary,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(Modifier.height(2.dp))
                                            Text(
                                                text = "${formatTime(start)} - ${formatTime(end)}",
                                                color = TextSecondary,
                                                style = MaterialTheme.typography.labelSmall,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                            val nowOffset = hourWidth * (
                                ((now - ui.windowStart).coerceAtLeast(0).toFloat()) /
                                    3_600_000f
                                )
                            Box(
                                modifier = Modifier
                                    .offset(x = nowOffset)
                                    .width(2.dp)
                                    .fillMaxHeight()
                                    .background(Accent)
                            )
                        }
                    }
                }
                HorizontalDivider(color = SurfaceElevated.copy(alpha = 0.6f))
            }
        }
    }
}
