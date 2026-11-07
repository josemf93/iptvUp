package com.iptvup.app.ui.home

import androidx.compose.foundation.background
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SportsBasketball
import androidx.compose.material.icons.rounded.SportsSoccer
import androidx.compose.material.icons.rounded.SportsTennis
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.border
import androidx.compose.runtime.key
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.clickable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.iptvup.app.core.ui.components.FocusableCard
import com.iptvup.app.core.ui.components.QualityBadge
import com.iptvup.app.core.ui.components.TvIconButton
import com.iptvup.app.core.ui.theme.Accent
import com.iptvup.app.core.ui.theme.Primary
import com.iptvup.app.core.ui.theme.TextPrimary
import com.iptvup.app.core.ui.theme.TextSecondary
import com.iptvup.app.core.ui.util.formatTime
import com.iptvup.app.core.ui.util.isTelevision
import com.iptvup.app.data.local.RecentChannelEntity
import com.iptvup.app.data.model.IptvProfile
import com.iptvup.app.data.sports.SportsEventRaw
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    profile: IptvProfile?,
    onPlay: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val recent by viewModel.recent.collectAsStateWithLifecycle()
    val sportsEvents by viewModel.sportsEvents.collectAsStateWithLifecycle()
    val sportsLoading by viewModel.sportsLoading.collectAsStateWithLifecycle()
    val syncing by viewModel.syncing.collectAsStateWithLifecycle()
    var confirmRefresh by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            viewModel.loadSports()
            delay(15 * 60 * 1000L)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Inicio",
                    color = TextPrimary,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = profile?.name ?: "Perfil IPTV",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
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
                        contentDescription = "Actualizar canales y EPG",
                        tint = TextSecondary
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        SportsCard(
            events = sportsEvents,
            loading = sportsLoading
        )

        if (recent.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            Text(
                text = "Últimos canales",
                color = TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                recent.forEach { item ->
                    RecentChannelRow(
                        item = item,
                        onClick = { onPlay(item.id) }
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
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
private fun SportsCard(
    events: List<SportsEventRaw>,
    loading: Boolean
) {
    val isTv = isTelevision()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(0xFF173A2A),
                        Color(0xFF0F5C3A),
                        Color(0xFF161022)
                    )
                )
            )
            .padding(18.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SportsSoccer,
                        contentDescription = null,
                        tint = Color.White
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Eventos deportivos",
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Hoy · fútbol, baloncesto y tenis",
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            var selectedSport by rememberSaveable { mutableStateOf(SPORT_TAGS.first().sport) }
            SportTags(selected = selectedSport, onSelect = { selectedSport = it })
            Spacer(Modifier.height(14.dp))
            val sportEvents = events.filter { it.sport == selectedSport }

            when {
                loading && events.isEmpty() -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "Buscando eventos...",
                        color = Color.White.copy(alpha = 0.75f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                sportEvents.isEmpty() -> Text(
                    text = "No hay partidos de ${selectedSport.lowercase()} para hoy.",
                    color = Color.White.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.bodySmall
                )

                else -> key(selectedSport) {
                    SportsCarousel(events = sportEvents, isTv = isTv)
                }
            }
        }
    }
}

private data class SportTag(val sport: String, val icon: ImageVector)

private val SPORT_TAGS = listOf(
    SportTag("Fútbol", Icons.Rounded.SportsSoccer),
    SportTag("Tenis", Icons.Rounded.SportsTennis),
    SportTag("Baloncesto", Icons.Rounded.SportsBasketball)
)

@Composable
private fun SportTags(
    selected: String,
    onSelect: (String) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SPORT_TAGS.forEach { tag ->
            SportTagChip(
                label = tag.sport,
                icon = tag.icon,
                selected = tag.sport == selected,
                onClick = { onSelect(tag.sport) }
            )
        }
    }
}

@Composable
private fun SportTagChip(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val shape = RoundedCornerShape(50)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(if (selected) Color.White.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.08f))
            .then(if (focused) Modifier.border(2.dp, Primary, shape) else Modifier)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
private fun SportsCarousel(
    events: List<SportsEventRaw>,
    isTv: Boolean
) {
    val pagerState = rememberPagerState(pageCount = { events.size })
    val scope = rememberCoroutineScope()
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isTv) {
                TvIconButton(
                    onClick = {
                        if (pagerState.currentPage > 0) {
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                        contentDescription = "Evento anterior",
                        tint = Color.White
                    )
                }
            }
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { page ->
                SportsEventPage(event = events[page])
            }
            if (isTv) {
                TvIconButton(
                    onClick = {
                        if (pagerState.currentPage < events.size - 1) {
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = "Evento siguiente",
                        tint = Color.White
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            events.indices.forEach { index ->
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .size(if (index == pagerState.currentPage) 8.dp else 6.dp)
                        .clip(CircleShape)
                        .background(
                            Color.White.copy(
                                alpha = if (index == pagerState.currentPage) 0.9f else 0.35f
                            )
                        )
                )
            }
        }
    }
}

@Composable
private fun SportsEventPage(event: SportsEventRaw) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = formatTime(event.startMs),
                color = Accent,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = " · ${event.sport} · ${event.competition}",
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (event.status == "in") {
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "EN DIRECTO",
                    color = Accent,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        TeamLine(name = event.homeTeam, logo = event.homeLogo)
        Spacer(Modifier.height(4.dp))
        TeamLine(name = event.awayTeam, logo = event.awayLogo)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.Tv,
                contentDescription = null,
                tint = Primary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = if (event.broadcasters.isEmpty()) {
                    "Canal no indicado"
                } else {
                    event.broadcasters.joinToString(" · ")
                },
                color = Primary,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun TeamLine(name: String, logo: String?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (logo != null) {
            AsyncImage(
                model = logo,
                contentDescription = null,
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.9f))
            )
        } else {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = name.take(1).uppercase(),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = name,
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun RecentChannelRow(
    item: RecentChannelEntity,
    onClick: () -> Unit
) {
    FocusableCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            com.iptvup.app.core.ui.components.ChannelLogo(
                name = item.name,
                logoUrl = item.logoUrl
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.groupName,
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.width(6.dp))
                    QualityBadge(item.name)
                }
            }
            Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = "Reproducir",
                tint = Primary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
