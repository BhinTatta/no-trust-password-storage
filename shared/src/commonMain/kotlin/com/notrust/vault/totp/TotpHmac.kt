package com.notrust.vault.totp

/**
 * The one platform-specific primitive TOTP needs. Deliberately backed by
 * each platform's own vetted crypto provider (JVM/Android: javax.crypto.Mac)
 * rather than a hand-rolled HMAC/SHA implementation in common code — HMAC
 * itself and RFC 4226's truncation are simple enough to keep pure and
 * tested here, but SHA-1/256/512 are exactly the kind of bit-level code
 * that's easy to get subtly wrong without a device to verify against, so
 * this borrows the platform's own correct-by-construction implementation
 * instead.
 */
expect fun totpHmac(algorithm: TotpAlgorithm, key: ByteArray, message: ByteArray): ByteArray
