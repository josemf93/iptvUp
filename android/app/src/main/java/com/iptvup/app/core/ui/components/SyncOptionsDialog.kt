package com.iptvup.app.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.iptvup.app.core.ui.theme.TextSecondary

@Composable
fun SyncOptionsDialog(
    onSyncFiltered: () -> Unit,
    onSyncAll: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Actualizar canales y EPG") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "La actualización automática diaria usa solo los grupos visibles.",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
                Button(
                    onClick = onSyncFiltered,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Text("Solo grupos visibles", fontWeight = FontWeight.SemiBold)
                        Text(
                            text = "Rápido · lo que se actualiza cada 24 h",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
                OutlinedButton(
                    onClick = onSyncAll,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Text("Todos los canales", fontWeight = FontWeight.SemiBold)
                        Text(
                            text = "Más lento · incluye grupos ocultos",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
