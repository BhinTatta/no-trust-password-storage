@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.notrust.vault.android.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.notrust.vault.android.ui.theme.VaultColors
import com.notrust.vault.android.ui.theme.VaultFieldShape
import com.notrust.vault.android.ui.theme.VaultLabelTextStyle
import com.notrust.vault.android.ui.theme.VaultScreenTitleTextStyle
import com.notrust.vault.android.ui.theme.vaultFieldColors
import com.notrust.vault.totp.TotpSeedParser

private const val ALIAS_MAX_LENGTH = 40

/**
 * Adds a standalone authenticator entry — a separate feature from password
 * entries entirely, so this screen never touches [EntryDraft]/[EntrySecrets].
 * Reached only from an already-unlocked [AuthenticatorScreen], so it doesn't
 * ask for the master password again — that was already given to view the
 * list this entry is about to join.
 */
@Composable
fun AddAuthenticatorScreen(
    isSaving: Boolean,
    errorMessage: String?,
    onSave: (alias: String?, seed: String) -> Unit,
    onBack: () -> Unit
) {
    var aliasText by remember { mutableStateOf("") }
    var seedText by remember { mutableStateOf("") }
    var isScanningQr by remember { mutableStateOf(false) }

    if (isScanningQr) {
        QrScannerScreen(
            onScanned = { scanned ->
                seedText = scanned
                isScanningQr = false
            },
            onCancel = { isScanningQr = false }
        )
        return
    }

    val parsed = remember(seedText) { seedText.takeIf { it.isNotBlank() }?.let { TotpSeedParser.parse(it) } }
    val seedInvalid = seedText.isNotBlank() && parsed == null
    val previewLabel = remember(seedText) { TotpSeedParser.previewLabel(seedText) }

    Scaffold(
        containerColor = VaultColors.Void,
        topBar = {
            TopAppBar(
                title = { Text("Add authenticator", style = VaultScreenTitleTextStyle.copy(color = VaultColors.TextPrimary)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = VaultColors.Void)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                "Scan the QR code your service shows for two-factor setup, or paste its secret key or otpauth:// link.",
                style = MaterialTheme.typography.bodyMedium,
                color = VaultColors.TextMuted
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(
                onClick = { isScanningQr = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("SCAN QR CODE", style = VaultLabelTextStyle)
            }
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = seedText,
                onValueChange = { seedText = it },
                label = { Text("Secret key or otpauth:// link") },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                shape = VaultFieldShape,
                colors = vaultFieldColors(),
                trailingIcon = if (seedText.isNotEmpty()) {
                    {
                        IconButton(onClick = { seedText = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear secret")
                        }
                    }
                } else null,
                modifier = Modifier.fillMaxWidth()
            )
            if (seedInvalid) {
                Text(
                    "Doesn't look like a valid secret key or otpauth:// link.",
                    color = VaultColors.Danger,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 6.dp)
                )
            } else if (parsed != null && previewLabel != null) {
                Text(
                    "Detected: $previewLabel",
                    color = VaultColors.Signal,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
            OutlinedTextField(
                value = aliasText,
                onValueChange = { if (it.length <= ALIAS_MAX_LENGTH) aliasText = it },
                label = { Text("Alias (optional)") },
                singleLine = true,
                placeholder = { Text(previewLabel ?: "Authenticator") },
                shape = VaultFieldShape,
                colors = vaultFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                "Leave blank to use the name from the QR code.",
                style = MaterialTheme.typography.bodyMedium,
                color = VaultColors.TextMuted,
                modifier = Modifier.padding(top = 4.dp)
            )

            if (errorMessage != null) {
                Text(errorMessage, color = VaultColors.Danger, modifier = Modifier.padding(top = 12.dp))
            }

            Button(
                onClick = { onSave(aliasText.trim().ifEmpty { null }, seedText.trim()) },
                enabled = !isSaving && parsed != null,
                colors = ButtonDefaults.buttonColors(containerColor = VaultColors.Signal, contentColor = Color(0xFF00201C)),
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 24.dp)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color(0xFF00201C))
                } else {
                    Text("SAVE", style = VaultLabelTextStyle.copy(color = Color(0xFF00201C)))
                }
            }
        }
    }
}
