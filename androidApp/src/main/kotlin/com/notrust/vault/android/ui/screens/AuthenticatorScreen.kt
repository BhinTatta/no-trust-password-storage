@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.notrust.vault.android.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notrust.vault.android.ui.theme.VaultColors
import com.notrust.vault.android.ui.theme.VaultFieldShape
import com.notrust.vault.android.ui.theme.VaultLabelTextStyle
import com.notrust.vault.android.ui.theme.VaultScreenTitleTextStyle
import com.notrust.vault.android.ui.theme.vaultFieldColors
import com.notrust.vault.model.TotpEntry
import com.notrust.vault.totp.TotpCode
import com.notrust.vault.totp.TotpSeedParser
import kotlinx.coroutines.delay

/**
 * A standalone screen for built-in authenticator (TOTP) codes — a
 * different feature from password entries, not reachable from any one
 * password's detail screen. Unlocking this screen (once, per visit) asks
 * for the master password same as any secrets-tier read; once unlocked,
 * every saved code is visible and live at once, no per-code reveal —
 * that's how every real authenticator app behaves, and there's nothing
 * more sensitive being shown per-code than what unlocking already exposed.
 */
@Composable
fun AuthenticatorScreen(
    entries: List<TotpEntry>?,
    isUnlocking: Boolean,
    unlockError: String?,
    onUnlock: (masterPassword: String) -> Unit,
    onAddClick: () -> Unit,
    onDeleteEntry: (TotpEntry) -> Unit,
    onCopyCode: (String) -> Unit,
    bottomBar: @Composable () -> Unit
) {
    Scaffold(
        containerColor = VaultColors.Void,
        topBar = {
            TopAppBar(
                title = { Text("Authenticator", style = VaultScreenTitleTextStyle.copy(color = VaultColors.TextPrimary)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = VaultColors.Void)
            )
        },
        bottomBar = bottomBar,
        floatingActionButton = {
            if (entries != null) {
                FloatingActionButton(
                    onClick = onAddClick,
                    containerColor = VaultColors.Signal,
                    contentColor = Color(0xFF00201C)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add authenticator")
                }
            }
        }
    ) { padding ->
        when {
            entries == null -> AuthenticatorUnlockPrompt(
                modifier = Modifier.fillMaxSize().padding(padding),
                isUnlocking = isUnlocking,
                error = unlockError,
                onUnlock = onUnlock
            )
            entries.isEmpty() -> AuthenticatorEmptyState(modifier = Modifier.fillMaxSize().padding(padding), onAddClick = onAddClick)
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp)
            ) {
                items(entries, key = { it.id }) { entry ->
                    TotpEntryRow(entry = entry, onCopy = onCopyCode, onDelete = { onDeleteEntry(entry) })
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = VaultColors.Hairline)
                }
            }
        }
    }
}

@Composable
private fun AuthenticatorUnlockPrompt(
    modifier: Modifier,
    isUnlocking: Boolean,
    error: String?,
    onUnlock: (String) -> Unit
) {
    var password by remember { mutableStateOf("") }
    Column(
        modifier = modifier.imePadding().verticalScroll(rememberScrollState()).padding(16.dp)
    ) {
        Text(
            "Your saved authenticator codes are locked. Enter your master password to view them — a leaked code seed works forever, so it gets the same protection as your passwords.",
            color = VaultColors.TextMuted
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Master password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            isError = error != null,
            shape = VaultFieldShape, colors = vaultFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )
        if (error != null) {
            Text(error, color = VaultColors.Danger, modifier = Modifier.padding(top = 8.dp))
        }
        Button(
            onClick = { onUnlock(password) },
            enabled = !isUnlocking && password.isNotEmpty(),
            colors = ButtonDefaults.buttonColors(containerColor = VaultColors.Signal, contentColor = Color(0xFF00201C)),
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
        ) {
            if (isUnlocking) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color(0xFF00201C))
            } else {
                Text("UNLOCK", style = VaultLabelTextStyle.copy(color = Color(0xFF00201C)))
            }
        }
    }
}

@Composable
private fun AuthenticatorEmptyState(modifier: Modifier, onAddClick: () -> Unit) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(48.dp))
        Icon(Icons.Default.Key, contentDescription = null, tint = VaultColors.TextMuted, modifier = Modifier.size(40.dp))
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "No authenticator codes yet",
            style = VaultScreenTitleTextStyle.copy(color = VaultColors.TextPrimary)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Scan a service's QR code or paste its secret key to start generating codes here.",
            style = MaterialTheme.typography.bodyMedium,
            color = VaultColors.TextMuted
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = onAddClick,
            colors = ButtonDefaults.buttonColors(containerColor = VaultColors.Signal, contentColor = Color(0xFF00201C))
        ) {
            Text("ADD AUTHENTICATOR", style = VaultLabelTextStyle.copy(color = Color(0xFF00201C)))
        }
    }
}

@Composable
private fun TotpEntryRow(entry: TotpEntry, onCopy: (String) -> Unit, onDelete: () -> Unit) {
    val spec = remember(entry.seed) { TotpSeedParser.parse(entry.seed) }
    val displayName = entry.alias?.takeIf { it.isNotBlank() }
        ?: remember(entry.seed) { TotpSeedParser.previewLabel(entry.seed) }
        ?: "Authenticator"
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (spec == null) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(displayName, style = MaterialTheme.typography.labelLarge, color = VaultColors.TextMuted)
                Text("Saved code couldn't be read.", color = VaultColors.Danger, style = MaterialTheme.typography.bodyMedium)
            }
            IconButton(onClick = { showDeleteConfirm = true }) {
                Icon(Icons.Default.Delete, contentDescription = "Remove $displayName")
            }
        }
    } else {
        var code by remember(entry.seed) { mutableStateOf("") }
        var remainingFraction by remember(entry.seed) { mutableStateOf(1f) }
        var secondsLeft by remember(entry.seed) { mutableStateOf(spec.periodSeconds) }
        LaunchedEffect(entry.seed) {
            while (true) {
                val now = System.currentTimeMillis() / 1000
                code = TotpCode.code(spec, now)
                secondsLeft = TotpCode.secondsRemaining(spec, now)
                remainingFraction = secondsLeft.toFloat() / spec.periodSeconds
                delay(200)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().clickable { onCopy(code) }.padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PieTimer(remainingFraction = remainingFraction, urgent = secondsLeft <= 5, modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(displayName, style = MaterialTheme.typography.labelLarge, color = VaultColors.TextMuted)
                Text(groupDigits(code), fontFamily = FontFamily.Monospace, fontSize = 24.sp, color = VaultColors.Signal)
            }
            IconButton(onClick = { onCopy(code) }) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Copy code for $displayName")
            }
            IconButton(onClick = { showDeleteConfirm = true }) {
                Icon(Icons.Default.Delete, contentDescription = "Remove $displayName")
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Remove \"$displayName\"?") },
            text = { Text("You'll lose these codes unless you still have the original QR code or secret key saved elsewhere.") },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; onDelete() }) {
                    Text("REMOVE", color = VaultColors.Danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("CANCEL") }
            },
            containerColor = VaultColors.Surface
        )
    }
}

/** A shrinking pie wedge — the standard "time left" visual every authenticator app uses instead of a plain countdown number. */
@Composable
private fun PieTimer(remainingFraction: Float, urgent: Boolean, modifier: Modifier = Modifier) {
    val wedgeColor = if (urgent) VaultColors.Danger else VaultColors.Signal
    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(color = wedgeColor.copy(alpha = 0.18f))
            drawArc(
                color = wedgeColor,
                startAngle = -90f,
                sweepAngle = 360f * remainingFraction.coerceIn(0f, 1f),
                useCenter = true
            )
        }
    }
}

/** "123456" -> "123 456" — purely a readability aid, same idea as how phone numbers are grouped. */
private fun groupDigits(code: String): String {
    if (code.length < 6) return code
    val mid = code.length / 2
    return "${code.substring(0, mid)} ${code.substring(mid)}"
}
