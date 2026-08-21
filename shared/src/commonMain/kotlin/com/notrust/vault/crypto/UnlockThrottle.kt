package com.notrust.vault.crypto

/**
 * How long to make someone wait after N consecutive wrong master-password
 * attempts. This is on top of Argon2id's own cost — a second, cheap line
 * of defense against fast retry loops — not a replacement for it.
 *
 * No attempt limit ever deletes or locks out the vault permanently: that
 * would just be a self-inflicted denial-of-service against the real
 * owner. The only cost of getting it wrong is time.
 */
object UnlockThrottle {
    private const val FREE_ATTEMPTS = 2
    private const val MAX_DELAY_MILLIS = 5 * 60 * 1000L // 5 minutes

    /** Milliseconds to wait before the *next* attempt is allowed, given [consecutiveFailures] so far. */
    fun delayMillisAfter(consecutiveFailures: Int): Long {
        require(consecutiveFailures >= 0) { "consecutiveFailures must not be negative" }
        if (consecutiveFailures <= FREE_ATTEMPTS) return 0L

        // The exponent bound here only exists to keep `1L shl exponent` from
        // overflowing on a pathological input like Int.MAX_VALUE — the real
        // ceiling is the coerceAtMost(MAX_DELAY_MILLIS) below, so this bound
        // must be well past the point where seconds already exceeds the cap.
        val exponent = (consecutiveFailures - FREE_ATTEMPTS).coerceAtMost(40)
        val seconds = 1L shl exponent // 2, 4, 8, 16, ...
        return (seconds * 1000L).coerceAtMost(MAX_DELAY_MILLIS)
    }
}
