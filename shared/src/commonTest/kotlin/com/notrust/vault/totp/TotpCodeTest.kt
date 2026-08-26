package com.notrust.vault.totp

import kotlin.test.Test
import kotlin.test.assertEquals

class TotpCodeTest {

    // RFC 4226 Appendix D — the official HOTP test vectors: ASCII secret
    // "12345678901234567890", SHA1, 6 digits, counters 0 through 9.
    @Test
    fun hotp_matchesRfc4226Vectors() {
        val secret = "12345678901234567890".encodeToByteArray()
        val spec = TotpSpec(secret, TotpAlgorithm.SHA1, digits = 6)
        val expected = listOf(
            "755224", "287082", "359152", "969429", "338314",
            "254676", "287922", "162583", "399871", "520489"
        )
        for (counter in expected.indices) {
            assertEquals(expected[counter], TotpCode.hotp(spec, counter.toLong()), "counter=$counter")
        }
    }

    // RFC 6238 Appendix B — the official TOTP test vectors (8-digit codes,
    // 30-second step, T0=0). Each algorithm repeats the RFC 4226 20-byte
    // ASCII secret to the length that algorithm's HMAC key wants.
    @Test
    fun code_matchesRfc6238Vectors() {
        // Each algorithm's key is the 20-byte ASCII secret "12345678901234567890"
        // repeated to the exact length RFC 6238 specifies (32 / 64 bytes).
        fun repeatTo(base: String, length: Int): ByteArray {
            val out = ByteArray(length)
            val baseBytes = base.encodeToByteArray()
            for (i in 0 until length) out[i] = baseBytes[i % baseBytes.size]
            return out
        }
        val key20 = repeatTo("12345678901234567890", 20)
        val key32 = repeatTo("12345678901234567890", 32)
        val key64 = repeatTo("12345678901234567890", 64)

        data class Vector(val time: Long, val algorithm: TotpAlgorithm, val key: ByteArray, val expected: String)
        val vectors = listOf(
            Vector(59L, TotpAlgorithm.SHA1, key20, "94287082"),
            Vector(59L, TotpAlgorithm.SHA256, key32, "46119246"),
            Vector(59L, TotpAlgorithm.SHA512, key64, "90693936"),
            Vector(1111111109L, TotpAlgorithm.SHA1, key20, "07081804"),
            Vector(1111111109L, TotpAlgorithm.SHA256, key32, "68084774"),
            Vector(1111111109L, TotpAlgorithm.SHA512, key64, "25091201"),
            Vector(1111111111L, TotpAlgorithm.SHA1, key20, "14050471"),
            Vector(2000000000L, TotpAlgorithm.SHA1, key20, "69279037"),
            Vector(20000000000L, TotpAlgorithm.SHA1, key20, "65353130")
        )
        for (v in vectors) {
            val spec = TotpSpec(v.key, v.algorithm, digits = 8, periodSeconds = 30)
            assertEquals(v.expected, TotpCode.code(spec, v.time), "time=${v.time} algo=${v.algorithm}")
        }
    }

    @Test
    fun code_defaultsToSixDigitsAndThirtySecondPeriod() {
        val spec = TotpSpec("12345678901234567890".encodeToByteArray())
        assertEquals(6, TotpCode.code(spec, 0L).length)
    }

    @Test
    fun secondsRemaining_countsDownWithinThePeriod() {
        val spec = TotpSpec("12345678901234567890".encodeToByteArray(), periodSeconds = 30)
        assertEquals(30, TotpCode.secondsRemaining(spec, 0L))
        assertEquals(1, TotpCode.secondsRemaining(spec, 29L))
        assertEquals(30, TotpCode.secondsRemaining(spec, 30L))
        assertEquals(15, TotpCode.secondsRemaining(spec, 45L))
    }

    @Test
    fun sameCounter_alwaysProducesTheSameCode() {
        val spec = TotpSpec("12345678901234567890".encodeToByteArray())
        val a = TotpCode.code(spec, 100L)
        val b = TotpCode.code(spec, 100L)
        assertEquals(a, b)
    }
}
