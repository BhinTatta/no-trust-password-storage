package com.notrust.vault.totp

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TotpSeedParserTest {

    @Test
    fun parse_acceptsFullOtpauthUri() {
        val spec = assertNotNull(TotpSeedParser.parse("otpauth://totp/GitHub:bob?secret=JBSWY3DPEHPK3PXP&digits=8"))
        assertContentEquals(Base32.decode("JBSWY3DPEHPK3PXP"), spec.secret)
        assertEquals(8, spec.digits)
    }

    @Test
    fun parse_acceptsBareBase32Secret_withDefaults() {
        val spec = assertNotNull(TotpSeedParser.parse("JBSWY3DPEHPK3PXP"))
        assertContentEquals(Base32.decode("JBSWY3DPEHPK3PXP"), spec.secret)
        assertEquals(TotpAlgorithm.SHA1, spec.algorithm)
        assertEquals(6, spec.digits)
        assertEquals(30, spec.periodSeconds)
    }

    @Test
    fun parse_acceptsBareSecretWithSpacesLikeIssuersDisplayThem() {
        // Most issuers show the manual-entry fallback code in 4-character
        // groups ("JBSW Y3DP EHPK 3PXP") — must work pasted verbatim too.
        val spec = assertNotNull(TotpSeedParser.parse("JBSW Y3DP EHPK 3PXP"))
        assertContentEquals(Base32.decode("JBSWY3DPEHPK3PXP"), spec.secret)
    }

    @Test
    fun parse_rejectsGarbage() {
        assertNull(TotpSeedParser.parse(""))
        assertNull(TotpSeedParser.parse("   "))
        assertNull(TotpSeedParser.parse("not a totp secret or uri!!!"))
    }

    @Test
    fun previewLabel_describesAFullUriButNotABareSecret() {
        assertEquals(
            "GitHub · bob",
            TotpSeedParser.previewLabel("otpauth://totp/GitHub:bob?secret=JBSWY3DPEHPK3PXP")
        )
        assertNull(TotpSeedParser.previewLabel("JBSWY3DPEHPK3PXP"))
    }
}
