@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.notrust.vault.android.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import com.notrust.vault.android.ui.CATEGORY_STYLES
import com.notrust.vault.android.ui.theme.VaultColors
import com.notrust.vault.android.ui.theme.VaultFieldShape
import com.notrust.vault.android.ui.theme.VaultLabelTextStyle
import com.notrust.vault.android.ui.theme.VaultScreenTitleTextStyle
import com.notrust.vault.android.ui.theme.vaultFieldColors
import com.notrust.vault.model.EntrySecrets
import com.notrust.vault.vault.EntryIconCategory

val PRESET_TAGS = listOf("Banking", "Work", "Google", "Social Media")

data class EntryDraft(
    val alias: String,
    val siteName: String,
    val username: String,
    val password: String,
    val notes: String,
    val tags: List<String> = emptyList(),
    val iconOverride: String? = null
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
    var iconOverride by remember { mutableStateOf(initial?.iconOverride) }

    Scaffold(
        containerColor = VaultColors.Void,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (initial == null) "Add entry" else "Edit entry",
                        style = VaultScreenTitleTextStyle.copy(color = VaultColors.TextPrimary)
                    )
                },
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
            OutlinedTextField(
                alias, { alias = it }, label = { Text("Alias") }, singleLine = true,
                shape = VaultFieldShape, colors = vaultFieldColors(), modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                siteName, { siteName = it }, label = { Text("Website / service") }, singleLine = true,
                shape = VaultFieldShape, colors = vaultFieldColors(), modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
            )
            OutlinedTextField(
                username, { username = it }, label = { Text("Username") }, singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                shape = VaultFieldShape, colors = vaultFieldColors(), modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
            )
            OutlinedTextField(
                password, { password = it }, label = { Text("Password") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                shape = VaultFieldShape, colors = vaultFieldColors(), modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
            )
            OutlinedTextField(
                notes, { notes = it }, label = { Text("Notes (optional)") },
                shape = VaultFieldShape, colors = vaultFieldColors(), modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
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
                    shape = VaultFieldShape, colors = vaultFieldColors(),
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

            Spacer(modifier = Modifier.height(20.dp))
            Text("ICON", style = VaultLabelTextStyle.copy(color = VaultColors.TextMuted))
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Auto-detects from the site name and tags. Pick one yourself if it guesses wrong.",
                style = MaterialTheme.typography.bodyMedium,
                color = VaultColors.TextMuted
            )
            Spacer(modifier = Modifier.height(10.dp))
            FlowRow(
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = iconOverride == null,
                    onClick = { iconOverride = null },
                    label = { Text("Auto") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = VaultColors.Signal,
                        selectedLabelColor = Color(0xFF00201C)
                    )
                )
                EntryIconCategory.entries.filter { it != EntryIconCategory.GENERIC }.forEach { category ->
                    val style = CATEGORY_STYLES[category] ?: return@forEach
                    val selected = iconOverride == category.name
                    FilterChip(
                        selected = selected,
                        onClick = { iconOverride = category.name },
                        label = { Text(category.name.lowercase().replaceFirstChar(Char::uppercase)) },
                        leadingIcon = { Icon(style.icon, contentDescription = null, tint = style.color) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = VaultColors.Signal,
                            selectedLabelColor = Color(0xFF00201C)
                        )
                    )
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
                shape = VaultFieldShape, colors = vaultFieldColors(),
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
                    onSave(EntryDraft(alias.trim(), siteName.trim(), username, password, notes, tags, iconOverride), masterPassword)
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
