package com.notrust.vault.android.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(
    biometricAvailable: Boolean,
    biometricEnabled: Boolean,
    onToggleBiometric: (enable: Boolean) -> Unit,
    decoyConfigured: Boolean,
    decoyError: String?,
    onSetupDecoy: (decoyPassword: String) -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text("Security", style = MaterialTheme.typography.titleMedium)

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Biometric unlock")
                    Text(
                        "Fingerprint/face unlocks browsing only — usernames and passwords always need your master password.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = biometricEnabled,
                    onCheckedChange = onToggleBiometric,
                    enabled = biometricAvailable || biometricEnabled
                )
            }
            if (!biometricAvailable && !biometricEnabled) {
                Text(
                    "No biometric enrolled on this device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 20.dp))

            Text("Decoy password", style = MaterialTheme.typography.titleMedium)
            Text(
                "Unlocking with a second password instead of your real one opens an empty, separate vault — for when someone is forcing you to unlock your phone. This vault stays exactly as it is either way.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)
            )

            if (decoyConfigured) {
                Text("A decoy password is already set up.", color = MaterialTheme.colorScheme.primary)
            } else {
                var decoyPassword by remember { mutableStateOf("") }
                var confirm by remember { mutableStateOf("") }
                OutlinedTextField(
                    value = decoyPassword,
                    onValueChange = { decoyPassword = it },
                    label = { Text("Decoy password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { confirm = it },
                    label = { Text("Confirm decoy password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
                if (decoyError != null) {
                    Text(decoyError, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
                }
                Button(
                    onClick = { onSetupDecoy(decoyPassword) },
                    enabled = decoyPassword.isNotEmpty() && decoyPassword == confirm,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                ) {
                    Text("Set up decoy password")
                }
            }
        }
    }
}
