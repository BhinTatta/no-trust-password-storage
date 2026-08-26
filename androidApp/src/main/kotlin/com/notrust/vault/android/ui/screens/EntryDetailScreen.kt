@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.notrust.vault.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import com.notrust.vault.android.ui.EntryIconBadge
import com.notrust.vault.android.ui.theme.VaultColors
import com.notrust.vault.android.ui.theme.VaultFieldShape
import com.notrust.vault.android.ui.theme.VaultLabelTextStyle
import com.notrust.vault.android.ui.theme.vaultFieldColors
import com.notrust.vault.model.BrowseIndexItem
import com.notrust.vault.model.EntrySecrets
import com.notrust.vault.totp.TotpCode
import com.notrust.vault.totp.TotpSeedParser
import kotlinx.coroutines.delay

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
    onCopyTotpCode: (String) -> Unit,
    onEdit: (revealed: EntrySecrets) -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        containerColor = VaultColors.Void,
        topBar = {
            TopAppBar(
                title = { Text(item.alias, fontFamily = FontFamily.Monospace, color = VaultColors.Signal) },
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
            EntryIconBadge(siteName = item.siteName, alias = item.alias, tags = item.tags, iconOverride = item.iconOverride, size = 56.dp)
            Spacer(modifier = Modifier.height(12.dp))
            Text(item.siteName, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace, color = VaultColors.TextMuted)
            if (item.tags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item.tags.forEach { tag ->
                        AssistChip(
                            onClick = {},
                            label = { Text(tag) },
                            colors = AssistChipDefaults.assistChipColors(containerColor = VaultColors.SurfaceRaised),
                            border = null
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))

            if (revealed == null) {
                RevealPrompt(isRevealing, revealError, onRevealRequest)
            } else {
                RevealedSecrets(
                    secrets = revealed,
                    remainingSeconds = remainingRevealSeconds,
                    onCopyUsername = onCopyUsername,
                    onCopyPassword = onCopyPassword,
                    onCopyTotpCode = onCopyTotpCode
                )
            }
        }
    }
}

@Composable
private fun RevealPrompt(isRevealing: Boolean, error: String?, onRevealRequest: (String) -> Unit) {
    var password by remember { mutableStateOf("") }

    Text("Username and password are hidden. Enter your master password to view them.", color = VaultColors.TextMuted)
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
        onClick = { onRevealRequest(password) },
        enabled = !isRevealing && password.isNotEmpty(),
        colors = ButtonDefaults.buttonColors(containerColor = VaultColors.Signal, contentColor = Color(0xFF00201C)),
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
    ) {
        if (isRevealing) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color(0xFF00201C))
        } else {
            Text("REVEAL", style = VaultLabelTextStyle.copy(color = Color(0xFF00201C)))
        }
    }
}

@Composable
private fun RevealedSecrets(
    secrets: EntrySecrets,
    remainingSeconds: Int?,
    onCopyUsername: (String) -> Unit,
    onCopyPassword: (String) -> Unit,
    onCopyTotpCode: (String) -> Unit
) {
    if (remainingSeconds != null) {
        Text(
            "Hiding again in ${remainingSeconds}s",
            style = MaterialTheme.typography.bodyMedium,
            color = VaultColors.Signal
        )
        Spacer(modifier = Modifier.height(12.dp))
    }

    LabeledSecretRow(label = "Username", value = secrets.username, onCopy = onCopyUsername)
    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = VaultColors.Hairline)
    LabeledSecretRow(label = "Password", value = secrets.password, onCopy = onCopyPassword)

    val totpSeed = secrets.totpSeed
    if (totpSeed != null) {
        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = VaultColors.Hairline)
        TotpCodeRow(totpSeed = totpSeed, onCopy = onCopyTotpCode)
    }

    if (secrets.notes.isNotBlank()) {
        Spacer(modifier = Modifier.height(20.dp))
        Text("Notes", style = MaterialTheme.typography.labelLarge, color = VaultColors.TextMuted)
        Text(secrets.notes, fontFamily = FontFamily.Monospace)
    }
}

/**
 * Live 6(-8)-digit authenticator code — only ever rendered from inside
 * [RevealedSecrets], i.e. only while this entry is in an active,
 * master-password-gated reveal, same as the username/password above it
 * and same as docs/ROADMAP.md's Phase 5 spec: TOTP never appears from a
 * biometric-only session, because the seed is at least as sensitive as
 * the password it protects — a leaked seed keeps generating valid codes
 * forever, not just once.
 */
@Composable
private fun TotpCodeRow(totpSeed: String, onCopy: (String) -> Unit) {
    val spec = remember(totpSeed) { TotpSeedParser.parse(totpSeed) }
    if (spec == null) {
        Text(
            "This entry's saved TOTP secret couldn't be read.",
            color = VaultColors.Danger,
            style = MaterialTheme.typography.bodyMedium
        )
        return
    }

    var code by remember(totpSeed) { mutableStateOf("") }
    var secondsLeft by remember(totpSeed) { mutableStateOf(spec.periodSeconds) }
    LaunchedEffect(totpSeed) {
        while (true) {
            val now = System.currentTimeMillis() / 1000
            code = TotpCode.code(spec, now)
            secondsLeft = TotpCode.secondsRemaining(spec, now)
            delay(1000)
        }
    }

    Column {
        Text("Authenticator code", style = MaterialTheme.typography.labelLarge, color = VaultColors.TextMuted)
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                groupDigits(code),
                fontFamily = FontFamily.Monospace,
                fontSize = 28.sp,
                color = VaultColors.Signal
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${secondsLeft}s",
                    style = MaterialTheme.typography.bodyMedium,
                    color = VaultColors.TextMuted,
                    modifier = Modifier.padding(end = 4.dp)
                )
                IconButton(onClick = { onCopy(code) }) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy authenticator code")
                }
            }
        }
    }
}

/** "123456" -> "123 456" — purely a readability aid, same idea as how phone numbers are grouped. */
private fun groupDigits(code: String): String {
    if (code.length < 6) return code
    val mid = code.length / 2
    return "${code.substring(0, mid)} ${code.substring(mid)}"
}

@Composable
private fun LabeledSecretRow(label: String, value: String, onCopy: (String) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge, color = VaultColors.TextMuted)
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
