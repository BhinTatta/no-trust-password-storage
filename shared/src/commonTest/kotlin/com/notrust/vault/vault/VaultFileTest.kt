package com.notrust.vault.vault

import com.notrust.vault.crypto.VaultCrypto
import com.notrust.vault.crypto.VaultDecryptionFailed
import com.notrust.vault.model.EntrySecrets
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VaultFileTest {

    private val masterPassword = "correct horse battery staple!"

    @Test
    fun createNew_producesAnEmptyUnlockableVault() = runTest {
        VaultCrypto.ensureInitialized()
        val file = VaultFile.createNew(masterPassword)
        val session = VaultSession.unlock(file, masterPassword)
        assertTrue(session.list().isEmpty())
    }

    @Test
    fun unlock_failsWithWrongMasterPassword() = runTest {
        VaultCrypto.ensureInitialized()
        val file = VaultFile.createNew(masterPassword)
        assertFailsWith<VaultDecryptionFailed> {
            VaultSession.unlock(file, "totally the wrong password!")
        }
    }

    @Test
    fun upsertAndReveal_roundTrips() = runTest {
        VaultCrypto.ensureInitialized()
        val file = VaultFile.createNew(masterPassword)
        val session = VaultSession.unlock(file, masterPassword)

        val secrets = EntrySecrets(username = "kartik@example.com", password = "hunter2!!", notes = "personal email")
        session.upsertSecret(masterPassword, entryId = null, alias = "My Email", siteName = "Gmail", secrets = secrets)

        val items = session.list()
        assertEquals(1, items.size)
        assertEquals("My Email", items[0].alias)
        assertEquals("Gmail", items[0].siteName)

        val revealed = session.reveal(masterPassword, items[0].id)
        assertEquals(secrets, revealed)
    }

    @Test
    fun reveal_failsWithWrongMasterPassword() = runTest {
        VaultCrypto.ensureInitialized()
        val file = VaultFile.createNew(masterPassword)
        val session = VaultSession.unlock(file, masterPassword)
        session.upsertSecret(masterPassword, null, "alias", "site", EntrySecrets("u", "p"))
        val id = session.list().single().id

        assertFailsWith<VaultDecryptionFailed> {
            session.reveal("wrong password entirely!", id)
        }
    }

    @Test
    fun search_matchesAliasOrSiteName_caseInsensitive() = runTest {
        VaultCrypto.ensureInitialized()
        val file = VaultFile.createNew(masterPassword)
        val session = VaultSession.unlock(file, masterPassword)
        session.upsertSecret(masterPassword, null, "Mom's Netflix", "netflix.com", EntrySecrets("u1", "p1"))
        session.upsertSecret(masterPassword, null, "Work VPN", "vpn.corp.example", EntrySecrets("u2", "p2"))

        assertEquals(1, session.search("netflix").size)
        assertEquals(1, session.search("MOM'S").size)
        assertEquals(1, session.search("vpn").size)
        assertEquals(0, session.search("nonexistent").size)
        assertEquals(2, session.search("").size)
    }

    @Test
    fun renameItem_doesNotRequireMasterPassword() = runTest {
        VaultCrypto.ensureInitialized()
        val file = VaultFile.createNew(masterPassword)
        val session = VaultSession.unlock(file, masterPassword)
        session.upsertSecret(masterPassword, null, "old alias", "site.com", EntrySecrets("u", "p"))
        val id = session.list().single().id

        session.renameItem(id, "new alias", "site.com")

        assertEquals("new alias", session.list().single().alias)
        // secrets are untouched by a rename
        assertEquals(EntrySecrets("u", "p"), session.reveal(masterPassword, id))
    }

    @Test
    fun deleteEntry_doesNotRequireMasterPassword_andRemovesSecrets() = runTest {
        VaultCrypto.ensureInitialized()
        val file = VaultFile.createNew(masterPassword)
        val session = VaultSession.unlock(file, masterPassword)
        session.upsertSecret(masterPassword, null, "alias", "site", EntrySecrets("u", "p"))
        val id = session.list().single().id

        session.deleteEntry(id)

        assertTrue(session.list().isEmpty())
        assertTrue(session.currentFile.secretEntries.isEmpty())
    }

    @Test
    fun lock_wipesSessionAndRefusesFurtherUse() = runTest {
        VaultCrypto.ensureInitialized()
        val file = VaultFile.createNew(masterPassword)
        val session = VaultSession.unlock(file, masterPassword)
        session.lock()
        assertFailsWith<IllegalStateException> { session.list() }
    }

    /**
     * The core security property of the whole two-tier design: the browse
     * DEK (what a biometric unlock will eventually hand back) must be
     * cryptographically incapable of decrypting secrets-tier ciphertext,
     * and vice versa. This gets its own dedicated test rather than being
     * an implicit side effect of the happy-path tests above.
     */
    @Test
    fun browseDekAndSecretsDek_areIndependent_neitherDecryptsTheOther() = runTest {
        VaultCrypto.ensureInitialized()
        val file = VaultFile.createNew(masterPassword)
        val session = VaultSession.unlock(file, masterPassword)
        session.upsertSecret(masterPassword, null, "alias", "site", EntrySecrets("u", "p"))

        val masterKey = VaultCrypto.deriveKey(masterPassword, file.salt, file.kdf)
        val browseDek = VaultCrypto.unwrapKey(file.wrappedBrowseDek, masterKey)
        val secretsDek = VaultCrypto.unwrapKey(file.wrappedSecretsDek, masterKey)

        assertTrue(!browseDek.contentEquals(secretsDek), "the two DEKs must never be equal")

        val secretBox = session.currentFile.secretEntries.values.single()
        assertFailsWith<VaultDecryptionFailed> {
            VaultCrypto.decrypt(secretBox, browseDek)
        }
        assertFailsWith<VaultDecryptionFailed> {
            VaultCrypto.decrypt(session.currentFile.browseIndex, secretsDek)
        }
    }

    @Test
    fun vaultFile_survivesJsonRoundTrip() = runTest {
        VaultCrypto.ensureInitialized()
        val file = VaultFile.createNew(masterPassword)
        val session = VaultSession.unlock(file, masterPassword)
        session.upsertSecret(masterPassword, null, "alias", "site", EntrySecrets("u", "p", totpSeed = "JBSWY3DPEHPK3PXP"))

        val json = session.currentFile.toJson()
        val restored = VaultFile.fromJson(json)

        assertEquals(session.currentFile, restored)
        val restoredSession = VaultSession.unlock(restored, masterPassword)
        val id = restoredSession.list().single().id
        val revealed = restoredSession.reveal(masterPassword, id)
        assertEquals("JBSWY3DPEHPK3PXP", revealed.totpSeed)
    }

    @Test
    fun reveal_forUnknownEntryId_throws() = runTest {
        VaultCrypto.ensureInitialized()
        val file = VaultFile.createNew(masterPassword)
        val session = VaultSession.unlock(file, masterPassword)
        assertFailsWith<IllegalStateException> {
            session.reveal(masterPassword, "does-not-exist")
        }
    }

    // --- Biometric-only unlock path (fromBrowseDek) ---

    @Test
    fun fromBrowseDek_seesTheSameBrowseIndexAsMasterPasswordUnlock() = runTest {
        VaultCrypto.ensureInitialized()
        val file = VaultFile.createNew(masterPassword)
        val passwordSession = VaultSession.unlock(file, masterPassword)
        passwordSession.upsertSecret(masterPassword, null, "alias", "site", EntrySecrets("u", "p"))

        val exportedBrowseDek = passwordSession.exportBrowseDekForBiometricSetup()
        val biometricSession = VaultSession.fromBrowseDek(passwordSession.currentFile, exportedBrowseDek)

        assertEquals(passwordSession.list(), biometricSession.list())
    }

    @Test
    fun fromBrowseDek_stillRequiresMasterPasswordToReveal() = runTest {
        VaultCrypto.ensureInitialized()
        val file = VaultFile.createNew(masterPassword)
        val passwordSession = VaultSession.unlock(file, masterPassword)
        passwordSession.upsertSecret(masterPassword, null, "alias", "site", EntrySecrets("u", "p"))
        val exportedBrowseDek = passwordSession.exportBrowseDekForBiometricSetup()

        val biometricSession = VaultSession.fromBrowseDek(passwordSession.currentFile, exportedBrowseDek)
        val id = biometricSession.list().single().id

        // The whole point: reaching this session via the browse DEK alone
        // must not shortcut the master-password requirement for secrets.
        assertFailsWith<IllegalArgumentException> {
            biometricSession.reveal("", id) // empty password -> fails validation before even trying to decrypt
        }
        assertEquals(EntrySecrets("u", "p"), biometricSession.reveal(masterPassword, id))
    }

    @Test
    fun fromBrowseDek_withWrongDek_failsToDecryptTheIndex() = runTest {
        VaultCrypto.ensureInitialized()
        val file = VaultFile.createNew(masterPassword)
        assertFailsWith<VaultDecryptionFailed> {
            VaultSession.fromBrowseDek(file, VaultCrypto.generateDek())
        }
    }
}
