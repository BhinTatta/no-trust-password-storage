@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.notrust.vault.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.notrust.vault.model.BrowseIndexItem
import com.notrust.vault.model.EntrySecrets

@Composable
fun EntryDetailScreen(
    item: BrowseIndexItem,
    revealed: EntrySecrets?,
    isRevealing: Boolean,
    revealError: String?,
    remainingRevealSeconds: Int?,
    onRevealRequest: (masterPassword: String) -> Unit,
    onCopyUsername: (String) -> Unit,
    onCopyPassword: (String) -> Unit,
    onEdit: (revealed: EntrySecrets) -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(item.alias, fontFamily = FontFamily.Monospace) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Edit only makes sense once you've actually seen the
                    // secrets — there is nothing to prefill otherwise, and
                    // silently overwriting an unseen secret is bad UX and
                    // bad data hygiene alike.
                    if (revealed != null) {
                        IconButton(onClick = { onEdit(revealed) }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit")
                        }
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text(item.siteName, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(24.dp))

            if (revealed == null) {
                RevealPrompt(isRevealing, revealError, onRevealRequest)
            } else {
                RevealedSecrets(
                    secrets = revealed,
                    remainingSeconds = remainingRevealSeconds,
                    onCopyUsername = onCopyUsername,
                    onCopyPassword = onCopyPassword
                )
            }
        }
    }
}

@Composable
private fun RevealPrompt(isRevealing: Boolean, error: String?, onRevealRequest: (String) -> Unit) {
    var password by remember { mutableStateOf("") }

    Text("Username and password are hidden. Enter your master password to view them.")
    Spacer(modifier = Modifier.height(12.dp))
    OutlinedTextField(
        value = password,
        onValueChange = { password = it },
        label = { Text("Master password") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        isError = error != null,
        modifier = Modifier.fillMaxWidth()
    )
    if (error != null) {
        Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
    }
    Button(
        onClick = { onRevealRequest(password) },
        enabled = !isRevealing && password.isNotEmpty(),
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
    ) {
        if (isRevealing) CircularProgressIndicator(modifier = Modifier.size(20.dp)) else Text("Reveal")
    }
}

@Composable
private fun RevealedSecrets(
    secrets: EntrySecrets,
    remainingSeconds: Int?,
    onCopyUsername: (String) -> Unit,
    onCopyPassword: (String) -> Unit
) {
    if (remainingSeconds != null) {
        Text(
            "Hiding again in ${remainingSeconds}s",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(12.dp))
    }

    LabeledSecretRow(label = "Username", value = secrets.username, onCopy = onCopyUsername)
    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
    LabeledSecretRow(label = "Password", value = secrets.password, onCopy = onCopyPassword)

    if (secrets.notes.isNotBlank()) {
        Spacer(modifier = Modifier.height(20.dp))
        Text("Notes", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(secrets.notes, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun LabeledSecretRow(label: String, value: String, onCopy: (String) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(value, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyLarge)
            IconButton(onClick = { onCopy(value) }) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Copy $label")
            }
        }
    }
}
