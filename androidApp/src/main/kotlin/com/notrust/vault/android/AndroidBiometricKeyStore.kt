package com.notrust.vault.android

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.notrust.vault.vault.BiometricKeyStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
private const val KEY_ALIAS = "no_trust_vault_browse_dek_wrap_key"
private const val TRANSFORMATION = "AES/GCM/NoPadding"
private const val GCM_TAG_LENGTH_BITS = 128

/** Thrown when the biometric prompt itself errors out (cancelled, lockout, etc.), distinct from a decrypt/auth failure. */
class BiometricPromptException(message: String) : Exception(message)

/**
 * Wraps/unwraps the browse DEK using an AES key that lives in the Android
 * Keystore (StrongBox-backed where available) and requires a live
 * biometric match for every single use — see docs/SECURITY.md. This key
 * never touches the secrets DEK; it is created and used purely to satisfy
 * the "biometric unlock only ever reaches the browse tier" property from
 * docs/README.md.
 *
 * This is the one part of Phase 2 that could not be verified at all in
 * the sandbox this was written in — no device, no emulator, no Android
 * SDK. The pattern below (KeyGenParameterSpec.setUserAuthenticationRequired
 * + BiometricPrompt.CryptoObject) is the standard, well-documented one,
 * but expect this specific file to need the most debugging on first real
 * run — see docs/ARCHITECTURE.md.
 */
class AndroidBiometricKeyStore(private val activity: FragmentActivity) : BiometricKeyStore {

    override suspend fun isAvailable(): Boolean = withContext(Dispatchers.Main) {
        BiometricManager.from(activity)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }

    private fun buildKeySpec(strongBox: Boolean): KeyGenParameterSpec =
        KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
            .apply {
                if (strongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    setIsStrongBoxBacked(true)
                }
            }
            .build()

    private fun getOrCreateKey(): SecretKey {
        (keyStore().getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        return try {
            keyGenerator.init(buildKeySpec(strongBox = true))
            keyGenerator.generateKey()
        } catch (e: StrongBoxUnavailableException) {
            // Not every device has StrongBox hardware — fall back to the
            // regular Keystore-backed key, still hardware-backed on any
            // device with a TEE, just not the extra StrongBox isolation.
            keyGenerator.init(buildKeySpec(strongBox = false))
            keyGenerator.generateKey()
        }
    }

    override suspend fun wrap(browseDek: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val authenticated = authenticate(cipher)
        val ciphertext = authenticated.doFinal(browseDek)
        val iv = authenticated.iv
        require(iv.size <= 255) { "Unexpectedly long GCM IV" }
        return byteArrayOf(iv.size.toByte()) + iv + ciphertext
    }

    override suspend fun unwrap(wrapped: ByteArray): ByteArray {
        val ivLength = wrapped[0].toInt() and 0xFF
        val iv = wrapped.copyOfRange(1, 1 + ivLength)
        val ciphertext = wrapped.copyOfRange(1 + ivLength, wrapped.size)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        try {
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        } catch (e: KeyPermanentlyInvalidatedException) {
            // A new fingerprint/face was enrolled since this key was made —
            // by design (setInvalidatedByBiometricEnrollment), the old key
            // is now unusable. Drop it; the caller falls back to the
            // master password and can re-enable biometric unlock after.
            invalidate()
            throw IllegalStateException(
                "Biometric key invalidated (biometric enrollment changed) — unlock with master password, then re-enable biometric unlock in Settings.",
                e
            )
        }
        val authenticated = authenticate(cipher)
        return authenticated.doFinal(ciphertext)
    }

    override suspend fun invalidate() {
        val store = keyStore()
        if (store.containsAlias(KEY_ALIAS)) {
            store.deleteEntry(KEY_ALIAS)
        }
    }

    /** Shows the biometric prompt bound to [cipher] and suspends until the user authenticates. */
    private suspend fun authenticate(cipher: Cipher): Cipher = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            val callback = object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    val resultCipher = result.cryptoObject?.cipher
                    if (resultCipher != null) {
                        continuation.resume(resultCipher)
                    } else {
                        continuation.resumeWithException(BiometricPromptException("Biometric result had no cipher"))
                    }
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(BiometricPromptException(errString.toString()))
                    }
                }

                override fun onAuthenticationFailed() {
                    // A single non-matching attempt, not a terminal error —
                    // the prompt itself stays open for the user to retry.
                }
            }

            val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback)
            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock No-Trust Vault")
                .setSubtitle("Confirm your biometric to browse your saved entries")
                .setNegativeButtonText("Use master password")
                .build()

            continuation.invokeOnCancellation { prompt.cancelAuthentication() }
            prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
        }
    }
}
