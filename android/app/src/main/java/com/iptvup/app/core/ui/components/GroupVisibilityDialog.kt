package com.iptvup.app.core.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.iptvup.app.core.ui.theme.Primary
import com.iptvup.app.core.ui.theme.SurfaceDark
import com.iptvup.app.core.ui.theme.TextPrimary
import com.iptvup.app.core.ui.theme.TextSecondary

@Composable
fun GroupVisibilityDialog(
    groups: List<String>,
    hiddenGroups: Set<String>,
    onToggle: (String) -> Unit,
    onShowAll: () -> Unit,
    onHideAll: () -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val visibleGroups = remember(groups, query) {
        if (query.isBlank()) {
            groups
        } else {
            groups.filter { it.contains(query, ignoreCase = true) }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Grupos visibles") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Desactiva los grupos que no quieras ver en TV y en la guía.",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    shape = RoundedCornerShape(28.dp),
                    placeholder = { Text("Buscar grupo...") },
                    leadingIcon = {
                        Icon(Icons.Rounded.Search, contentDescription = null)
                    },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            TvIconButton(onClick = { query = "" }) {
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = onShowAll) {
                        Text("Mostrar todos")
                    }
                    TextButton(onClick = onHideAll) {
                        Text("Ocultar todos")
                    }
                }
                if (groups.isEmpty()) {
                    Text(
                        text = "No hay grupos en este perfil.",
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                } else if (visibleGroups.isEmpty()) {
                    Text(
                        text = "Sin resultados para \"$query\".",
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
                visibleGroups.forEach { group ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = group,
                            color = TextPrimary,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Checkbox(
                            checked = group !in hiddenGroups,
                            onCheckedChange = { onToggle(group) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Listo")
            }
        }
    )
}
