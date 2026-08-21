@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.notrust.vault.android.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import com.notrust.vault.model.EntrySecrets

data class EntryDraft(
    val alias: String,
    val siteName: String,
    val username: String,
    val password: String,
    val notes: String
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (initial == null) "Add entry" else "Edit entry") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
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
            OutlinedTextField(alias, { alias = it }, label = { Text("Alias") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(siteName, { siteName = it }, label = { Text("Website / service") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
            OutlinedTextField(username, { username = it }, label = { Text("Username") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
            OutlinedTextField(
                password, { password = it }, label = { Text("Password") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
            )
            OutlinedTextField(notes, { notes = it }, label = { Text("Notes (optional)") }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))

            Text(
                "Saving needs your master password too — creating or editing a secret is as sensitive as viewing one.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 20.dp)
            )
            OutlinedTextField(
                masterPassword, { masterPassword = it }, label = { Text("Master password") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                isError = errorMessage != null,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
            if (errorMessage != null) {
                Text(errorMessage, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
            }

            val canSave = alias.isNotBlank() && siteName.isNotBlank() && username.isNotBlank() &&
                password.isNotBlank() && masterPassword.isNotBlank()

            Button(
                onClick = {
                    onSave(EntryDraft(alias.trim(), siteName.trim(), username, password, notes), masterPassword)
                },
                enabled = !isSaving && canSave,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
            ) {
                if (isSaving) CircularProgressIndicator(modifier = Modifier.size(20.dp)) else Text("Save")
            }
        }
    }
}
