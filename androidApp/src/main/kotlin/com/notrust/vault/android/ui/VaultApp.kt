package com.notrust.vault.android.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.notrust.vault.android.VaultRepository
import com.notrust.vault.android.ui.screens.AddEditEntryScreen
import com.notrust.vault.android.ui.screens.BrowseScreen
import com.notrust.vault.android.ui.screens.CreateVaultScreen
import com.notrust.vault.android.ui.screens.EntryDetailScreen
import com.notrust.vault.android.ui.screens.EntryDraft
import com.notrust.vault.android.ui.screens.UnlockScreen
import com.notrust.vault.android.copyThenAutoClear
import com.notrust.vault.android.ui.theme.NoTrustVaultTheme
import com.notrust.vault.crypto.VaultDecryptionFailed
import com.notrust.vault.model.BrowseIndexItem
import com.notrust.vault.model.EntrySecrets
import com.notrust.vault.vault.VaultFile
import com.notrust.vault.vault.VaultSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** How long revealed secrets stay on screen before auto-redacting. See docs/SECURITY.md. */
private const val REVEAL_DISPLAY_SECONDS = 20

private sealed interface Screen {
    data object Loading : Screen
    data object CreateVault : Screen
    data object Unlock : Screen
    data object Browse : Screen
    data class EntryDetail(val item: BrowseIndexItem) : Screen
    data class AddEdit(val entryId: String?, val initial: EntryDraft?) : Screen
}

@Composable
fun VaultApp(repository: VaultRepository) {
    NoTrustVaultTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            val scope = rememberCoroutineScope()
            var screen by remember { mutableStateOf<Screen>(Screen.Loading) }
            var vaultFile by remember { mutableStateOf<VaultFile?>(null) }
            var session by remember { mutableStateOf<VaultSession?>(null) }

            LaunchedEffect(Unit) {
                screen = if (repository.exists()) Screen.Unlock else Screen.CreateVault
            }

            // Auto-lock: leaving the app wipes the browse session and
            // drops back to Unlock. Per docs/SECURITY.md this session
            // never held secrets-tier material anyway, so there's nothing
            // extra to wipe there — only the browse index.
            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_STOP) {
                        session?.lock()
                        session = null
                        if (screen is Screen.Browse || screen is Screen.EntryDetail || screen is Screen.AddEdit) {
                            screen = Screen.Unlock
                        }
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            when (val s = screen) {
                Screen.Loading -> LoadingIndicator()

                Screen.CreateVault -> {
                    var working by remember { mutableStateOf(false) }
                    var error by remember { mutableStateOf<String?>(null) }
                    CreateVaultScreen(working, error) { password ->
                        scope.launch {
                            working = true
                            error = null
                            try {
                                val file = repository.createVault(password)
                                vaultFile = file
                                session = repository.unlock(file, password)
                                screen = Screen.Browse
                            } catch (e: IllegalArgumentException) {
                                error = e.message
                            } catch (e: Exception) {
                                error = "Could not create the vault: ${e.message}"
                            } finally {
                                working = false
                            }
                        }
                    }
                }

                Screen.Unlock -> {
                    var working by remember { mutableStateOf(false) }
                    var error by remember { mutableStateOf<String?>(null) }
                    UnlockScreen(working, error) { password ->
                        scope.launch {
                            working = true
                            error = null
                            try {
                                val file = vaultFile ?: repository.load().also { vaultFile = it }
                                session = repository.unlock(file, password)
                                screen = Screen.Browse
                            } catch (e: VaultDecryptionFailed) {
                                error = "Wrong master password."
                            } catch (e: Exception) {
                                error = "Could not unlock the vault: ${e.message}"
                            } finally {
                                working = false
                            }
                        }
                    }
                }

                Screen.Browse -> {
                    val currentSession = checkNotNull(session) { "Browse reached with no unlocked session" }
                    var query by remember { mutableStateOf("") }
                    BrowseScreen(
                        items = currentSession.search(query),
                        query = query,
                        onQueryChange = { query = it },
                        onItemClick = { screen = Screen.EntryDetail(it) },
                        onAddClick = { screen = Screen.AddEdit(entryId = null, initial = null) }
                    )
                }

                is Screen.EntryDetail -> {
                    val currentSession = checkNotNull(session) { "EntryDetail reached with no unlocked session" }
                    EntryDetailRoute(
                        session = currentSession,
                        repository = repository,
                        item = s.item,
                        scope = scope,
                        onBack = { screen = Screen.Browse },
                        onEdit = { secrets ->
                            screen = Screen.AddEdit(
                                entryId = s.item.id,
                                initial = EntryDraft(s.item.alias, s.item.siteName, secrets.username, secrets.password, secrets.notes)
                            )
                        },
                        onDeleted = { screen = Screen.Browse }
                    )
                }

                is Screen.AddEdit -> {
                    val currentSession = checkNotNull(session) { "AddEdit reached with no unlocked session" }
                    var isSaving by remember { mutableStateOf(false) }
                    var error by remember { mutableStateOf<String?>(null) }
                    AddEditEntryScreen(
                        initial = s.initial,
                        isSaving = isSaving,
                        errorMessage = error,
                        onSave = { draft, masterPassword ->
                            scope.launch {
                                isSaving = true
                                error = null
                                try {
                                    withContext(Dispatchers.Default) {
                                        currentSession.upsertSecret(masterPassword, s.entryId, draft.alias, draft.siteName, draft.toSecrets())
                                    }
                                    repository.save(currentSession)
                                    screen = Screen.Browse
                                } catch (e: VaultDecryptionFailed) {
                                    error = "Wrong master password."
                                } catch (e: Exception) {
                                    error = "Could not save this entry: ${e.message}"
                                } finally {
                                    isSaving = false
                                }
                            }
                        },
                        onBack = { screen = Screen.Browse }
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadingIndicator() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EntryDetailRoute(
    session: VaultSession,
    repository: VaultRepository,
    item: BrowseIndexItem,
    scope: CoroutineScope,
    onBack: () -> Unit,
    onEdit: (EntrySecrets) -> Unit,
    onDeleted: () -> Unit
) {
    val context = LocalContext.current
    var revealed by remember(item.id) { mutableStateOf<EntrySecrets?>(null) }
    var isRevealing by remember(item.id) { mutableStateOf(false) }
    var error by remember(item.id) { mutableStateOf<String?>(null) }
    var remainingSeconds by remember(item.id) { mutableStateOf<Int?>(null) }

    // Auto-redact: once revealed, count down and clear — never leave
    // secrets on screen indefinitely. See docs/SECURITY.md.
    LaunchedEffect(revealed) {
        if (revealed != null) {
            var secondsLeft = REVEAL_DISPLAY_SECONDS
            remainingSeconds = secondsLeft
            while (secondsLeft > 0) {
                delay(1000)
                secondsLeft -= 1
                remainingSeconds = secondsLeft
            }
            revealed = null
            remainingSeconds = null
        }
    }

    EntryDetailScreen(
        item = item,
        revealed = revealed,
        isRevealing = isRevealing,
        revealError = error,
        remainingRevealSeconds = remainingSeconds,
        onRevealRequest = { password ->
            scope.launch {
                isRevealing = true
                error = null
                try {
                    revealed = withContext(Dispatchers.Default) { session.reveal(password, item.id) }
                } catch (e: VaultDecryptionFailed) {
                    error = "Wrong master password."
                } catch (e: Exception) {
                    error = "Could not reveal this entry: ${e.message}"
                } finally {
                    isRevealing = false
                }
            }
        },
        onCopyUsername = { copyThenAutoClear(context, scope, "username", it) },
        onCopyPassword = { copyThenAutoClear(context, scope, "password", it) },
        onEdit = onEdit,
        onDelete = {
            session.deleteEntry(item.id)
            scope.launch { repository.save(session) }
            onDeleted()
        },
        onBack = onBack
    )
}
