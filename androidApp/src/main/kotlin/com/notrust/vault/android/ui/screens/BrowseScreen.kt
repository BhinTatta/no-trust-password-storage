@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.notrust.vault.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.notrust.vault.android.ui.theme.VaultColors
import com.notrust.vault.android.ui.theme.VaultLabelTextStyle
import com.notrust.vault.android.ui.theme.vaultFieldColors
import com.notrust.vault.model.BrowseIndexItem

@Composable
fun BrowseScreen(
    items: List<BrowseIndexItem>,
    query: String,
    onQueryChange: (String) -> Unit,
    allTags: List<String>,
    selectedTag: String?,
    onTagSelected: (String?) -> Unit,
    onItemClick: (BrowseIndexItem) -> Unit,
    onAddClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onProfileClick: () -> Unit,
    integrityWarning: Boolean
) {
    Scaffold(
        containerColor = VaultColors.Void,
        topBar = {
            TopAppBar(
                title = { Text("VAULT", style = VaultLabelTextStyle.copy(color = VaultColors.Signal)) },
                navigationIcon = {
                    IconButton(onClick = onProfileClick) {
                        Icon(Icons.Default.AccountCircle, contentDescription = "Profile")
                    }
                },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = VaultColors.Void)
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddClick, containerColor = VaultColors.Signal, contentColor = Color(0xFF00201C)) {
                Icon(Icons.Default.Add, contentDescription = "Add entry")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (integrityWarning) {
                Text(
                    "This device looks rooted or is running under a debugger — see docs/SECURITY.md. This app still works, but its usual security guarantees rely on the device itself being trustworthy.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = VaultColors.Danger,
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                )
            }

            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                label = { Text("Search") },
                singleLine = true,
                colors = vaultFieldColors(),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
            )

            if (allTags.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    items(allTags, key = { it }) { tag ->
                        val selected = tag == selectedTag
                        FilterChip(
                            selected = selected,
                            onClick = { onTagSelected(if (selected) null else tag) },
                            label = { Text(tag) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = VaultColors.Signal,
                                selectedLabelColor = Color(0xFF00201C)
                            )
                        )
                    }
                }
            }

            if (items.isEmpty()) {
                Text(
                    if (query.isBlank() && selectedTag == null) "No entries yet — tap + to add one." else "No matches.",
                    color = VaultColors.TextMuted,
                    modifier = Modifier.padding(16.dp)
                )
            }

            LazyColumn(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(items, key = { it.id }) { item ->
                    VaultEntryCard(item = item, onClick = { onItemClick(item) })
                }
                item {
                    Spacer(modifier = Modifier.height(80.dp)) // clears the FAB
                }
            }
        }
    }
}

@Composable
private fun VaultEntryCard(item: BrowseIndexItem, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(VaultColors.Surface)
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Text(
            item.alias,
            fontFamily = FontFamily.Monospace,
            color = VaultColors.Signal,
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            item.siteName,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodyMedium,
            color = VaultColors.TextMuted
        )
        if (item.tags.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item.tags.forEach { tag ->
                    AssistChip(
                        onClick = {},
                        label = { Text(tag, style = MaterialTheme.typography.bodyMedium) },
                        colors = AssistChipDefaults.assistChipColors(containerColor = VaultColors.SurfaceRaised),
                        border = null
                    )
                }
            }
        }
    }
}
