package com.iptvup.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.iptvup.app.core.ui.components.BrandLogo
import com.iptvup.app.core.ui.components.FocusableCard
import com.iptvup.app.core.ui.components.QualityBadge
import com.iptvup.app.core.ui.theme.Accent
import com.iptvup.app.core.ui.theme.Primary
import com.iptvup.app.core.ui.theme.SurfaceDark
import com.iptvup.app.core.ui.theme.SurfaceElevated
import com.iptvup.app.core.ui.theme.TextPrimary
import com.iptvup.app.core.ui.theme.TextSecondary
import com.iptvup.app.core.ui.util.isTelevision
import com.iptvup.app.data.local.RecentChannelEntity
import com.iptvup.app.data.model.IptvProfile

@Composable
fun HomeScreen(
    profile: IptvProfile?,
    onPlay: (String) -> Unit,
    onGoLive: () -> Unit,
    onGoEpg: () -> Unit,
    onGoFavorites: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val channelCount by viewModel.channelCount.collectAsStateWithLifecycle()
    val favoriteCount by viewModel.favoriteCount.collectAsStateWithLifecycle()
    val recent by viewModel.recent.collectAsStateWithLifecycle()
    val television = isTelevision()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Text(
            text = "Inicio",
            color = TextPrimary,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color(0xFF42209A),
                            Primary.copy(alpha = 0.85f),
                            Color(0xFF1A1230)
                        )
                    )
                )
                .padding(24.dp)
        ) {
            Column {
                BrandLogo(showName = false)
                Spacer(Modifier.height(18.dp))
                Text(
                    text = "Tu televisión,\na tu manera",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = profile?.name ?: "Perfil IPTV",
                    color = Color.White.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = onGoLive) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Ver TV")
                    }
                    OutlinedButton(onClick = onGoEpg) {
                        Icon(Icons.Rounded.CalendarMonth, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Guía EPG")
                    }
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(
                value = channelCount.toString(),
                label = "Canales",
                modifier = Modifier.weight(1f)
            )
            StatCard(
                value = favoriteCount.toString(),
                label = "Favoritos",
                modifier = Modifier.weight(1f)
            )
        }

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

        Spacer(Modifier.height(20.dp))
        Text(
            text = "Accesos rápidos",
            color = TextPrimary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(10.dp))

        if (television) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                QuickCard(
                    title = "TV en directo",
                    subtitle = "$channelCount canales",
                    icon = { Icon(Icons.Rounded.LiveTv, contentDescription = null, tint = Primary) },
                    onClick = onGoLive,
                    modifier = Modifier.weight(1f)
                )
                QuickCard(
                    title = "Guía EPG",
                    subtitle = "Ahora y después",
                    icon = { Icon(Icons.Rounded.CalendarMonth, contentDescription = null, tint = Accent) },
                    onClick = onGoEpg,
                    modifier = Modifier.weight(1f)
                )
                QuickCard(
                    title = "Favoritos",
                    subtitle = "$favoriteCount guardados",
                    icon = { Icon(Icons.Rounded.Star, contentDescription = null, tint = Accent) },
                    onClick = onGoFavorites,
                    modifier = Modifier.weight(1f)
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickCard(
                    title = "TV en directo",
                    subtitle = "$channelCount canales disponibles",
                    icon = { Icon(Icons.Rounded.LiveTv, contentDescription = null, tint = Primary) },
                    onClick = onGoLive,
                    modifier = Modifier.fillMaxWidth()
                )
                QuickCard(
                    title = "Guía EPG",
                    subtitle = "Consulta la programación",
                    icon = { Icon(Icons.Rounded.CalendarMonth, contentDescription = null, tint = Accent) },
                    onClick = onGoEpg,
                    modifier = Modifier.fillMaxWidth()
                )
                QuickCard(
                    title = "Favoritos",
                    subtitle = "$favoriteCount canales guardados",
                    icon = { Icon(Icons.Rounded.Star, contentDescription = null, tint = Accent) },
                    onClick = onGoFavorites,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        Spacer(Modifier.height(24.dp))
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

@Composable
private fun StatCard(
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceDark)
            .padding(16.dp)
    ) {
        Text(
            text = value,
            color = TextPrimary,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            color = TextSecondary,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun QuickCard(
    title: String,
    subtitle: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FocusableCard(onClick = onClick, modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceElevated),
                contentAlignment = Alignment.Center
            ) {
                icon()
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
