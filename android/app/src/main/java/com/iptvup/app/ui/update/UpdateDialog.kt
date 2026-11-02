package com.iptvup.app.ui.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.iptvup.app.core.ui.theme.Primary
import com.iptvup.app.core.ui.theme.TextSecondary
import com.iptvup.app.update.UpdateState

@Composable
fun UpdateDialog(
    state: UpdateState,
    onInstall: () -> Unit,
    onSkip: () -> Unit,
    onDismiss: () -> Unit
) {
    when (state) {
        UpdateState.Idle, UpdateState.Checking -> Unit

        is UpdateState.Available -> {
            AlertDialog(
                onDismissRequest = { if (!state.mandatory) onDismiss() },
                title = { Text("Actualización disponible") },
                text = {
                    Column(
                        modifier = Modifier
                            .heightIn(max = 320.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = "Versión ${state.info.versionName}",
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = state.info.notes.ifBlank {
                                "Mejoras y correcciones de la aplicación."
                            },
                            color = TextSecondary
                        )
                        if (state.mandatory) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = "Esta actualización es obligatoria para seguir usando la app.",
                                color = Primary
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = onInstall) {
                        Text("Actualizar")
                    }
                },
                dismissButton = {
                    if (!state.mandatory) {
                        Column {
                            TextButton(onClick = onDismiss) {
                                Text("Más tarde")
                            }
                            TextButton(onClick = onSkip) {
                                Text("Omitir esta versión", color = TextSecondary)
                            }
                        }
                    }
                }
            )
        }

        is UpdateState.Downloading -> {
            AlertDialog(
                onDismissRequest = {},
                title = { Text("Descargando actualización") },
                text = {
                    Column {
                        LinearProgressIndicator(
                            progress = { state.progress / 100f },
                            modifier = Modifier.fillMaxWidth(),
                            color = Primary
                        )
                        Spacer(Modifier.height(10.dp))
                        Text("${state.progress}%")
                    }
                },
                confirmButton = {}
            )
        }

        UpdateState.Installing -> {
            AlertDialog(
                onDismissRequest = {},
                title = { Text("Instalador abierto") },
                text = {
                    Text(
                        "Sigue las instrucciones del sistema para instalar la nueva versión."
                    )
                },
                confirmButton = {
                    TextButton(onClick = onDismiss) {
                        Text("Cerrar")
                    }
                }
            )
        }

        is UpdateState.Error -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("No se pudo actualizar") },
                text = { Text(state.message) },
                confirmButton = {
                    TextButton(onClick = onInstall) {
                        Text("Reintentar")
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) {
                        Text("Cerrar")
                    }
                }
            )
        }
    }
}
