@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.notrust.vault.android.ui.screens

import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.notrust.vault.android.ui.theme.VaultColors
import com.notrust.vault.android.ui.theme.VaultFieldShape
import com.notrust.vault.android.ui.theme.VaultLabelTextStyle
import com.notrust.vault.android.ui.theme.VaultScreenTitleTextStyle
import com.notrust.vault.android.ui.theme.vaultFieldColors
import kotlinx.coroutines.launch
import java.io.File

/**
 * Whatever the vault is encrypted with is the only thing that can ever
 * decrypt this file — save-to-device, share, and "email to yourself" are
 * all just different ways of handing the user the exact same ciphertext
 * blob that's already sitting on disk. Nothing here ever touches a
 * plaintext secret.
 */
@Composable
fun ImportExportScreen(
    isWorking: Boolean,
    statusMessage: String?,
    statusIsError: Boolean,
    onExportRequested: suspend () -> ByteArray,
    onImportConfirmed: (bytes: ByteArray, masterPassword: String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var pendingImportBytes by remember { mutableStateOf<ByteArray?>(null) }
    var importPassword by remember { mutableStateOf("") }

    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            scope.launch {
                val bytes = onExportRequested()
                context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
            }
        }
    }

    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes != null) {
                    pendingImportBytes = bytes
                }
            }
        }
    }

    fun shareExport() {
        scope.launch {
            val bytes = onExportRequested()
            val cacheFile = File(context.cacheDir, "no-trust-vault-export.json")
            cacheFile.writeBytes(bytes)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", cacheFile)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "No-Trust Vault export")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share vault export"))
        }
    }

    Scaffold(
        containerColor = VaultColors.Void,
        topBar = {
            TopAppBar(
                title = { Text("Import / Export", style = VaultScreenTitleTextStyle.copy(color = VaultColors.TextPrimary)) },
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
            Text("EXPORT", style = VaultLabelTextStyle.copy(color = VaultColors.TextMuted))
            Text(
                "Everything in this vault, as one encrypted file. Only your master password can ever decrypt it — sharing or emailing it is as safe as it sitting on this phone.",
                style = MaterialTheme.typography.bodyMedium,
                color = VaultColors.TextMuted,
                modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)
            )
            Button(
                onClick = { saveLauncher.launch("no-trust-vault-export-${System.currentTimeMillis()}.json") },
                enabled = !isWorking,
                colors = ButtonDefaults.buttonColors(containerColor = VaultColors.Signal, contentColor = Color(0xFF00201C)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("SAVE TO DEVICE", style = VaultLabelTextStyle.copy(color = Color(0xFF00201C)))
            }
            OutlinedButton(
                onClick = { shareExport() },
                enabled = !isWorking,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
            ) {
                Text("SHARE / EMAIL TO MYSELF", style = VaultLabelTextStyle)
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 24.dp), color = VaultColors.Hairline)

            Text("IMPORT", style = VaultLabelTextStyle.copy(color = VaultColors.TextMuted))
            Text(
                "Restores a vault from an exported file. This REPLACES everything currently in this vault and cannot be undone.",
                style = MaterialTheme.typography.bodyMedium,
                color = VaultColors.Danger,
                modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)
            )
            OutlinedButton(
                onClick = { openLauncher.launch(arrayOf("application/json", "application/octet-stream", "*/*")) },
                enabled = !isWorking,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("CHOOSE EXPORT FILE", style = VaultLabelTextStyle)
            }

            val importedBytes = pendingImportBytes
            if (importedBytes != null) {
                Text(
                    "File loaded (${importedBytes.size} bytes). Enter the master password it was exported with to confirm.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = VaultColors.TextMuted,
                    modifier = Modifier.padding(top = 12.dp)
                )
                OutlinedTextField(
                    value = importPassword,
                    onValueChange = { importPassword = it },
                    label = { Text("Master password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    shape = VaultFieldShape, colors = vaultFieldColors(),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
                Button(
                    onClick = { onImportConfirmed(importedBytes, importPassword) },
                    enabled = !isWorking && importPassword.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = VaultColors.Danger, contentColor = Color.White),
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                ) {
                    Text("REPLACE VAULT WITH THIS FILE", style = VaultLabelTextStyle.copy(color = Color.White))
                }
            }

            if (statusMessage != null) {
                Text(
                    statusMessage,
                    color = if (statusIsError) VaultColors.Danger else VaultColors.Signal,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
        }
    }
}
