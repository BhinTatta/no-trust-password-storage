package com.notrust.vault.totp

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class OtpAuthUriTest {

    @Test
    fun parse_fullUriWithAllParams() {
        val parsed = assertNotNull(
            OtpAuthUri.parse("otpauth://totp/Example:alice@example.com?secret=JBSWY3DPEHPK3PXP&issuer=Example&algorithm=SHA1&digits=6&period=30")
        )
        assertEquals("alice@example.com", parsed.label)
        assertEquals("Example", parsed.issuer)
        assertContentEquals(Base32.decode("JBSWY3DPEHPK3PXP"), parsed.secret)
        assertEquals(TotpAlgorithm.SHA1, parsed.algorithm)
        assertEquals(6, parsed.digits)
        assertEquals(30, parsed.periodSeconds)
    }

    @Test
    fun parse_minimalUri_fillsInDefaults() {
        val parsed = assertNotNull(OtpAuthUri.parse("otpauth://totp/GitHub:bob?secret=JBSWY3DPEHPK3PXP"))
        assertEquals("bob", parsed.label)
        assertEquals("GitHub", parsed.issuer)
        assertEquals(TotpAlgorithm.SHA1, parsed.algorithm)
        assertEquals(6, parsed.digits)
        assertEquals(30, parsed.periodSeconds)
    }

    @Test
    fun parse_labelWithNoIssuerPrefix_leavesIssuerNull() {
        val parsed = assertNotNull(OtpAuthUri.parse("otpauth://totp/bob@example.com?secret=JBSWY3DPEHPK3PXP"))
        assertEquals("bob@example.com", parsed.label)
        assertNull(parsed.issuer)
    }

    @Test
    fun parse_issuerQueryParamOverridesLabelPrefix() {
        val parsed = assertNotNull(
            OtpAuthUri.parse("otpauth://totp/OldName:bob?secret=JBSWY3DPEHPK3PXP&issuer=NewName")
        )
        assertEquals("NewName", parsed.issuer)
    }

    @Test
    fun parse_percentEncodedLabelIsDecoded() {
        val parsed = assertNotNull(OtpAuthUri.parse("otpauth://totp/My%20Company:bob?secret=JBSWY3DPEHPK3PXP"))
        assertEquals("My Company", parsed.issuer)
        assertEquals("bob", parsed.label)
    }

    @Test
    fun parse_nonDefaultAlgorithmDigitsAndPeriod() {
        val parsed = assertNotNull(
            OtpAuthUri.parse("otpauth://totp/svc:acct?secret=JBSWY3DPEHPK3PXP&algorithm=SHA256&digits=8&period=60")
        )
        assertEquals(TotpAlgorithm.SHA256, parsed.algorithm)
        assertEquals(8, parsed.digits)
        assertEquals(60, parsed.periodSeconds)
    }

    @Test
    fun parse_rejectsHotp() {
        assertNull(OtpAuthUri.parse("otpauth://hotp/svc:acct?secret=JBSWY3DPEHPK3PXP&counter=0"))
    }

    @Test
    fun parse_rejectsMissingSecret() {
        assertNull(OtpAuthUri.parse("otpauth://totp/svc:acct?issuer=svc"))
    }

    @Test
    fun parse_rejectsInvalidSecret() {
        assertNull(OtpAuthUri.parse("otpauth://totp/svc:acct?secret=not-valid-base32!!!"))
    }

    @Test
    fun parse_rejectsNonOtpauthUris() {
        assertNull(OtpAuthUri.parse("https://example.com/totp?secret=JBSWY3DPEHPK3PXP"))
        assertNull(OtpAuthUri.parse("JBSWY3DPEHPK3PXP"))
    }
}
