package com.notrust.vault.vault

import com.notrust.vault.crypto.EncryptedBox
import com.notrust.vault.crypto.VaultCrypto
import com.notrust.vault.model.BrowseIndexItem
import com.notrust.vault.model.EntrySecrets
import com.notrust.vault.model.TotpEntry
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * A live, unlocked session over a [VaultFile].
 *
 * Two very different privilege levels live in one session, on purpose:
 *  - The **browse DEK** is derived once at [unlock] and kept in memory for
 *    the life of the session (this is what a biometric unlock will hand
 *    back, in Phase 2, instead of the master password). It's enough to
 *    list/search/rename/delete entries — never enough to read a secret.
 *  - The **secrets DEK** is *never* cached. [reveal] and [upsertSecret]
 *    each take the master password fresh, derive the key, use it once,
 *    and let it go out of scope. There is no session-wide "logged in to
 *    see secrets" state.
 *
 * Call [lock] when you're done — it wipes the browse DEK and the
 * in-memory index, and the session refuses further use afterward.
 */
class VaultSession private constructor(
    private var file: VaultFile,
    private var browseDek: ByteArray,
    private var browseIndex: MutableList<BrowseIndexItem>
) {
    private var locked = false

    private fun requireUnlocked() = check(!locked) { "This vault session has been locked; unlock() a new one." }

    val currentFile: VaultFile
        get() {
            requireUnlocked()
            return file
        }

    fun list(): List<BrowseIndexItem> {
        requireUnlocked()
        return browseIndex.toList()
    }

    /**
     * Exposes the raw browse DEK once, for the biometric setup flow only:
     * "enable biometric unlock" means wrapping this with a platform
     * `BiometricKeyStore.wrap` call so future app launches can reach
     * [fromBrowseDek] without the master password. Not used anywhere else
     * — this is the one deliberate escape hatch, named accordingly.
     */
    fun exportBrowseDekForBiometricSetup(): ByteArray {
        requireUnlocked()
        return browseDek.copyOf()
    }

    fun search(query: String): List<BrowseIndexItem> {
        requireUnlocked()
        if (query.isBlank()) return list()
        return browseIndex.filter {
            it.alias.contains(query, ignoreCase = true) ||
                it.siteName.contains(query, ignoreCase = true) ||
                it.tags.any { tag -> tag.contains(query, ignoreCase = true) }
        }
    }

    /** All distinct tags currently in use, for building a filter chip row. */
    fun allTags(): List<String> = browseIndex.flatMap { it.tags }.distinct().sorted()

    fun filterByTag(tag: String): List<BrowseIndexItem> {
        requireUnlocked()
        return browseIndex.filter { it.tags.contains(tag) }
    }

    /** Re-derives the master key fresh and decrypts exactly one entry's secrets. Never cached. */
    fun reveal(masterPassword: String, entryId: String): EntrySecrets {
        requireUnlocked()
        val box = file.secretEntries[entryId] ?: error("No such entry: $entryId")
        val masterKey = VaultCrypto.deriveKey(masterPassword, file.salt, file.kdf)
        try {
            val secretsDek = VaultCrypto.unwrapKey(file.wrappedSecretsDek, masterKey)
            try {
                val plaintext = VaultCrypto.decrypt(box, secretsDek)
                return Json.decodeFromString(EntrySecrets.serializer(), plaintext.decodeToString())
            } finally {
                VaultCrypto.wipe(secretsDek)
            }
        } finally {
            VaultCrypto.wipe(masterKey)
        }
    }

    /**
     * Creates or replaces an entry's secrets. Requires the master password
     * (encrypting a new secret is exactly as sensitive as revealing one).
     * The browse-index update (alias/site name) reuses the already-unlocked
     * browse DEK — no extra password prompt for that half of the write.
     */
    fun upsertSecret(
        masterPassword: String,
        entryId: String?,
        alias: String,
        siteName: String,
        secrets: EntrySecrets,
        tags: List<String> = emptyList(),
        iconOverride: String? = null
    ): VaultFile {
        requireUnlocked()
        val id = entryId ?: VaultCrypto.newId()
        val masterKey = VaultCrypto.deriveKey(masterPassword, file.salt, file.kdf)
        try {
            val secretsDek = VaultCrypto.unwrapKey(file.wrappedSecretsDek, masterKey)
            try {
                val plaintext = Json.encodeToString(EntrySecrets.serializer(), secrets).encodeToByteArray()
                val box = VaultCrypto.encrypt(plaintext, secretsDek)
                file = file.copy(secretEntries = file.secretEntries + (id to box))
            } finally {
                VaultCrypto.wipe(secretsDek)
            }
        } finally {
            VaultCrypto.wipe(masterKey)
        }
        upsertBrowseItem(BrowseIndexItem(id, alias, siteName, tags, iconOverride))
        return file
    }

    /** Renames/re-tags an entry's visible label only. Needs just the browse DEK — no master password. */
    fun renameItem(entryId: String, newAlias: String, newSiteName: String, newTags: List<String>? = null): VaultFile {
        requireUnlocked()
        val existing = browseIndex.firstOrNull { it.id == entryId } ?: error("No such entry: $entryId")
        upsertBrowseItem(BrowseIndexItem(entryId, newAlias, newSiteName, newTags ?: existing.tags, existing.iconOverride))
        return file
    }

    /** Deletes an entry entirely. Needs just the browse DEK — no master password, no decryption. */
    fun deleteEntry(entryId: String): VaultFile {
        requireUnlocked()
        browseIndex.removeAll { it.id == entryId }
        file = file.copy(secretEntries = file.secretEntries - entryId)
        persistBrowseIndex()
        return file
    }

    // --- Standalone authenticator (TOTP) entries ---
    //
    // A completely separate feature from password entries: one list, one
    // encrypted blob, browsed on its own screen. Same secrets DEK and same
    // "master password required, every time, never cached" rule as
    // [reveal]/[upsertSecret] above — a leaked seed generates valid codes
    // forever, so it gets no less protection than the password it backs.

    /** Decrypts and returns every saved authenticator entry. Empty if none have been added yet. */
    fun listTotpEntries(masterPassword: String): List<TotpEntry> {
        requireUnlocked()
        val masterKey = VaultCrypto.deriveKey(masterPassword, file.salt, file.kdf)
        try {
            val secretsDek = VaultCrypto.unwrapKey(file.wrappedSecretsDek, masterKey)
            try {
                return decodeTotpEntries(secretsDek)
            } finally {
                VaultCrypto.wipe(secretsDek)
            }
        } finally {
            VaultCrypto.wipe(masterKey)
        }
    }

    /** Creates a new authenticator entry, or replaces an existing one if [id] matches one already saved. */
    fun upsertTotpEntry(masterPassword: String, id: String?, alias: String?, seed: String): VaultFile {
        requireUnlocked()
        val entryId = id ?: VaultCrypto.newId()
        val masterKey = VaultCrypto.deriveKey(masterPassword, file.salt, file.kdf)
        try {
            val secretsDek = VaultCrypto.unwrapKey(file.wrappedSecretsDek, masterKey)
            try {
                val updated = decodeTotpEntries(secretsDek).filterNot { it.id == entryId } +
                    TotpEntry(entryId, alias, seed)
                file = file.copy(totpVault = encodeTotpEntries(updated, secretsDek))
            } finally {
                VaultCrypto.wipe(secretsDek)
            }
        } finally {
            VaultCrypto.wipe(masterKey)
        }
        return file
    }

    /** Deletes one authenticator entry. Still needs the master password — same tier as deleting/overwriting a password's secrets would. */
    fun deleteTotpEntry(masterPassword: String, id: String): VaultFile {
        requireUnlocked()
        val masterKey = VaultCrypto.deriveKey(masterPassword, file.salt, file.kdf)
        try {
            val secretsDek = VaultCrypto.unwrapKey(file.wrappedSecretsDek, masterKey)
            try {
                val updated = decodeTotpEntries(secretsDek).filterNot { it.id == id }
                file = file.copy(totpVault = encodeTotpEntries(updated, secretsDek))
            } finally {
                VaultCrypto.wipe(secretsDek)
            }
        } finally {
            VaultCrypto.wipe(masterKey)
        }
        return file
    }

    private fun decodeTotpEntries(secretsDek: ByteArray): List<TotpEntry> {
        val box = file.totpVault ?: return emptyList()
        val plaintext = VaultCrypto.decrypt(box, secretsDek)
        return Json.decodeFromString(ListSerializer(TotpEntry.serializer()), plaintext.decodeToString())
    }

    private fun encodeTotpEntries(entries: List<TotpEntry>, secretsDek: ByteArray): EncryptedBox {
        val plaintext = Json.encodeToString(ListSerializer(TotpEntry.serializer()), entries).encodeToByteArray()
        return VaultCrypto.encrypt(plaintext, secretsDek)
    }

    private fun upsertBrowseItem(item: BrowseIndexItem) {
        browseIndex.removeAll { it.id == item.id }
        browseIndex.add(item)
        persistBrowseIndex()
    }

    private fun persistBrowseIndex() {
        val plaintext = Json.encodeToString(ListSerializer(BrowseIndexItem.serializer()), browseIndex).encodeToByteArray()
        val box = VaultCrypto.encrypt(plaintext, browseDek)
        file = file.copy(browseIndex = box)
    }

    /** Wipes the in-memory browse DEK and index. The session is unusable after this. */
    fun lock() {
        if (locked) return
        VaultCrypto.wipe(browseDek)
        browseIndex.clear()
        locked = true
    }

    companion object {
        /**
         * Unlocks a [VaultFile] with the master password. Derives the
         * master key once, right here, to unwrap the browse DEK and
         * decrypt the browse index — then lets the master key go out of
         * scope. (Phase 2 adds a second entry point that instead takes an
         * already-unwrapped browse DEK from a platform biometric key
         * store, for the biometric-only unlock path.)
         */
        fun unlock(file: VaultFile, masterPassword: String): VaultSession {
            val masterKey = VaultCrypto.deriveKey(masterPassword, file.salt, file.kdf)
            try {
                val browseDek = VaultCrypto.unwrapKey(file.wrappedBrowseDek, masterKey)
                return fromBrowseDek(file, browseDek)
            } finally {
                VaultCrypto.wipe(masterKey)
            }
        }

        /**
         * The biometric-only unlock path: [browseDek] comes from a
         * platform `BiometricKeyStore.unwrap` call rather than the master
         * password. This can only ever reach the browse tier — [reveal]
         * and [upsertSecret] still always require the master password,
         * fresh, every time, regardless of how the session was unlocked.
         */
        fun fromBrowseDek(file: VaultFile, browseDek: ByteArray): VaultSession {
            val indexJson = VaultCrypto.decrypt(file.browseIndex, browseDek).decodeToString()
            val index = Json.decodeFromString(ListSerializer(BrowseIndexItem.serializer()), indexJson).toMutableList()
            return VaultSession(file, browseDek.copyOf(), index)
        }
    }
}
