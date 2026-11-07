package com.iptvup.app.core.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImagePainter
import coil.compose.AsyncImage
import com.iptvup.app.core.ui.theme.Accent
import com.iptvup.app.core.ui.theme.Background
import com.iptvup.app.core.ui.theme.Danger
import com.iptvup.app.core.ui.theme.Primary
import com.iptvup.app.core.ui.theme.PrimarySoft
import com.iptvup.app.core.ui.theme.SurfaceDark
import com.iptvup.app.core.ui.theme.SurfaceElevated
import com.iptvup.app.core.ui.theme.TextPrimary
import com.iptvup.app.core.ui.theme.TextSecondary
import com.iptvup.app.core.ui.util.isTelevision
import com.iptvup.app.data.identity.ChannelQuality

@Composable
fun AppBackground(content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Background,
                        Color(0xFF12121A),
                        Background
                    )
                )
            ),
        content = content
    )
}

@Composable
fun BrandLogo(modifier: Modifier = Modifier, showName: Boolean = true) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(Primary),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
        if (showName) {
            Spacer(Modifier.width(12.dp))
            Text(
                text = "IPTVUp",
                color = TextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun LoadingOverlay(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.78f)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Primary)
            Spacer(Modifier.height(18.dp))
            Text(
                text = message,
                color = TextPrimary,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
fun ErrorBanner(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Danger.copy(alpha = 0.12f))
            .border(1.dp, Danger.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Rounded.ErrorOutline,
            contentDescription = null,
            tint = Danger
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = message,
            color = TextPrimary,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        if (onRetry != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Reintentar",
                color = Primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onRetry)
                    .padding(8.dp)
            )
        }
    }
}

@Composable
fun EmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(SurfaceElevated),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Tv,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(32.dp)
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = title,
            color = TextPrimary,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = message,
            color = TextSecondary,
            style = MaterialTheme.typography.bodyMedium
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(18.dp))
            Button(onClick = onAction) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
fun ChannelLogo(
    name: String,
    logoUrl: String?,
    modifier: Modifier = Modifier
) {
    var loaded by remember(logoUrl) { mutableStateOf(false) }
    Box(
        modifier = modifier
            .size(56.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceElevated),
        contentAlignment = Alignment.Center
    ) {
        if (!loaded) {
            Icon(
                imageVector = Icons.Rounded.Tv,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(26.dp)
            )
        }
        if (!logoUrl.isNullOrBlank()) {
            AsyncImage(
                model = logoUrl,
                contentDescription = name,
                contentScale = ContentScale.Fit,
                onState = { state -> loaded = state is AsyncImagePainter.State.Success },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(6.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FocusableCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Card(
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .then(
                if (focused) {
                    Modifier.border(2.dp, Primary, RoundedCornerShape(18.dp))
                } else {
                    Modifier
                }
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark)
    ) {
        Column(content = content)
    }
}

@Composable
fun QualityBadge(name: String, modifier: Modifier = Modifier) {
    val label = ChannelQuality.label(name) ?: return
    val color = when (label) {
        "8K", "4K" -> Accent
        "FHD" -> Primary
        "HD" -> PrimarySoft
        else -> TextSecondary
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(5.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.4.sp
        )
    }
}

@Composable
fun TypeBadge(kind: String) {
    val label = if (kind.equals("xtream", ignoreCase = true)) "XTREAM" else "M3U"
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Primary.copy(alpha = 0.16f))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            color = Primary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp
        )
    }
}

@Composable
fun TvIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .then(
                if (focused) Modifier.border(2.dp, Primary, CircleShape) else Modifier
            )
            .clickable(
                interactionSource = interactionSource,
                indication = if (isTelevision()) null else LocalIndication.current,
                enabled = enabled,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
fun NowNextText(
    now: String?,
    next: String?,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = now ?: "Sin información de EPG",
            color = if (now != null) TextPrimary else TextSecondary,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (next != null) {
            Text(
                text = "Después: $next",
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
