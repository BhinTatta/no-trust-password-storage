package com.notrust.vault.android.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.lifecycle.compose.currentStateAsState
import com.notrust.vault.android.BiometricPromptException
import com.notrust.vault.android.DeviceIntegrity
import com.notrust.vault.android.VaultKind
import com.notrust.vault.android.VaultRepository
import com.notrust.vault.android.copyThenAutoClear
import com.notrust.vault.android.ui.screens.AddEditEntryScreen
import com.notrust.vault.android.ui.screens.BrowseScreen
import com.notrust.vault.android.ui.screens.CreateVaultScreen
import com.notrust.vault.android.ui.screens.EntryDetailScreen
import com.notrust.vault.android.ui.screens.EntryDraft
import com.notrust.vault.android.ui.screens.ImportExportScreen
import com.notrust.vault.android.ui.screens.ProfileScreen
import com.notrust.vault.android.ui.screens.SettingsScreen
import com.notrust.vault.android.ui.screens.UnlockScreen
import com.notrust.vault.android.ui.theme.AccentOption
import com.notrust.vault.android.ui.theme.NoTrustVaultTheme
import com.notrust.vault.android.ui.theme.VaultColors
import com.notrust.vault.crypto.VaultDecryptionFailed
import com.notrust.vault.model.BrowseIndexItem
import com.notrust.vault.model.EntrySecrets
import com.notrust.vault.vault.BiometricKeyStore
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
    data object Settings : Screen
    data object Profile : Screen
    data object ImportExport : Screen
}

@Composable
fun VaultApp(repository: VaultRepository, biometricKeyStore: BiometricKeyStore) {
    NoTrustVaultTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            val scope = rememberCoroutineScope()
            var screen by remember { mutableStateOf<Screen>(Screen.Loading) }
            var session by remember { mutableStateOf<VaultSession?>(null) }
            // Which on-disk file `session` currently belongs to — real or
            // decoy (see docs/SECURITY.md). Every save must go back to the
            // *same* file the session came from, or a decoy-mode edit would
            // silently overwrite the real vault.
            var vaultKind by remember { mutableStateOf(VaultKind.REAL) }

            var integrityWarning by remember { mutableStateOf(false) }
            var biometricAvailable by remember { mutableStateOf(false) }
            var biometricEnabled by remember { mutableStateOf(false) }
            var decoyConfigured by remember { mutableStateOf(false) }
            var currentAccent by remember { mutableStateOf(AccentOption.Default) }
            // Consumed by the very next ON_STOP only — set right before we
            // ourselves hand off to a system file picker or share sheet, so
            // that expected transition doesn't trigger the auto-lock below.
            var suppressAutoLockOnce by remember { mutableStateOf(false) }

            LaunchedEffect(Unit) {
                integrityWarning = DeviceIntegrity.looksCompromised()
                biometricAvailable = biometricKeyStore.isAvailable()
                biometricEnabled = repository.biometricUnlockEnabled()
                decoyConfigured = repository.exists(VaultKind.DECOY)
                currentAccent = AccentOption.fromId(repository.loadAccentColorId())
                VaultColors.applyAccent(currentAccent)
                screen = if (repository.exists()) Screen.Unlock else Screen.CreateVault
            }

            // Auto-lock: leaving the app wipes the browse session and
            // drops back to Unlock. This never held secrets-tier material
            // anyway (docs/SECURITY.md) — only the browse index.
            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_STOP) {
                        if (suppressAutoLockOnce) {
                            suppressAutoLockOnce = false
                        } else {
                            session?.lock()
                            session = null
                            if (screen is Screen.Browse || screen is Screen.EntryDetail || screen is Screen.AddEdit || screen is Screen.Settings || screen is Screen.Profile || screen is Screen.ImportExport) {
                                screen = Screen.Unlock
                            }
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
                                session = repository.unlock(file, password)
                                vaultKind = VaultKind.REAL
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
                    // Deliberately two separate flags, not one shared
                    // "isWorking": if the biometric prompt ever hangs (it's
                    // the one thing here that's never been verified on a
                    // real device — see AndroidBiometricKeyStore's class
                    // doc), that must never disable the master-password
                    // button too. The password path has to work no matter
                    // what biometrics are doing.
                    var working by remember { mutableStateOf(false) }
                    var biometricWorking by remember { mutableStateOf(false) }
                    var error by remember { mutableStateOf<String?>(null) }
                    var throttleSeconds by remember { mutableStateOf(0) }

                    LaunchedEffect(screen) {
                        while (true) {
                            val waitMillis = repository.requiredWaitMillis()
                            throttleSeconds = ((waitMillis + 999) / 1000).toInt()
                            if (waitMillis <= 0) break
                            delay(1000)
                        }
                    }

                    suspend fun runBiometricUnlock(auto: Boolean) {
                        biometricWorking = true
                        error = null
                        try {
                            val wrapped = repository.loadBiometricWrappedBrowseDek()
                            val browseDek = biometricKeyStore.unwrap(wrapped)
                            // Always read fresh from disk here rather than reusing a cached
                            // VaultFile — a stale in-memory copy is exactly how a save made
                            // earlier in this same app session used to silently "disappear"
                            // on the next unlock (it was still on disk; a cached pre-edit
                            // snapshot was what got shown).
                            val realFile = repository.load()
                            session = repository.unlockWithBrowseDek(realFile, browseDek)
                            vaultKind = VaultKind.REAL
                            screen = Screen.Browse
                        } catch (e: IllegalStateException) {
                            // Biometric key invalidated (new enrollment) —
                            // AndroidBiometricKeyStore already dropped it.
                            biometricEnabled = false
                            error = e.message
                        } catch (e: BiometricPromptException) {
                            // A user backing out of an auto-triggered prompt (to type
                            // the master password instead) is not an error worth a
                            // red banner — only surface it when they tapped the button.
                            if (!e.isUserCancellation) {
                                error = "Biometric unlock failed: ${e.message}"
                            } else if (!auto) {
                                error = null
                            }
                        } catch (e: Exception) {
                            error = "Biometric unlock failed: ${e.message}"
                        } finally {
                            biometricWorking = false
                        }
                    }

                    // Auto-trigger once per Unlock-screen visit, but only once
                    // the activity is actually resumed. Firing this the instant
                    // `screen` flips to Unlock (which happens on ON_STOP, i.e.
                    // while the app is backgrounding) calls
                    // BiometricPrompt.authenticate() during a transitional
                    // lifecycle state — on some devices that silently never
                    // calls back at all, which used to leave biometricWorking
                    // (previously the same flag the password button used)
                    // stuck true forever. Gating on RESUMED avoids calling it
                    // at that bad time in the first place.
                    val lifecycleState by lifecycleOwner.lifecycle.currentStateAsState()
                    LaunchedEffect(screen, biometricEnabled, lifecycleState) {
                        if (biometricEnabled && lifecycleState == Lifecycle.State.RESUMED) {
                            runBiometricUnlock(auto = true)
                        }
                    }

                    UnlockScreen(
                        isWorking = working,
                        errorMessage = error,
                        throttleSecondsRemaining = throttleSeconds,
                        biometricAvailable = biometricEnabled,
                        isBiometricWorking = biometricWorking,
                        onUnlock = { password ->
                            scope.launch {
                                working = true
                                error = null
                                try {
                                    // Always read fresh from disk here rather than reusing a cached
                                    // VaultFile — a stale in-memory copy is exactly how a save made
                                    // earlier in this same app session used to silently "disappear"
                                    // on the next unlock (it was still on disk; a cached pre-edit
                                    // snapshot was what got shown).
                                    val realFile = repository.load()
                                    try {
                                        session = repository.unlock(realFile, password)
                                        vaultKind = VaultKind.REAL
                                        repository.recordUnlockAttempt(true)
                                        screen = Screen.Browse
                                    } catch (e: VaultDecryptionFailed) {
                                        if (repository.exists(VaultKind.DECOY)) {
                                            val decoyFile = repository.load(VaultKind.DECOY)
                                            session = repository.unlock(decoyFile, password)
                                            vaultKind = VaultKind.DECOY
                                            repository.recordUnlockAttempt(true)
                                            screen = Screen.Browse
                                        } else {
                                            throw e
                                        }
                                    }
                                } catch (e: VaultDecryptionFailed) {
                                    repository.recordUnlockAttempt(false)
                                    error = "Wrong master password."
                                } catch (e: Exception) {
                                    error = "Could not unlock the vault: ${e.message}"
                                } finally {
                                    working = false
                                }
                            }
                        },
                        onBiometricUnlock = {
                            scope.launch { runBiometricUnlock(auto = false) }
                        }
                    )
                }

                Screen.Browse -> {
                    val currentSession = checkNotNull(session) { "Browse reached with no unlocked session" }
                    var query by remember { mutableStateOf("") }
                    var selectedTag by remember { mutableStateOf<String?>(null) }
                    val baseItems = currentSession.search(query)
                    val displayedItems = selectedTag?.let { tag -> baseItems.filter { tag in it.tags } } ?: baseItems
                    BrowseScreen(
                        items = displayedItems,
                        query = query,
                        onQueryChange = { query = it },
                        allTags = currentSession.allTags(),
                        selectedTag = selectedTag,
                        onTagSelected = { selectedTag = it },
                        onItemClick = { screen = Screen.EntryDetail(it) },
                        onAddClick = { screen = Screen.AddEdit(entryId = null, initial = null) },
                        integrityWarning = integrityWarning,
                        bottomBar = { VaultBottomNavBar(current = screen, onNavigate = { screen = it }) }
                    )
                }

                Screen.Profile -> {
                    val currentSession = checkNotNull(session) { "Profile reached with no unlocked session" }
                    val items = currentSession.list()
                    val tagCounts = items.flatMap { it.tags }.groupingBy { it }.eachCount()
                        .toList().sortedByDescending { it.second }
                    ProfileScreen(
                        entryCount = items.size,
                        tagCounts = tagCounts,
                        biometricEnabled = biometricEnabled,
                        decoyConfigured = decoyConfigured,
                        bottomBar = { VaultBottomNavBar(current = screen, onNavigate = { screen = it }) }
                    )
                }

                is Screen.EntryDetail -> {
                    val currentSession = checkNotNull(session) { "EntryDetail reached with no unlocked session" }
                    EntryDetailRoute(
                        session = currentSession,
                        repository = repository,
                        vaultKind = vaultKind,
                        item = s.item,
                        scope = scope,
                        onBack = { screen = Screen.Browse },
                        onEdit = { secrets ->
                            screen = Screen.AddEdit(
                                entryId = s.item.id,
                                initial = EntryDraft(s.item.alias, s.item.siteName, secrets.username, secrets.password, secrets.notes, s.item.tags, s.item.iconOverride, secrets.totpSeed)
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
                                        currentSession.upsertSecret(masterPassword, s.entryId, draft.alias, draft.siteName, draft.toSecrets(), draft.tags, draft.iconOverride)
                                    }
                                    repository.save(currentSession, vaultKind)
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

                Screen.Settings -> {
                    val currentSession = checkNotNull(session) { "Settings reached with no unlocked session" }
                    var decoyError by remember { mutableStateOf<String?>(null) }
                    SettingsScreen(
                        biometricAvailable = biometricAvailable,
                        biometricEnabled = biometricEnabled,
                        onToggleBiometric = { enable ->
                            scope.launch {
                                try {
                                    if (enable) {
                                        val dek = currentSession.exportBrowseDekForBiometricSetup()
                                        val wrapped = biometricKeyStore.wrap(dek)
                                        repository.saveBiometricWrappedBrowseDek(wrapped)
                                    } else {
                                        repository.clearBiometricUnlock()
                                        biometricKeyStore.invalidate()
                                    }
                                    biometricEnabled = enable
                                } catch (e: Exception) {
                                    // Biometric setup is best-effort convenience,
                                    // not the vault's real security boundary —
                                    // leave the toggle as it was and let the
                                    // user retry rather than crash the screen.
                                    biometricEnabled = repository.biometricUnlockEnabled()
                                }
                            }
                        },
                        decoyConfigured = decoyConfigured,
                        decoyError = decoyError,
                        onSetupDecoy = { decoyPassword ->
                            scope.launch {
                                decoyError = null
                                try {
                                    // Always read fresh from disk here rather than reusing a cached
                                    // VaultFile — a stale in-memory copy is exactly how a save made
                                    // earlier in this same app session used to silently "disappear"
                                    // on the next unlock (it was still on disk; a cached pre-edit
                                    // snapshot was what got shown).
                                    val realFile = repository.load()
                                    val sameAsReal = withContext(Dispatchers.Default) {
                                        try {
                                            VaultSession.unlock(realFile, decoyPassword).also { it.lock() }
                                            true
                                        } catch (e: VaultDecryptionFailed) {
                                            false
                                        }
                                    }
                                    if (sameAsReal) {
                                        decoyError = "That's your real master password — the decoy password must be different."
                                    } else {
                                        repository.createVault(decoyPassword, VaultKind.DECOY)
                                        decoyConfigured = true
                                    }
                                } catch (e: IllegalArgumentException) {
                                    decoyError = e.message
                                } catch (e: Exception) {
                                    decoyError = "Could not set up the decoy vault: ${e.message}"
                                }
                            }
                        },
                        onImportExportClick = { screen = Screen.ImportExport },
                        currentAccent = currentAccent,
                        onAccentSelected = { option ->
                            currentAccent = option
                            VaultColors.applyAccent(option)
                            scope.launch { repository.saveAccentColorId(option.id) }
                        },
                        bottomBar = { VaultBottomNavBar(current = screen, onNavigate = { screen = it }) }
                    )
                }

                Screen.ImportExport -> {
                    val currentSession = checkNotNull(session) { "ImportExport reached with no unlocked session" }
                    var working by remember { mutableStateOf(false) }
                    var status by remember { mutableStateOf<String?>(null) }
                    var statusIsError by remember { mutableStateOf(false) }

                    ImportExportScreen(
                        isWorking = working,
                        statusMessage = status,
                        statusIsError = statusIsError,
                        onExportRequested = {
                            repository.exportBytes(vaultKind)
                        },
                        onExternalActivityStarting = { suppressAutoLockOnce = true },
                        onImportConfirmed = { bytes, masterPassword ->
                            scope.launch {
                                working = true
                                status = null
                                statusIsError = false
                                try {
                                    val candidate = repository.parseImportedVault(bytes)
                                    // Confirming the password decrypts it before touching
                                    // disk is what makes this safe to call "restore" rather
                                    // than "gamble the only copy of your vault" — a bad file
                                    // or wrong password never gets the chance to overwrite
                                    // the working vault.
                                    val restoredSession = repository.unlock(candidate, masterPassword)
                                    repository.replaceVault(candidate, VaultKind.REAL)
                                    currentSession.lock()
                                    session = restoredSession
                                    vaultKind = VaultKind.REAL
                                    status = "Vault restored."
                                    screen = Screen.Browse
                                } catch (e: VaultDecryptionFailed) {
                                    statusIsError = true
                                    status = "Wrong master password for this file."
                                } catch (e: Exception) {
                                    statusIsError = true
                                    status = "Not a valid vault export file."
                                } finally {
                                    working = false
                                }
                            }
                        },
                        onBack = { screen = Screen.Settings }
                    )
                }
            }
        }
    }
}

/**
 * The three peer destinations reachable at any time once unlocked — Vault,
 * Status, Settings — replacing what used to be separate top-bar icon taps.
 * A persistent bottom tab bar (rather than pushed screens reached from
 * scattered icons) is the standard pattern premium mobile apps use for
 * top-level navigation; EntryDetail/AddEdit/ImportExport stay as pushed
 * flows on top of it, each with their own back arrow, since those aren't
 * peer destinations.
 */
@Composable
private fun VaultBottomNavBar(current: Screen, onNavigate: (Screen) -> Unit) {
    val itemColors = NavigationBarItemDefaults.colors(
        selectedIconColor = VaultColors.Signal,
        selectedTextColor = VaultColors.Signal,
        indicatorColor = VaultColors.SurfaceRaised,
        unselectedIconColor = VaultColors.TextMuted,
        unselectedTextColor = VaultColors.TextMuted
    )
    NavigationBar(containerColor = VaultColors.Surface) {
        NavigationBarItem(
            selected = current is Screen.Browse,
            onClick = { onNavigate(Screen.Browse) },
            icon = { Icon(Icons.Default.Lock, contentDescription = null) },
            label = { Text("Vault") },
            colors = itemColors
        )
        NavigationBarItem(
            selected = current is Screen.Profile,
            onClick = { onNavigate(Screen.Profile) },
            icon = { Icon(Icons.Default.Shield, contentDescription = null) },
            label = { Text("Status") },
            colors = itemColors
        )
        NavigationBarItem(
            selected = current is Screen.Settings,
            onClick = { onNavigate(Screen.Settings) },
            icon = { Icon(Icons.Default.Settings, contentDescription = null) },
            label = { Text("Settings") },
            colors = itemColors
        )
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
    vaultKind: VaultKind,
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
        onCopyTotpCode = { copyThenAutoClear(context, scope, "TOTP code", it) },
        onEdit = onEdit,
        onDelete = {
            session.deleteEntry(item.id)
            scope.launch { repository.save(session, vaultKind) }
            onDeleted()
        },
        onBack = onBack
    )
}
