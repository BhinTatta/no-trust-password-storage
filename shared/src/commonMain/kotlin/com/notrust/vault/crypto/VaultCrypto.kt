package com.notrust.vault.crypto

import com.ionspin.kotlin.crypto.LibsodiumInitializer
import com.ionspin.kotlin.crypto.pwhash.PasswordHash
import com.ionspin.kotlin.crypto.pwhash.crypto_pwhash_MEMLIMIT_MODERATE
import com.ionspin.kotlin.crypto.pwhash.crypto_pwhash_OPSLIMIT_MODERATE
import com.ionspin.kotlin.crypto.pwhash.crypto_pwhash_SALTBYTES
import com.ionspin.kotlin.crypto.pwhash.crypto_pwhash_argon2id_ALG_ARGON2ID13
import com.ionspin.kotlin.crypto.secretbox.SecretBox
import com.ionspin.kotlin.crypto.secretbox.SecretBoxCorruptedOrTamperedDataExceptionOrInvalidKey
import com.ionspin.kotlin.crypto.secretbox.crypto_secretbox_KEYBYTES
import com.ionspin.kotlin.crypto.secretbox.crypto_secretbox_NONCEBYTES
import com.ionspin.kotlin.crypto.util.LibsodiumRandom
import kotlinx.serialization.Serializable
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Thrown whenever a ciphertext fails to authenticate. This is the *only*
 * signal for "wrong master password" — there is no separate password
 * check, so a wrong password and a tampered/corrupted file look identical
 * on purpose (nothing distinguishes them, which is the point of an AEAD).
 */
class VaultDecryptionFailed : Exception("Decryption failed: wrong password, or the data is corrupted/tampered with.")

/**
 * KDF parameters are stored per-vault (not assumed globally) so a future
 * version of this app can tune Argon2id's cost without breaking vaults
 * created under older parameters — an old vault always records exactly
 * the parameters it needs to be re-derived correctly.
 */
@Serializable
data class KdfParams(
    val opsLimit: ULong = crypto_pwhash_OPSLIMIT_MODERATE,
    val memLimit: Int = crypto_pwhash_MEMLIMIT_MODERATE,
    val algorithm: Int = crypto_pwhash_argon2id_ALG_ARGON2ID13
)

@OptIn(ExperimentalEncodingApi::class)
@Serializable(with = EncryptedBoxSerializer::class)
data class EncryptedBox(val nonce: ByteArray, val ciphertext: ByteArray) {
    fun toCompactString(): String = "${Base64.encode(nonce)}:${Base64.encode(ciphertext)}"

    companion object {
        fun fromCompactString(s: String): EncryptedBox {
            val (n, c) = s.split(":", limit = 2)
            return EncryptedBox(Base64.decode(n), Base64.decode(c))
        }
    }

    override fun equals(other: Any?): Boolean =
        other is EncryptedBox && nonce.contentEquals(other.nonce) && ciphertext.contentEquals(other.ciphertext)

    override fun hashCode(): Int = 31 * nonce.contentHashCode() + ciphertext.contentHashCode()
}

/**
 * All the crypto primitives the vault needs, in one place, backed by
 * libsodium (Argon2id via crypto_pwhash, XSalsa20-Poly1305 AEAD via
 * SecretBox). Nothing here should ever be called before [ensureInitialized].
 */
@OptIn(ExperimentalUnsignedTypes::class)
object VaultCrypto {

    val DEK_LENGTH_BYTES = crypto_secretbox_KEYBYTES // 32
    const val SALT_LENGTH_BYTES = crypto_pwhash_SALTBYTES  // 16

    suspend fun ensureInitialized() {
        if (!LibsodiumInitializer.isInitialized()) {
            LibsodiumInitializer.initialize()
        }
    }

    /**
     * Master passwords are restricted to printable ASCII (0x20-0x7E).
     *
     * This isn't an arbitrary restriction: the underlying libsodium JVM
     * binding derives the native byte-length of the password from
     * Kotlin's `String.length` (UTF-16 code units), then hands the raw
     * String to a native call that encodes it as bytes separately. For
     * any character outside single-UTF-16-unit-equals-single-byte ASCII,
     * those two counts can disagree — a real cross-platform correctness
     * hazard for a KDF, where a mismatched byte length is silently wrong
     * key material rather than a visible error. Restricting to ASCII
     * (the same convention hardware wallets use for seed phrases) makes
     * this entire bug class impossible rather than merely unlikely.
     */
    fun validateMasterPassword(password: String) {
        require(password.isNotEmpty()) { "Master password must not be empty." }
        require(password.length >= 12) { "Master password must be at least 12 characters." }
        require(password.all { it.code in 0x20..0x7E }) {
            "Master password must contain only printable ASCII characters (letters, digits, punctuation) — see docs/SECURITY.md for why."
        }
    }

    fun randomSalt(): ByteArray = LibsodiumRandom.buf(SALT_LENGTH_BYTES).asByteArray()

    fun generateDek(): ByteArray = SecretBox.keygen().asByteArray()

    fun newId(): String {
        @OptIn(ExperimentalStdlibApi::class)
        return LibsodiumRandom.buf(16).asByteArray().toHexString()
    }

    /** Derives a 32-byte key from [password] and [salt] using Argon2id. Slow by design. */
    fun deriveKey(password: String, salt: ByteArray, params: KdfParams = KdfParams()): ByteArray {
        validateMasterPassword(password)
        require(salt.size == SALT_LENGTH_BYTES) { "Salt must be $SALT_LENGTH_BYTES bytes, was ${salt.size}." }
        return PasswordHash.pwhash(
            outputLength = DEK_LENGTH_BYTES,
            password = password,
            salt = salt.asUByteArray(),
            opsLimit = params.opsLimit,
            memLimit = params.memLimit,
            algorithm = params.algorithm
        ).asByteArray()
    }

    /** Encrypts [plaintext] under [key] with a fresh random nonce. */
    fun encrypt(plaintext: ByteArray, key: ByteArray): EncryptedBox {
        require(key.size == DEK_LENGTH_BYTES) { "Key must be $DEK_LENGTH_BYTES bytes, was ${key.size}." }
        val nonce = LibsodiumRandom.buf(crypto_secretbox_NONCEBYTES).asByteArray()
        val ciphertext = SecretBox.easy(
            message = plaintext.asUByteArray(),
            nonce = nonce.asUByteArray(),
            key = key.asUByteArray()
        ).asByteArray()
        return EncryptedBox(nonce, ciphertext)
    }

    /** Decrypts [box] under [key]. Throws [VaultDecryptionFailed] on any auth failure. */
    fun decrypt(box: EncryptedBox, key: ByteArray): ByteArray {
        require(key.size == DEK_LENGTH_BYTES) { "Key must be $DEK_LENGTH_BYTES bytes, was ${key.size}." }
        return try {
            SecretBox.openEasy(
                ciphertext = box.ciphertext.asUByteArray(),
                nonce = box.nonce.asUByteArray(),
                key = key.asUByteArray()
            ).asByteArray()
        } catch (e: SecretBoxCorruptedOrTamperedDataExceptionOrInvalidKey) {
            throw VaultDecryptionFailed()
        }
    }

    /** Wraps a 32-byte key ([toWrap], e.g. a DEK) under another key ([wrappingKey]). */
    fun wrapKey(toWrap: ByteArray, wrappingKey: ByteArray): EncryptedBox = encrypt(toWrap, wrappingKey)

    /** Reverses [wrapKey]. Throws [VaultDecryptionFailed] on wrong password/corruption. */
    fun unwrapKey(wrapped: EncryptedBox, wrappingKey: ByteArray): ByteArray = decrypt(wrapped, wrappingKey)

    /** Best-effort zeroing of key material once it's no longer needed. */
    fun wipe(bytes: ByteArray) {
        bytes.fill(0)
    }
}
