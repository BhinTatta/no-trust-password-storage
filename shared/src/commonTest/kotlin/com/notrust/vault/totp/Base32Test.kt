package com.notrust.vault.totp

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class Base32Test {

    // RFC 4648 §10 — the canonical published test vectors.
    private val vectors = listOf(
        "" to "",
        "f" to "MY======",
        "fo" to "MZXQ====",
        "foo" to "MZXW6===",
        "foob" to "MZXW6YQ=",
        "fooba" to "MZXW6YTB",
        "foobar" to "MZXW6YTBOI======"
    )

    @Test
    fun encode_matchesRfc4648Vectors() {
        for ((plain, encoded) in vectors) {
            assertEquals(encoded, Base32.encode(plain.encodeToByteArray()), "encoding '$plain'")
        }
    }

    @Test
    fun decode_matchesRfc4648Vectors() {
        for ((plain, encoded) in vectors) {
            assertContentEquals(plain.encodeToByteArray(), Base32.decode(encoded), "decoding '$encoded'")
        }
    }

    @Test
    fun decode_isCaseInsensitiveAndTolerantOfSpacesAndMissingPadding() {
        assertContentEquals("foobar".encodeToByteArray(), Base32.decode("mzxw6ytboi======"))
        assertContentEquals("foobar".encodeToByteArray(), Base32.decode("MZXW 6YTB OI"))
        assertContentEquals("foobar".encodeToByteArray(), Base32.decode("MZXW6YTBOI"))
    }

    @Test
    fun decode_rejectsInvalidCharacters() {
        assertFailsWith<IllegalArgumentException> { Base32.decode("not-valid-base32-!!!") }
    }

    @Test
    fun decode_theCommonTutorialSecretDecodesToTheKnownBytes() {
        // A widely-used example secret (e.g. Wikipedia's HOTP article) — a
        // handy real-world sanity check beyond the RFC's own ASCII vectors.
        // "Hello!" followed by the bytes 0xDE 0xAD 0xBE 0xEF.
        val expected = byteArrayOf(0x48, 0x65, 0x6C, 0x6C, 0x6F, 0x21, 0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte())
        assertContentEquals(expected, Base32.decode("JBSWY3DPEHPK3PXP"))
    }
}
