package com.notrust.vault.vault

/**
 * Plugged in by platform code (Phase 2): Android Keystore/StrongBox now,
 * iOS Secure Enclave + Keychain later. Wraps/unwraps the browse DEK only —
 * this type must never be handed the secrets DEK. Implementations should
 * require a live biometric match (e.g. Android's
 * `setUserAuthenticationRequired(true)`) before `unwrap` succeeds.
 *
 * Not implemented in the shared module on purpose: there is no portable
 * API for real secure hardware, so this is the one deliberate seam between
 * shared and platform-native code (see docs/ARCHITECTURE.md).
 */
interface BiometricKeyStore {
    suspend fun isAvailable(): Boolean
    suspend fun wrap(browseDek: ByteArray): ByteArray
    suspend fun unwrap(wrapped: ByteArray): ByteArray
    suspend fun invalidate()
}
