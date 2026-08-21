package com.notrust.vault.vault

import com.notrust.vault.crypto.ByteArrayBase64Serializer
import com.notrust.vault.crypto.EncryptedBox
import com.notrust.vault.crypto.KdfParams
import com.notrust.vault.crypto.VaultCrypto
import com.notrust.vault.model.BrowseIndexItem
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The single file that gets written to disk and synced to Drive.
 *
 * [salt] and [kdf] are plaintext metadata (not secret — Argon2id's
 * memory-hardness is the defense, not salt secrecy). Everything else is
 * ciphertext: [wrappedSecretsDek] and [wrappedBrowseDek] are the DEK,
 * wrapped by the Master Key derived from [salt]/[kdf]; [browseIndex] is
 * the alias/site-name list encrypted under the (unwrapped) browse DEK;
 * [secretEntries] holds one independently-encrypted blob per entry,
 * encrypted under the (unwrapped) secrets DEK, so revealing one entry
 * never requires decrypting any other.
 */
@Serializable
data class VaultFile(
    val formatVersion: Int = CURRENT_FORMAT_VERSION,
    @Serializable(with = ByteArrayBase64Serializer::class)
    val salt: ByteArray,
    val kdf: KdfParams = KdfParams(),
    val wrappedSecretsDek: EncryptedBox,
    val wrappedBrowseDek: EncryptedBox,
    val browseIndex: EncryptedBox,
    val secretEntries: Map<String, EncryptedBox> = emptyMap()
) {
    fun toJson(): String = json.encodeToString(serializer(), this)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VaultFile) return false
        return formatVersion == other.formatVersion &&
            salt.contentEquals(other.salt) &&
            kdf == other.kdf &&
            wrappedSecretsDek == other.wrappedSecretsDek &&
            wrappedBrowseDek == other.wrappedBrowseDek &&
            browseIndex == other.browseIndex &&
            secretEntries == other.secretEntries
    }

    override fun hashCode(): Int {
        var result = formatVersion
        result = 31 * result + salt.contentHashCode()
        result = 31 * result + kdf.hashCode()
        result = 31 * result + wrappedSecretsDek.hashCode()
        result = 31 * result + wrappedBrowseDek.hashCode()
        result = 31 * result + browseIndex.hashCode()
        result = 31 * result + secretEntries.hashCode()
        return result
    }

    companion object {
        const val CURRENT_FORMAT_VERSION = 1

        val json = Json { ignoreUnknownKeys = false; prettyPrint = false }

        fun fromJson(text: String): VaultFile = json.decodeFromString(serializer(), text)

        /**
         * Creates a brand-new, empty vault. [masterPassword] is validated
         * (see [VaultCrypto.validateMasterPassword]) and used once, here,
         * to wrap both DEKs — it is not retained afterward.
         */
        fun createNew(masterPassword: String, kdf: KdfParams = KdfParams()): VaultFile {
            VaultCrypto.validateMasterPassword(masterPassword)
            val salt = VaultCrypto.randomSalt()
            val masterKey = VaultCrypto.deriveKey(masterPassword, salt, kdf)
            try {
                val secretsDek = VaultCrypto.generateDek()
                val browseDek = VaultCrypto.generateDek()
                try {
                    val emptyIndexJson = Json.encodeToString(
                        kotlinx.serialization.builtins.ListSerializer(BrowseIndexItem.serializer()),
                        emptyList()
                    )
                    val browseIndexBox = VaultCrypto.encrypt(emptyIndexJson.encodeToByteArray(), browseDek)
                    return VaultFile(
                        salt = salt,
                        kdf = kdf,
                        wrappedSecretsDek = VaultCrypto.wrapKey(secretsDek, masterKey),
                        wrappedBrowseDek = VaultCrypto.wrapKey(browseDek, masterKey),
                        browseIndex = browseIndexBox,
                        secretEntries = emptyMap()
                    )
                } finally {
                    VaultCrypto.wipe(secretsDek)
                    VaultCrypto.wipe(browseDek)
                }
            } finally {
                VaultCrypto.wipe(masterKey)
            }
        }
    }
}
