package com.notrust.vault.android

import com.notrust.vault.crypto.UnlockThrottle
import com.notrust.vault.crypto.VaultCrypto
import com.notrust.vault.vault.VaultFile
import com.notrust.vault.vault.VaultSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

enum class VaultKind { REAL, DECOY }

@Serializable
private data class ThrottleState(val consecutiveFailures: Int = 0, val lastAttemptEpochMillis: Long = 0)

/**
 * Owns the vault file(s) on disk — the real vault, and an optional decoy
 * vault (see docs/SECURITY.md, "Decoy / duress password"). Every write
 * goes through a temp file plus atomic rename — never edited in place —
 * so a crash mid-write can't corrupt the only local copy (docs/TESTING.md).
 * KDF/AEAD calls are CPU-bound and deliberately slow (that's the point of
 * Argon2id), so they run on Dispatchers.Default, never the caller's thread.
 *
 * The unlock-attempt throttle (see UnlockThrottle) is tracked here, in its
 * own small plaintext local file. It's not secret, and it must never ride
 * along inside the synced vault file: it's per-device local policy, not
 * vault content, and Phase 3's Drive sync should never touch it.
 */
class VaultRepository(private val filesDir: File) {
    private val realFile = File(filesDir, "vault.json")
    private val decoyFile = File(filesDir, "vault_decoy.json")
    private val throttleFile = File(filesDir, "unlock_throttle.json")
    private val biometricWrappedDekFile = File(filesDir, "browse_dek.bio")

    private fun fileFor(kind: VaultKind) = if (kind == VaultKind.REAL) realFile else decoyFile

    suspend fun exists(kind: VaultKind = VaultKind.REAL): Boolean =
        withContext(Dispatchers.IO) { fileFor(kind).exists() }

    suspend fun load(kind: VaultKind = VaultKind.REAL): VaultFile = withContext(Dispatchers.IO) {
        VaultFile.fromJson(fileFor(kind).readText())
    }

    private suspend fun persist(kind: VaultKind, file: VaultFile) = withContext(Dispatchers.IO) {
        val target = fileFor(kind)
        val tmp = File(target.parentFile, "${target.name}.tmp")
        tmp.writeText(file.toJson())
        if (!tmp.renameTo(target)) {
            error("Failed to save vault: could not replace ${target.path}")
        }
    }

    suspend fun createVault(masterPassword: String, kind: VaultKind = VaultKind.REAL): VaultFile =
        withContext(Dispatchers.Default) {
            VaultCrypto.ensureInitialized()
            val file = VaultFile.createNew(masterPassword)
            persist(kind, file)
            file
        }

    suspend fun unlock(file: VaultFile, masterPassword: String): VaultSession = withContext(Dispatchers.Default) {
        VaultCrypto.ensureInitialized()
        VaultSession.unlock(file, masterPassword)
    }

    /** The biometric-only unlock path — see [VaultSession.fromBrowseDek]. Same init requirement as [unlock]. */
    suspend fun unlockWithBrowseDek(file: VaultFile, browseDek: ByteArray): VaultSession = withContext(Dispatchers.Default) {
        VaultCrypto.ensureInitialized()
        VaultSession.fromBrowseDek(file, browseDek)
    }

    /** Persists whatever the session's current state is (call after any mutation). */
    suspend fun save(session: VaultSession, kind: VaultKind = VaultKind.REAL) {
        persist(kind, session.currentFile)
    }

    // --- Unlock throttle: see docs/SECURITY.md, "Rate-limited unlock" ---

    private suspend fun loadThrottle(): ThrottleState = withContext(Dispatchers.IO) {
        if (!throttleFile.exists()) return@withContext ThrottleState()
        // A corrupt/unreadable throttle file fails open (reset to zero
        // failures) rather than crashing the app — unlike the vault
        // itself, this is a non-secret convenience counter, not something
        // that needs to fail loud.
        runCatching { Json.decodeFromString(ThrottleState.serializer(), throttleFile.readText()) }
            .getOrDefault(ThrottleState())
    }

    private suspend fun saveThrottle(state: ThrottleState) = withContext(Dispatchers.IO) {
        val tmp = File(filesDir, "${throttleFile.name}.tmp")
        tmp.writeText(Json.encodeToString(ThrottleState.serializer(), state))
        tmp.renameTo(throttleFile)
    }

    /** Milliseconds the caller must still wait before the next unlock attempt is allowed. */
    suspend fun requiredWaitMillis(): Long {
        val state = loadThrottle()
        val totalDelay = UnlockThrottle.delayMillisAfter(state.consecutiveFailures)
        val elapsed = System.currentTimeMillis() - state.lastAttemptEpochMillis
        return (totalDelay - elapsed).coerceAtLeast(0)
    }

    /** Call after every unlock attempt, success or failure, to update the throttle. */
    suspend fun recordUnlockAttempt(succeeded: Boolean) {
        val state = loadThrottle()
        saveThrottle(
            if (succeeded) {
                ThrottleState()
            } else {
                state.copy(
                    consecutiveFailures = state.consecutiveFailures + 1,
                    lastAttemptEpochMillis = System.currentTimeMillis()
                )
            }
        )
    }

    // --- Biometric browse-tier unlock (see docs/SECURITY.md) ---
    //
    // This file holds only ciphertext: the browse DEK wrapped by a key that
    // lives in the Android Keystore and requires a live biometric match to
    // use (see AndroidBiometricKeyStore). It's meaningless without that
    // key, so it needs no extra encryption layer of its own — plain file
    // storage is fine, same reasoning as the vault files themselves.

    suspend fun biometricUnlockEnabled(): Boolean =
        withContext(Dispatchers.IO) { biometricWrappedDekFile.exists() }

    suspend fun saveBiometricWrappedBrowseDek(wrapped: ByteArray) = withContext(Dispatchers.IO) {
        val tmp = File(filesDir, "${biometricWrappedDekFile.name}.tmp")
        tmp.writeBytes(wrapped)
        if (!tmp.renameTo(biometricWrappedDekFile)) {
            error("Failed to save biometric key material: could not replace ${biometricWrappedDekFile.path}")
        }
    }

    suspend fun loadBiometricWrappedBrowseDek(): ByteArray =
        withContext(Dispatchers.IO) { biometricWrappedDekFile.readBytes() }

    suspend fun clearBiometricUnlock() = withContext(Dispatchers.IO) {
        biometricWrappedDekFile.delete()
        Unit
    }
}
