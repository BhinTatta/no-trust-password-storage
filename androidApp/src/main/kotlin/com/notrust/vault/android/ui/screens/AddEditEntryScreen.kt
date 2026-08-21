@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.notrust.vault.android.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.notrust.vault.android.ui.theme.VaultColors
import com.notrust.vault.android.ui.theme.VaultLabelTextStyle
import com.notrust.vault.android.ui.theme.vaultFieldColors
import com.notrust.vault.model.EntrySecrets

val PRESET_TAGS = listOf("Banking", "Work", "Google", "Social Media")

data class EntryDraft(
    val alias: String,
    val siteName: String,
    val username: String,
    val password: String,
    val notes: String,
    val tags: List<String> = emptyList()
) {
    fun toSecrets() = EntrySecrets(username = username, password = password, notes = notes)
}

@Composable
fun AddEditEntryScreen(
    initial: EntryDraft?,
    isSaving: Boolean,
    errorMessage: String?,
    onSave: (draft: EntryDraft, masterPassword: String) -> Unit,
    onBack: () -> Unit
) {
    var alias by remember { mutableStateOf(initial?.alias ?: "") }
    var siteName by remember { mutableStateOf(initial?.siteName ?: "") }
    var username by remember { mutableStateOf(initial?.username ?: "") }
    var password by remember { mutableStateOf(initial?.password ?: "") }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }
    var masterPassword by remember { mutableStateOf("") }

    var selectedPresets by remember {
        mutableStateOf((initial?.tags ?: emptyList()).filter { it in PRESET_TAGS }.toSet())
    }
    var customTags by remember {
        mutableStateOf((initial?.tags ?: emptyList()).filterNot { it in PRESET_TAGS })
    }
    var newTagText by remember { mutableStateOf("") }

    Scaffold(
        containerColor = VaultColors.Void,
        topBar = {
            TopAppBar(
                title = { Text(if (initial == null) "ADD ENTRY" else "EDIT ENTRY", style = VaultLabelTextStyle) },
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
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            OutlinedTextField(
                alias, { alias = it }, label = { Text("Alias") }, singleLine = true,
                colors = vaultFieldColors(), modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                siteName, { siteName = it }, label = { Text("Website / service") }, singleLine = true,
                colors = vaultFieldColors(), modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
            )
            OutlinedTextField(
                username, { username = it }, label = { Text("Username") }, singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                colors = vaultFieldColors(), modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
            )
            OutlinedTextField(
                password, { password = it }, label = { Text("Password") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                colors = vaultFieldColors(), modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
            )
            OutlinedTextField(
                notes, { notes = it }, label = { Text("Notes (optional)") },
                colors = vaultFieldColors(), modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))
            Text("TAGS", style = VaultLabelTextStyle.copy(color = VaultColors.TextMuted))
            Spacer(modifier = Modifier.height(10.dp))

            FlowRow(
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
            ) {
                PRESET_TAGS.forEach { tag ->
                    val selected = tag in selectedPresets
                    FilterChip(
                        selected = selected,
                        onClick = {
                            selectedPresets = if (selected) selectedPresets - tag else selectedPresets + tag
                        },
                        label = { Text(tag) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = VaultColors.Signal,
                            selectedLabelColor = Color(0xFF00201C)
                        )
                    )
                }
                customTags.forEach { tag ->
                    InputChip(
                        selected = false,
                        onClick = {},
                        label = { Text(tag) },
                        trailingIcon = {
                            IconButton(onClick = { customTags = customTags - tag }, modifier = Modifier.size(18.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Remove tag $tag")
                            }
                        },
                        colors = InputChipDefaults.inputChipColors(containerColor = VaultColors.SurfaceRaised)
                    )
                }
            }

            Row(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                OutlinedTextField(
                    value = newTagText,
                    onValueChange = { newTagText = it },
                    label = { Text("Custom tag") },
                    singleLine = true,
                    colors = vaultFieldColors(),
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = {
                        val trimmed = newTagText.trim()
                        if (trimmed.isNotEmpty() && trimmed !in customTags && trimmed !in PRESET_TAGS) {
                            customTags = customTags + trimmed
                        }
                        newTagText = ""
                    },
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add tag", tint = VaultColors.Signal)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text(
                "Saving needs your master password too — creating or editing a secret is as sensitive as viewing one.",
                style = MaterialTheme.typography.bodyMedium,
                color = VaultColors.TextMuted
            )
            OutlinedTextField(
                masterPassword, { masterPassword = it }, label = { Text("Master password") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                isError = errorMessage != null,
                colors = vaultFieldColors(),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
            if (errorMessage != null) {
                Text(errorMessage, color = VaultColors.Danger, modifier = Modifier.padding(top = 8.dp))
            }

            val canSave = alias.isNotBlank() && siteName.isNotBlank() && username.isNotBlank() &&
                password.isNotBlank() && masterPassword.isNotBlank()

            Button(
                onClick = {
                    val tags = (selectedPresets + customTags).toList()
                    onSave(EntryDraft(alias.trim(), siteName.trim(), username, password, notes, tags), masterPassword)
                },
                enabled = !isSaving && canSave,
                colors = ButtonDefaults.buttonColors(containerColor = VaultColors.Signal, contentColor = Color(0xFF00201C)),
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 24.dp)
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
