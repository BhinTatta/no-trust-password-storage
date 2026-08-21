package com.notrust.vault.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import com.notrust.vault.android.ui.CipherRainBackground
import com.notrust.vault.android.ui.theme.VaultColors
import com.notrust.vault.android.ui.theme.VaultLabelTextStyle
import com.notrust.vault.android.ui.theme.vaultFieldColors

@Composable
fun CreateVaultScreen(
    isWorking: Boolean,
    errorMessage: String?,
    onCreate: (masterPassword: String) -> Unit
) {
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }

    Box(modifier = Modifier.fillMaxSize().background(VaultColors.Void)) {
        CipherRainBackground(modifier = Modifier.fillMaxSize())

        Column(
            modifier = Modifier.fillMaxSize().padding(28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("FORGE YOUR VAULT", style = VaultLabelTextStyle.copy(color = VaultColors.Signal))
            Spacer(modifier = Modifier.height(20.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(VaultColors.Surface.copy(alpha = 0.92f))
                    .padding(24.dp)
            ) {
                Text("Set your master password", style = MaterialTheme.typography.titleMedium)
                Text(
                    "This encrypts everything. There is no recovery — if you forget it, the vault is gone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = VaultColors.TextMuted,
                    modifier = Modifier.padding(top = 6.dp, bottom = 20.dp)
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Master password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                    colors = vaultFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { confirm = it },
                    label = { Text("Confirm master password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                    colors = vaultFieldColors(),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                )

                val mismatch = confirm.isNotEmpty() && password != confirm
                if (mismatch) {
                    Text("Passwords don't match.", color = VaultColors.Danger, modifier = Modifier.padding(top = 8.dp))
                }
                if (errorMessage != null) {
                    Text(errorMessage, color = VaultColors.Danger, modifier = Modifier.padding(top = 8.dp))
                }

                Button(
                    onClick = { onCreate(password) },
                    enabled = !isWorking && password.isNotEmpty() && password == confirm,
                    colors = ButtonDefaults.buttonColors(containerColor = VaultColors.Signal, contentColor = Color(0xFF00201C)),
                    modifier = Modifier.fillMaxWidth().padding(top = 20.dp)
                ) {
                    if (isWorking) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color(0xFF00201C))
                    } else {
                        Text("CREATE VAULT", style = VaultLabelTextStyle.copy(color = Color(0xFF00201C)))
                    }
                }
            }
        }
    }
}
