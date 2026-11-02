package com.iptvup.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iptvup.app.BuildConfig
import com.iptvup.app.core.ui.components.ErrorBanner
import com.iptvup.app.core.ui.theme.Accent
import com.iptvup.app.core.ui.theme.Primary
import com.iptvup.app.core.ui.theme.SurfaceElevated
import com.iptvup.app.core.ui.theme.TextPrimary
import com.iptvup.app.core.ui.theme.TextSecondary
import com.iptvup.app.update.UpdateState

@Composable
fun GlobalSettingsDialog(
    onDismiss: () -> Unit,
    viewModel: GlobalSettingsViewModel = hiltViewModel()
) {
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.checkUpdates(manual = false)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ajustes") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                DialogSectionTitle("Actualizaciones")
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

                    UpdateState.UpToDate -> Row(verticalAlignment = Alignment.CenterVertically) {
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
                    onClick = { viewModel.checkUpdates(manual = true) },
                    enabled = updateState !is UpdateState.Checking &&
                        updateState !is UpdateState.Downloading &&
                        updateState !is UpdateState.Installing,
                    modifier = Modifier.fillMaxWidth()
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

                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = SurfaceElevated)
                Spacer(Modifier.height(16.dp))
                DialogSectionTitle("Acerca de")
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
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar")
            }
        }
    )
}

@Composable
private fun DialogSectionTitle(text: String) {
    Text(
        text = text,
        color = TextSecondary,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}
