@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.notrust.vault.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.notrust.vault.android.ui.theme.VaultColors
import com.notrust.vault.android.ui.theme.VaultLabelTextStyle

/**
 * There are no accounts in this app — no name, no email, nothing to sign
 * into (see README, "Why"). What stands in for a profile is the vault's
 * own identity: how much it holds, and the state of the defenses around
 * it. Framed honestly as that, not dressed up as a personal profile.
 */
@Composable
fun ProfileScreen(
    entryCount: Int,
    tagCounts: List<Pair<String, Int>>,
    biometricEnabled: Boolean,
    decoyConfigured: Boolean,
    onBack: () -> Unit
) {
    Scaffold(
        containerColor = VaultColors.Void,
        topBar = {
            TopAppBar(
                title = { Text("VAULT STATUS", style = VaultLabelTextStyle.copy(color = VaultColors.Signal)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = VaultColors.Void)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(VaultColors.SurfaceRaised),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Shield, contentDescription = null, tint = VaultColors.Signal)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text("$entryCount entries secured", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Encrypted on this device only, by design.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = VaultColors.TextMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
            Text("DEFENSES", style = VaultLabelTextStyle.copy(color = VaultColors.TextMuted))
            Spacer(modifier = Modifier.height(12.dp))

            StatusRow(
                icon = Icons.Default.Fingerprint,
                label = "Biometric browse unlock",
                value = if (biometricEnabled) "Enabled" else "Off",
                active = biometricEnabled
            )
            StatusRow(
                icon = Icons.Default.VisibilityOff,
                label = "Decoy password",
                value = if (decoyConfigured) "Configured" else "Not set up",
                active = decoyConfigured
            )

            if (tagCounts.isNotEmpty()) {
                Spacer(modifier = Modifier.height(28.dp))
                Text("BY TAG", style = VaultLabelTextStyle.copy(color = VaultColors.TextMuted))
                Spacer(modifier = Modifier.height(12.dp))
                tagCounts.forEach { (tag, count) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(tag, fontFamily = FontFamily.Monospace)
                        Text("$count", fontFamily = FontFamily.Monospace, color = VaultColors.TextMuted)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String, active: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(VaultColors.Surface)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = if (active) VaultColors.Signal else VaultColors.TextFaint)
        Spacer(modifier = Modifier.width(12.dp))
        Text(label, modifier = Modifier.weight(1f))
        Text(value, color = if (active) VaultColors.Signal else VaultColors.TextMuted)
    }
}
