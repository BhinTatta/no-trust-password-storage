package com.notrust.vault.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notrust.vault.android.ui.CipherRainBackground
import com.notrust.vault.android.ui.theme.VaultColors
import com.notrust.vault.android.ui.theme.VaultFieldShape
import com.notrust.vault.android.ui.theme.VaultLabelTextStyle
import com.notrust.vault.android.ui.theme.vaultFieldColors

@Composable
fun UnlockScreen(
    isWorking: Boolean,
    errorMessage: String?,
    throttleSecondsRemaining: Int,
    biometricAvailable: Boolean,
    isBiometricWorking: Boolean,
    onUnlock: (masterPassword: String) -> Unit,
    onBiometricUnlock: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    val throttled = throttleSecondsRemaining > 0

    Box(modifier = Modifier.fillMaxSize().background(VaultColors.Void)) {
        CipherRainBackground(modifier = Modifier.fillMaxSize())

        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Mark ("NT") stands in for a logo — plain type, wide tracking,
            // signal-colored, so it reads as an instrument, not a brand.
            Text(
                "N·T",
                style = VaultLabelTextStyle.copy(fontSize = 15.sp, color = VaultColors.Signal)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "NO-TRUST VAULT",
                style = VaultLabelTextStyle.copy(color = VaultColors.TextMuted)
            )
            Spacer(modifier = Modifier.height(28.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(VaultColors.Surface.copy(alpha = 0.92f))
                    .padding(24.dp)
            ) {
                Text(
                    "Enter your master password",
                    style = MaterialTheme.typography.bodyMedium,
                    color = VaultColors.TextMuted
                )
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Master password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    isError = errorMessage != null,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                    shape = VaultFieldShape, colors = vaultFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorMessage != null) {
                    Text(errorMessage, color = VaultColors.Danger, modifier = Modifier.padding(top = 8.dp))
                }
                if (throttled) {
                    Text(
                        "Too many wrong attempts. Try again in ${throttleSecondsRemaining}s.",
                        color = VaultColors.Danger,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                Button(
                    onClick = { onUnlock(password) },
                    enabled = !isWorking && !throttled && password.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = VaultColors.Signal, contentColor = Color(0xFF00201C)),
                    modifier = Modifier.fillMaxWidth().padding(top = 20.dp)
                ) {
                    if (isWorking) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color(0xFF00201C))
                    } else {
                        Text("UNLOCK", style = VaultLabelTextStyle.copy(color = Color(0xFF00201C)))
                    }
                }

                if (biometricAvailable) {
                    OutlinedButton(
                        onClick = onBiometricUnlock,
                        // Deliberately gated on isBiometricWorking alone, not
                        // isWorking too: if the biometric prompt ever hangs
                        // (see AndroidBiometricKeyStore's own caveat about this
                        // being unverifiable without a real device), the master
                        // password path below must stay usable regardless.
                        enabled = !isBiometricWorking && !throttled,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                    ) {
                        if (isBiometricWorking) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = VaultColors.Signal)
                        } else {
                            Text("UNLOCK WITH BIOMETRICS", style = VaultLabelTextStyle)
                        }
                    }
                }
            }
        }
    }
}
