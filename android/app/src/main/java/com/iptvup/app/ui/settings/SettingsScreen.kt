package com.iptvup.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.platform.LocalContext
import com.iptvup.app.core.diag.DiagLog
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.foundation.clickable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
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
import com.iptvup.app.core.ui.theme.Accent
import com.iptvup.app.core.ui.theme.Danger
import com.iptvup.app.core.ui.theme.Primary
import com.iptvup.app.core.ui.theme.SurfaceDark
import com.iptvup.app.core.ui.theme.SurfaceElevated
import com.iptvup.app.core.ui.theme.TextPrimary
import com.iptvup.app.core.ui.theme.TextSecondary
import com.iptvup.app.core.ui.util.formatLastSync
import com.iptvup.app.core.ui.util.isTelevision
import com.iptvup.app.update.UpdateState

@Composable
fun SettingsScreen(
    onSwitchProfile: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val channelCount by viewModel.channelCount.collectAsStateWithLifecycle()
    val lastEpgSync by viewModel.lastEpgSync.collectAsStateWithLifecycle()
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    val syncOnOpen by viewModel.syncOnOpen.collectAsStateWithLifecycle()
    var confirmLogout by remember { mutableStateOf(false) }
    var showDiag by remember { mutableStateOf(false) }
    var diagText by remember { mutableStateOf("") }
    val buttonWidth = if (isTelevision()) 0.5f else 1f
    val context = LocalContext.current

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
                text = "$channelCount canales en caché",
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Última sync de canales: ${formatLastSync(viewModel.profile?.lastSyncedAt)}",
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Última sync de EPG: ${formatLastSync(lastEpgSync)}",
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(Modifier.height(16.dp))
        SectionTitle("Cuenta")
        SettingsCard {
            OutlinedButton(
                onClick = { viewModel.switchProfile(onSwitchProfile) },
                modifier = Modifier.fillMaxWidth(buttonWidth)
            ) {
                Icon(Icons.Rounded.SwapHoriz, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Cambiar de perfil")
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = { confirmLogout = true },
                modifier = Modifier.fillMaxWidth(buttonWidth)
            ) {
                Icon(Icons.Rounded.Logout, contentDescription = null, tint = Danger)
                Spacer(Modifier.width(8.dp))
                Text("Cerrar sesión", color = Danger)
            }
        }

        Spacer(Modifier.height(16.dp))
        SectionTitle("Lista de canales")
        SettingsCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.setSyncOnOpen(!syncOnOpen) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = syncOnOpen,
                    onCheckedChange = null
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Actualizar al entrar en el perfil",
                        color = TextPrimary,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Descarga la lista de canales cada vez que abres el perfil. " +
                            "Si está desactivado, actualiza con el botón de refresco de TV en directo.",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
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

                UpdateState.UpToDate -> Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = Accent,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "La app está actualizada",
                        color = Accent,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                is UpdateState.Available -> Column {
                    Text(
                        text = "Hay una versión nueva disponible: v${state.info.versionName}",
                        color = Primary,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = viewModel::installUpdate,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Actualizar a v${state.info.versionName}")
                    }
                    if (state.mandatory) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Esta actualización es obligatoria.",
                            color = Primary,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }

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

                is UpdateState.Error -> ErrorBanner(
                    message = state.message,
                    onRetry = viewModel::installUpdate
                )
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = viewModel::checkUpdates,
                enabled = updateState !is UpdateState.Checking &&
                    updateState !is UpdateState.Downloading &&
                    updateState !is UpdateState.Installing,
                modifier = Modifier.fillMaxWidth(buttonWidth)
            ) {
                Icon(Icons.Rounded.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (updateState is UpdateState.UpToDate) {
                        "Buscar de nuevo"
                    } else {
                        "Buscar actualizaciones"
                    }
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        SectionTitle("Diagnóstico")
        SettingsCard {
            Text(
                text = "Registra qué pasa con el reproductor (errores, reconexiones y cierres). " +
                    "Si un canal se queda en negro, abre el registro y cópialo.",
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = {
                    diagText = DiagLog.dump().ifBlank { "Todavía no hay registros." }
                    showDiag = true
                },
                modifier = Modifier.fillMaxWidth(buttonWidth)
            ) {
                Text("Ver registro")
            }
        }

        Spacer(Modifier.height(16.dp))
        SectionTitle("Acerca de")
        SettingsCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "IPTVUp",
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

    if (showDiag) {
        AlertDialog(
            onDismissRequest = { showDiag = false },
            title = { Text("Registro del reproductor") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = diagText,
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("IptvUp diagnóstico", diagText))
                    }
                ) {
                    Text("Copiar")
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            DiagLog.clear()
                            diagText = "Registro borrado."
                        }
                    ) {
                        Text("Borrar")
                    }
                    TextButton(onClick = { showDiag = false }) {
                        Text("Cerrar")
                    }
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
