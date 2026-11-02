package com.iptvup.app.ui.profiles

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.iptvup.app.core.ui.components.AppBackground
import com.iptvup.app.core.ui.components.LoadingOverlay
import com.iptvup.app.core.ui.theme.Danger
import com.iptvup.app.core.ui.theme.Primary
import com.iptvup.app.core.ui.theme.SurfaceDark
import com.iptvup.app.core.ui.theme.TextPrimary
import com.iptvup.app.core.ui.theme.TextSecondary
import com.iptvup.app.data.model.ProfileKind

@Composable
fun AddProfileScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: AddProfileViewModel = hiltViewModel()
) {
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
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Volver",
                        tint = TextPrimary
                    )
                }
                Text(
                    text = if (viewModel.isEditing) "Editar perfil" else "Nuevo perfil",
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp)
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .padding(horizontal = 24.dp, vertical = 10.dp)
            ) {
                Text(
                    text = "Ponle un nombre y elige cómo acceder a tu lista.",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(20.dp))

                OutlinedTextField(
                    value = viewModel.name,
                    onValueChange = {
                        viewModel.name = it
                        viewModel.clearError()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Nombre del perfil") },
                    placeholder = { Text("Casa, deportes, etc.") },
                    singleLine = true,
                    colors = fieldColors()
                )
                Spacer(Modifier.height(18.dp))

                Text(
                    text = "Tipo de acceso",
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilterChip(
                        selected = viewModel.kind == ProfileKind.M3U,
                        onClick = {
                            viewModel.kind = ProfileKind.M3U
                            viewModel.clearError()
                        },
                        label = { Text("Lista M3U") }
                    )
                    FilterChip(
                        selected = viewModel.kind == ProfileKind.XTREAM,
                        onClick = {
                            viewModel.kind = ProfileKind.XTREAM
                            viewModel.clearError()
                        },
                        label = { Text("Xtream Codes") }
                    )
                }
                Spacer(Modifier.height(18.dp))

                if (viewModel.kind == ProfileKind.M3U) {
                    OutlinedTextField(
                        value = viewModel.m3uUrl,
                        onValueChange = {
                            viewModel.m3uUrl = it
                            viewModel.clearError()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("URL de la lista M3U") },
                        placeholder = { Text("https://.../lista.m3u") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Next
                        ),
                        colors = fieldColors()
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Pega la URL completa. Si incluye usuario y contraseña, se guardará cifrada.",
                        color = TextSecondary,
                        style = MaterialTheme.typography.labelSmall
                    )
                } else {
                    OutlinedTextField(
                        value = viewModel.xtreamHost,
                        onValueChange = {
                            viewModel.xtreamHost = it
                            viewModel.clearError()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Host de Xtream") },
                        placeholder = { Text("http://servidor:8080") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Next
                        ),
                        colors = fieldColors()
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = viewModel.xtreamUsername,
                        onValueChange = {
                            viewModel.xtreamUsername = it
                            viewModel.clearError()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Usuario") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        colors = fieldColors()
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = viewModel.xtreamPassword,
                        onValueChange = {
                            viewModel.xtreamPassword = it
                            viewModel.clearError()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Contraseña") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Next
                        ),
                        colors = fieldColors()
                    )
                }

                Spacer(Modifier.height(18.dp))
                OutlinedTextField(
                    value = viewModel.epgUrl,
                    onValueChange = {
                        viewModel.epgUrl = it
                        viewModel.clearError()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("URL del EPG (opcional)") },
                    placeholder = { Text("Se detecta automáticamente si es posible") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done
                    ),
                    colors = fieldColors()
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Si tu proveedor no la incluye en la lista, puedes pegar aquí el XMLTV.",
                    color = TextSecondary,
                    style = MaterialTheme.typography.labelSmall
                )

                Spacer(Modifier.height(20.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = Primary.copy(alpha = 0.08f),
                            shape = RoundedCornerShape(14.dp)
                        )
                        .padding(14.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Info,
                        contentDescription = null,
                        tint = Primary
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "Los datos de acceso se cifran antes de guardarse en la nube. " +
                            "La app necesita que la API esté disponible para crear el perfil.",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                viewModel.error?.let { message ->
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = message,
                        color = Danger,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(Modifier.height(22.dp))
                Button(
                    onClick = { viewModel.save(onSaved) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    enabled = !viewModel.loading && !viewModel.initialLoading
                ) {
                    Text(
                        text = when {
                            viewModel.loading -> "Guardando..."
                            viewModel.isEditing -> "Guardar cambios"
                            else -> "Guardar perfil"
                        },
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(Modifier.height(28.dp))
            }
        }

        if (viewModel.initialLoading) {
            LoadingOverlay(message = "Cargando perfil...")
        } else if (viewModel.loading) {
            LoadingOverlay(
                message = if (viewModel.isEditing) "Guardando cambios..." else "Creando perfil..."
            )
        }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = SurfaceDark,
    unfocusedContainerColor = SurfaceDark,
    focusedBorderColor = Primary,
    unfocusedBorderColor = TextSecondary.copy(alpha = 0.35f),
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    focusedLabelColor = Primary,
    unfocusedLabelColor = TextSecondary,
    cursorColor = Primary
)
