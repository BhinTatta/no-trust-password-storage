package com.notrust.vault.android

import com.notrust.vault.crypto.VaultCrypto
import com.notrust.vault.vault.VaultFile
import com.notrust.vault.vault.VaultSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Owns the one vault file on disk. Every write goes through a temp file
 * plus atomic rename — never edited in place — so a crash mid-write can't
 * corrupt the only local copy (see docs/TESTING.md). KDF/AEAD calls are
 * CPU-bound and deliberately slow (that's the point of Argon2id), so they
 * run on Dispatchers.Default, never the caller's thread.
 */
class VaultRepository(filesDir: File) {
    private val vaultFile = File(filesDir, "vault.json")

    suspend fun exists(): Boolean = withContext(Dispatchers.IO) { vaultFile.exists() }

    suspend fun load(): VaultFile = withContext(Dispatchers.IO) {
        VaultFile.fromJson(vaultFile.readText())
    }

    private suspend fun persist(file: VaultFile) = withContext(Dispatchers.IO) {
        val tmp = File(vaultFile.parentFile, "${vaultFile.name}.tmp")
        tmp.writeText(file.toJson())
        if (!tmp.renameTo(vaultFile)) {
            error("Failed to save vault: could not replace ${vaultFile.path}")
        }
    }

    suspend fun createVault(masterPassword: String): VaultFile = withContext(Dispatchers.Default) {
        VaultCrypto.ensureInitialized()
        val file = VaultFile.createNew(masterPassword)
        persist(file)
        file
    }

    suspend fun unlock(file: VaultFile, masterPassword: String): VaultSession = withContext(Dispatchers.Default) {
        VaultCrypto.ensureInitialized()
        VaultSession.unlock(file, masterPassword)
    }

    /** Persists whatever the session's current state is (call after any mutation). */
    suspend fun save(session: VaultSession) {
        persist(session.currentFile)
    }
}
