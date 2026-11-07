package com.iptvup.app.core.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.iptvup.app.core.ui.theme.Primary
import com.iptvup.app.core.ui.theme.SurfaceDark
import com.iptvup.app.core.ui.theme.TextPrimary
import com.iptvup.app.core.ui.theme.TextSecondary
import kotlinx.coroutines.delay

@Composable
fun SearchDialog(
    initialQuery: String,
    onQueryChange: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var value by remember { mutableStateOf(initialQuery) }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    LaunchedEffect(Unit) {
        delay(150)
        runCatching { focusRequester.requestFocus() }
        keyboard?.show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Buscar") },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = {
                    value = it
                    onQueryChange(it)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                shape = RoundedCornerShape(28.dp),
                placeholder = { Text("Canal o grupo") },
                trailingIcon = {
                    if (value.isNotEmpty()) {
                        TvIconButton(
                            onClick = {
                                value = ""
                                onQueryChange("")
                            }
                        ) {
                            Icon(Icons.Rounded.Clear, contentDescription = "Limpiar")
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                        keyboard?.hide()
                        onDismiss()
                    }
                ),
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
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Listo")
            }
        },
        dismissButton = {
            if (value.isNotEmpty()) {
                TextButton(
                    onClick = {
                        value = ""
                        onQueryChange("")
                    }
                ) {
                    Text("Limpiar")
                }
            }
        }
    )
}
