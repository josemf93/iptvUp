package com.iptvup.app.ui.profiles

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.ChildCare
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Newspaper
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.SportsSoccer
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iptvup.app.core.ui.components.AppBackground
import com.iptvup.app.core.ui.components.BrandLogo
import com.iptvup.app.core.ui.components.EmptyState
import com.iptvup.app.core.ui.components.ErrorBanner
import com.iptvup.app.core.ui.components.FocusableCard
import com.iptvup.app.core.ui.components.TypeBadge
import com.iptvup.app.core.ui.theme.Danger
import com.iptvup.app.core.ui.theme.Primary
import com.iptvup.app.core.ui.theme.SurfaceElevated
import com.iptvup.app.core.ui.theme.TextPrimary
import com.iptvup.app.core.ui.theme.TextSecondary
import com.iptvup.app.core.ui.util.formatLastSync
import com.iptvup.app.data.model.IptvProfile
import com.iptvup.app.ui.settings.GlobalSettingsDialog

private data class ProfileIconOption(
    val key: String,
    val label: String,
    val icon: ImageVector
)

private val profileIconOptions = listOf(
    ProfileIconOption("person", "Persona", Icons.Rounded.Person),
    ProfileIconOption("tv", "TV", Icons.Rounded.LiveTv),
    ProfileIconOption("movie", "Cine", Icons.Rounded.Movie),
    ProfileIconOption("sports", "Deportes", Icons.Rounded.SportsSoccer),
    ProfileIconOption("music", "Música", Icons.Rounded.MusicNote),
    ProfileIconOption("news", "Noticias", Icons.Rounded.Newspaper),
    ProfileIconOption("kids", "Infantil", Icons.Rounded.ChildCare),
    ProfileIconOption("star", "Favoritos", Icons.Rounded.Star),
    ProfileIconOption("games", "Juegos", Icons.Rounded.SportsEsports),
    ProfileIconOption("cast", "Cast", Icons.Rounded.Cast)
)

private fun profileIconFor(key: String?): ImageVector =
    profileIconOptions.firstOrNull { it.key == key }?.icon ?: Icons.Rounded.Person

@Composable
fun ProfilesScreen(
    onOpenProfile: (String) -> Unit,
    onAddProfile: () -> Unit,
    onEditProfile: (String) -> Unit,
    viewModel: ProfilesViewModel = hiltViewModel()
) {
    var profileToDelete by remember { mutableStateOf<IptvProfile?>(null) }
    var iconProfile by remember { mutableStateOf<IptvProfile?>(null) }
    var confirmLogout by remember { mutableStateOf(false) }
    var showGlobalSettings by remember { mutableStateOf(false) }
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val profileIcons by viewModel.profileIcons.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel.navigateTo) {
        viewModel.navigateTo?.let { profileId ->
            viewModel.consumeNavigation()
            onOpenProfile(profileId)
        }
    }

    AppBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                    )
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BrandLogo()
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { showGlobalSettings = true }) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = "Ajustes",
                        tint = TextSecondary
                    )
                }
                IconButton(onClick = { confirmLogout = true }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Logout,
                        contentDescription = "Cerrar sesión",
                        tint = TextSecondary
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                Text(
                    text = "Tus perfiles",
                    color = TextPrimary,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Elige un perfil IPTV para empezar a ver la televisión.",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(18.dp))
                viewModel.error?.let { message ->
                    ErrorBanner(
                        message = message,
                        onRetry = viewModel::refresh
                    )
                    Spacer(Modifier.height(14.dp))
                }
            }

            if (profiles.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyState(
                        title = "Todavía no hay perfiles",
                        message = "Crea un perfil con una lista M3U o con credenciales Xtream Codes.",
                        actionLabel = "Añadir perfil",
                        onAction = onAddProfile
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 300.dp),
                    contentPadding = PaddingValues(24.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    item {
                        FocusableCard(
                            onClick = onAddProfile,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Primary.copy(alpha = 0.18f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Add,
                                        contentDescription = null,
                                        tint = Primary
                                    )
                                }
                                Spacer(Modifier.width(14.dp))
                                Column {
                                    Text(
                                        text = "Añadir perfil",
                                        color = TextPrimary,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "M3U o Xtream Codes",
                                        color = TextSecondary,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }

                    items(profiles, key = { it.id }) { profile ->
                        ProfileCard(
                            profile = profile,
                            icon = profileIconFor(profileIcons[profile.id]),
                            onOpen = { viewModel.open(profile) },
                            onPickIcon = { iconProfile = profile },
                            onEdit = { onEditProfile(profile.id) },
                            onDelete = { profileToDelete = profile }
                        )
                    }
                }
            }
        }
    }

    iconProfile?.let { profile ->
        val selectedKey = profileIcons[profile.id] ?: "person"
        AlertDialog(
            onDismissRequest = { iconProfile = null },
            title = { Text("Icono del perfil") },
            text = {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    modifier = Modifier.height(190.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(profileIconOptions, key = { it.key }) { option ->
                        val selected = option.key == selectedKey
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    if (selected) {
                                        Primary.copy(alpha = 0.22f)
                                    } else {
                                        SurfaceElevated
                                    }
                                )
                                .then(
                                    if (selected) {
                                        Modifier.border(
                                            2.dp,
                                            Primary,
                                            RoundedCornerShape(16.dp)
                                        )
                                    } else {
                                        Modifier
                                    }
                                )
                                .clickable {
                                    viewModel.setProfileIcon(profile.id, option.key)
                                    iconProfile = null
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = option.icon,
                                contentDescription = option.label,
                                tint = if (selected) Primary else TextSecondary
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { iconProfile = null }) {
                    Text("Cerrar")
                }
            }
        )
    }

    if (showGlobalSettings) {
        GlobalSettingsDialog(onDismiss = { showGlobalSettings = false })
    }

    profileToDelete?.let { profile ->
        AlertDialog(
            onDismissRequest = { profileToDelete = null },
            title = { Text("Borrar perfil") },
            text = {
                Text(
                    "Se borrará \"${profile.name}\" de la nube y de este dispositivo. " +
                        "La lista y el EPG se volverán a descargar si vuelves a crearlo."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.delete(profile)
                        profileToDelete = null
                    }
                ) {
                    Text("Borrar", color = Danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { profileToDelete = null }) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("Cerrar sesión") },
            text = { Text("¿Quieres salir de tu cuenta? Los perfiles seguirán guardados en la nube.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmLogout = false
                        viewModel.logout()
                    }
                ) {
                    Text("Salir")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmLogout = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
private fun ProfileCard(
    profile: IptvProfile,
    icon: ImageVector,
    onOpen: () -> Unit,
    onPickIcon: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    FocusableCard(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(SurfaceElevated)
                        .clickable(onClick = onPickIcon),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = "Cambiar icono del perfil",
                        tint = Primary
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = profile.name,
                        color = TextPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    TypeBadge(profile.kind.wire)
                }
                IconButton(onClick = onEdit) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = "Editar perfil",
                        tint = TextSecondary
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = "Borrar perfil",
                        tint = TextSecondary
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = "Última sincronización: ${formatLastSync(profile.lastSyncedAt)}",
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
