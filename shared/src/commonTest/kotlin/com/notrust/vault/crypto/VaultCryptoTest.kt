package com.notrust.vault.crypto

import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Why these tests are shaped the way they are: the two official libsodium
 * test vectors we found for Argon2id (in pwhash_argon2id.c) use a 32-byte
 * and an 8-byte salt respectively, exercising the raw `crypto_pwhash_argon2id`
 * primitive and the `_str` string-format path. Our code calls the generic
 * `crypto_pwhash()` dispatcher, which *requires* exactly
 * `crypto_pwhash_SALTBYTES` (16) bytes of salt — a different entry point.
 * Reusing those vectors here would either not compile (wrong salt length)
 * or silently test a code path we don't use. Instead: a determinism test,
 * a sensitivity test (every parameter must matter), a self-pinned
 * regression vector for the exact call we make, and a cross-check against
 * this library's independent `str`/`strVerify` API as an end-to-end sanity
 * check that the native binding itself is alive and correct.
 */
class VaultCryptoTest {

    private fun salt(byte: Int): ByteArray = ByteArray(VaultCrypto.SALT_LENGTH_BYTES) { byte.toByte() }

    @Test
    fun initializes() = runTest {
        VaultCrypto.ensureInitialized()
    }

    @Test
    fun deriveKey_isDeterministic() = runTest {
        VaultCrypto.ensureInitialized()
        val salt = salt(1)
        val a = VaultCrypto.deriveKey("correct horse battery staple!", salt)
        val b = VaultCrypto.deriveKey("correct horse battery staple!", salt)
        assertContentEquals(a, b)
    }

    @Test
    fun deriveKey_outputLengthIsDekLength() = runTest {
        VaultCrypto.ensureInitialized()
        val key = VaultCrypto.deriveKey("correct horse battery staple!", salt(2))
        assertEquals(VaultCrypto.DEK_LENGTH_BYTES, key.size)
    }

    @Test
    fun deriveKey_differentPasswordsDiffer() = runTest {
        VaultCrypto.ensureInitialized()
        val s = salt(3)
        val a = VaultCrypto.deriveKey("correct horse battery staple!", s)
        val b = VaultCrypto.deriveKey("correct horse battery staple?", s)
        assertFalse(a.contentEquals(b), "changing the password must change the derived key")
    }

    @Test
    fun deriveKey_differentSaltsDiffer() = runTest {
        VaultCrypto.ensureInitialized()
        val a = VaultCrypto.deriveKey("correct horse battery staple!", salt(4))
        val b = VaultCrypto.deriveKey("correct horse battery staple!", salt(5))
        assertFalse(a.contentEquals(b), "changing the salt must change the derived key")
    }

    @Test
    fun deriveKey_differentKdfParamsDiffer() = runTest {
        VaultCrypto.ensureInitialized()
        val s = salt(6)
        val default = VaultCrypto.deriveKey("correct horse battery staple!", s, KdfParams())
        val weaker = VaultCrypto.deriveKey(
            "correct horse battery staple!",
            s,
            KdfParams(
                opsLimit = com.ionspin.kotlin.crypto.pwhash.crypto_pwhash_OPSLIMIT_MIN,
                memLimit = com.ionspin.kotlin.crypto.pwhash.crypto_pwhash_MEMLIMIT_MIN
            )
        )
        assertFalse(default.contentEquals(weaker), "changing KDF cost parameters must change the derived key")
    }

    /**
     * Self-pinned regression vector: fixed inputs, output recorded once
     * against this exact library version and locked in. If a future
     * dependency bump or refactor silently changes this library's Argon2id
     * output for identical inputs, this test catches it immediately.
     */
    @OptIn(ExperimentalEncodingApi::class)
    @Test
    fun deriveKey_matchesPinnedRegressionVector() = runTest {
        VaultCrypto.ensureInitialized()
        val salt = ByteArray(16) { it.toByte() } // 0x00, 0x01, ..., 0x0F
        val key = VaultCrypto.deriveKey("regression-test-password-001", salt, KdfParams())
        assertEquals(PINNED_REGRESSION_VECTOR_BASE64, Base64.encode(key))
    }

    @Test
    fun passwordHashStrRoundTrip_sanityChecksTheNativeBinding() = runTest {
        VaultCrypto.ensureInitialized()
        val hash = com.ionspin.kotlin.crypto.pwhash.PasswordHash.str(
            "some password",
            com.ionspin.kotlin.crypto.pwhash.crypto_pwhash_OPSLIMIT_MODERATE,
            com.ionspin.kotlin.crypto.pwhash.crypto_pwhash_MEMLIMIT_MODERATE
        )
        assertTrue(com.ionspin.kotlin.crypto.pwhash.PasswordHash.strVerify(hash, "some password"))
        assertFalse(com.ionspin.kotlin.crypto.pwhash.PasswordHash.strVerify(hash, "wrong password"))
    }

    @Test
    fun validateMasterPassword_rejectsTooShort() {
        assertFailsWith<IllegalArgumentException> { VaultCrypto.validateMasterPassword("short1") }
    }

    @Test
    fun validateMasterPassword_rejectsEmpty() {
        assertFailsWith<IllegalArgumentException> { VaultCrypto.validateMasterPassword("") }
    }

    @Test
    fun validateMasterPassword_rejectsNonAscii() {
        // See the class doc on VaultCrypto: non-ASCII risks a byte-length
        // mismatch between Kotlin's String.length and the native marshalled
        // byte count in the underlying JVM binding.
        assertFailsWith<IllegalArgumentException> { VaultCrypto.validateMasterPassword("correct-horse-café") }
    }

    @Test
    fun validateMasterPassword_acceptsValidAscii() {
        VaultCrypto.validateMasterPassword("correct horse battery staple!")
    }

    @Test
    fun encryptDecrypt_roundTripsManyRandomInputs() = runTest {
        VaultCrypto.ensureInitialized()
        val key = VaultCrypto.generateDek()
        val random = Random(42)
        val cases = buildList {
            add(ByteArray(0))
            add("hello".encodeToByteArray())
            add("unicode: café 🔐".encodeToByteArray())
            repeat(50) {
                add(random.nextBytes(random.nextInt(0, 4096)))
            }
        }
        for (plaintext in cases) {
            val box = VaultCrypto.encrypt(plaintext, key)
            val decrypted = VaultCrypto.decrypt(box, key)
            assertContentEquals(plaintext, decrypted, "round trip failed for ${plaintext.size}-byte input")
        }
    }

    @Test
    fun encrypt_producesFreshNonceEachTime() = runTest {
        VaultCrypto.ensureInitialized()
        val key = VaultCrypto.generateDek()
        val a = VaultCrypto.encrypt("same message".encodeToByteArray(), key)
        val b = VaultCrypto.encrypt("same message".encodeToByteArray(), key)
        assertFalse(a.nonce.contentEquals(b.nonce), "nonces must not repeat")
        assertFalse(a.ciphertext.contentEquals(b.ciphertext), "ciphertext must differ when the nonce differs")
    }

    @Test
    fun decrypt_failsWithWrongKey() = runTest {
        VaultCrypto.ensureInitialized()
        val box = VaultCrypto.encrypt("secret".encodeToByteArray(), VaultCrypto.generateDek())
        assertFailsWith<VaultDecryptionFailed> {
            VaultCrypto.decrypt(box, VaultCrypto.generateDek())
        }
    }

    @Test
    fun decrypt_failsOnTamperedCiphertext() = runTest {
        VaultCrypto.ensureInitialized()
        val key = VaultCrypto.generateDek()
        val box = VaultCrypto.encrypt("secret message".encodeToByteArray(), key)
        val tampered = box.ciphertext.copyOf()
        tampered[0] = (tampered[0].toInt() xor 0x01).toByte()
        assertFailsWith<VaultDecryptionFailed> {
            VaultCrypto.decrypt(EncryptedBox(box.nonce, tampered), key)
        }
    }

    @Test
    fun decrypt_failsOnTamperedNonce() = runTest {
        VaultCrypto.ensureInitialized()
        val key = VaultCrypto.generateDek()
        val box = VaultCrypto.encrypt("secret message".encodeToByteArray(), key)
        val tamperedNonce = box.nonce.copyOf()
        tamperedNonce[0] = (tamperedNonce[0].toInt() xor 0x01).toByte()
        assertFailsWith<VaultDecryptionFailed> {
            VaultCrypto.decrypt(EncryptedBox(tamperedNonce, box.ciphertext), key)
        }
    }

    @Test
    fun wrapUnwrapKey_roundTrips() = runTest {
        VaultCrypto.ensureInitialized()
        val wrappingKey = VaultCrypto.generateDek()
        val secret = VaultCrypto.generateDek()
        val wrapped = VaultCrypto.wrapKey(secret, wrappingKey)
        val unwrapped = VaultCrypto.unwrapKey(wrapped, wrappingKey)
        assertContentEquals(secret, unwrapped)
    }

    @Test
    fun encryptedBox_compactStringRoundTrips() = runTest {
        VaultCrypto.ensureInitialized()
        val key = VaultCrypto.generateDek()
        val box = VaultCrypto.encrypt("round trip me".encodeToByteArray(), key)
        val restored = EncryptedBox.fromCompactString(box.toCompactString())
        assertEquals(box, restored)
        assertContentEquals("round trip me".encodeToByteArray(), VaultCrypto.decrypt(restored, key))
    }

    @Test
    fun generateDek_producesDistinctKeys() = runTest {
        VaultCrypto.ensureInitialized()
        val a = VaultCrypto.generateDek()
        val b = VaultCrypto.generateDek()
        assertNotEquals(a.toList(), b.toList())
    }

    companion object {
        // Filled in from a verified run against multiplatform-crypto-libsodium-bindings 0.9.5.
        // See docs/TESTING.md — CI fails loudly if this ever silently changes.
        const val PINNED_REGRESSION_VECTOR_BASE64 = "OAqf1s3A6VolD1PkE7rmDWjeFyILz2J5A8rD+cNa4so="
    }
}
