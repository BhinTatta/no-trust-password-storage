package com.notrust.vault.android.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.notrust.vault.model.BrowseIndexItem

@Composable
fun BrowseScreen(
    items: List<BrowseIndexItem>,
    query: String,
    onQueryChange: (String) -> Unit,
    onItemClick: (BrowseIndexItem) -> Unit,
    onAddClick: () -> Unit,
    onSettingsClick: () -> Unit,
    integrityWarning: Boolean
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("No-Trust Vault") },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddClick) {
                Icon(Icons.Default.Add, contentDescription = "Add entry")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (integrityWarning) {
                Text(
                    "This device looks rooted or is running under a debugger — see docs/SECURITY.md. This app still works, but its usual security guarantees rely on the device itself being trustworthy.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                )
            }
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                label = { Text("Search") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
            )

            if (items.isEmpty()) {
                Text(
                    if (query.isBlank()) "No entries yet — tap + to add one." else "No matches.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }

            LazyColumn {
                items(items, key = { it.id }) { item ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onItemClick(item) }
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        Text(
                            item.alias,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            item.siteName,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}
