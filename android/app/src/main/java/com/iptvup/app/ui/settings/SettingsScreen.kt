package com.iptvup.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iptvup.app.BuildConfig
import com.iptvup.app.core.ui.components.ErrorBanner
import com.iptvup.app.core.ui.components.TypeBadge
import com.iptvup.app.core.ui.theme.Danger
import com.iptvup.app.core.ui.theme.Primary
import com.iptvup.app.core.ui.theme.SurfaceDark
import com.iptvup.app.core.ui.theme.SurfaceElevated
import com.iptvup.app.core.ui.theme.TextPrimary
import com.iptvup.app.core.ui.theme.TextSecondary
import com.iptvup.app.core.ui.util.formatLastSync
import com.iptvup.app.update.UpdateState

@Composable
fun SettingsScreen(
    onSwitchProfile: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val channelCount by viewModel.channelCount.collectAsStateWithLifecycle()
    val hiddenUi by viewModel.hiddenUi.collectAsStateWithLifecycle()
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    var confirmLogout by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Text(
            text = "Ajustes",
            color = TextPrimary,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Cuenta, perfil y datos descargados",
            color = TextSecondary,
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(16.dp))

        SettingsCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(SurfaceElevated)
                        .padding(12.dp)
                ) {
                    Icon(Icons.Rounded.Person, contentDescription = null, tint = Primary)
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = viewModel.profile?.name ?: "Perfil",
                        color = TextPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = viewModel.email.orEmpty(),
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                viewModel.profile?.let { TypeBadge(it.kind.wire) }
            }
            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = SurfaceElevated)
            Spacer(Modifier.height(14.dp))
            Text(
                text = "$channelCount canales en caché · " +
                    "Última sync: ${formatLastSync(viewModel.profile?.lastSyncedAt)}",
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(Modifier.height(16.dp))
        SectionTitle("Datos")
        SettingsCard {
            if (viewModel.busy) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = Primary
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = viewModel.status.ifBlank { "Actualizando..." },
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Spacer(Modifier.height(12.dp))
            }
            Button(
                onClick = viewModel::sync,
                enabled = !viewModel.busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Actualizar lista y EPG")
            }
            viewModel.error?.let { message ->
                Spacer(Modifier.height(10.dp))
                ErrorBanner(message = message, onRetry = viewModel::sync)
            }
        }

        Spacer(Modifier.height(16.dp))
        SectionTitle("Elementos ocultos")
        SettingsCard {
            if (hiddenUi.groups.isEmpty() && hiddenUi.channels.isEmpty()) {
                Text(
                    text = "No hay grupos ni canales ocultos.",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (hiddenUi.groups.isNotEmpty()) {
                Text(
                    text = "Grupos",
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                hiddenUi.groups.forEach { group ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = group,
                            color = TextSecondary,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        IconButton(onClick = { viewModel.restoreGroup(group) }) {
                            Icon(
                                imageVector = Icons.Rounded.Visibility,
                                contentDescription = "Mostrar grupo",
                                tint = Primary
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            if (hiddenUi.channels.isNotEmpty()) {
                Text(
                    text = "Canales",
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                hiddenUi.channels.forEach { (key, name) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = name,
                            color = TextSecondary,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        IconButton(onClick = { viewModel.restoreChannel(key) }) {
                            Icon(
                                imageVector = Icons.Rounded.Visibility,
                                contentDescription = "Mostrar canal",
                                tint = Primary
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        SectionTitle("Cuenta")
        SettingsCard {
            OutlinedButton(
                onClick = { viewModel.switchProfile(onSwitchProfile) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.SwapHoriz, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Cambiar de perfil")
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = { confirmLogout = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.Logout, contentDescription = null, tint = Danger)
                Spacer(Modifier.width(8.dp))
                Text("Cerrar sesión", color = Danger)
            }
        }

        Spacer(Modifier.height(16.dp))
        SectionTitle("Actualizaciones")
        SettingsCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Versión instalada",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "v${BuildConfig.VERSION_NAME}",
                    color = TextPrimary,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(8.dp))
            when (val state = updateState) {
                UpdateState.Idle -> Text(
                    text = "Comprueba si hay una versión nueva.",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )

                UpdateState.Checking -> Text(
                    text = "Buscando actualizaciones...",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )

                is UpdateState.Available -> Text(
                    text = "Hay una versión nueva disponible: v${state.info.versionName}",
                    color = Primary,
                    style = MaterialTheme.typography.bodySmall
                )

                is UpdateState.Downloading -> {
                    LinearProgressIndicator(
                        progress = { state.progress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                        color = Primary
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Descargando... ${state.progress}%",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                UpdateState.Installing -> Text(
                    text = "Instalador abierto.",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )

                is UpdateState.Error -> ErrorBanner(message = state.message)
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = viewModel::checkUpdates,
                enabled = updateState !is UpdateState.Checking,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Buscar actualizaciones")
            }
        }

        Spacer(Modifier.height(16.dp))
        SectionTitle("Acerca de")
        SettingsCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "IptvUp",
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "v${BuildConfig.VERSION_NAME}",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Reproductor IPTV privado. La app no incluye listas ni contenido; " +
                    "tú aportas tu propia fuente legal.",
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(Modifier.height(28.dp))
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
private fun SectionTitle(text: String) {
    Text(
        text = text,
        color = TextSecondary,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content
        )
    }
}
