package com.notrust.vault.totp

/**
 * Everything a TOTP entry needs to generate codes — decoded straight from
 * an otpauth:// URI or manual entry. [secret] is the raw decoded key
 * bytes (never the Base32 text) once it leaves [OtpAuthUri]/[Base32].
 */
data class TotpSpec(
    val secret: ByteArray,
    val algorithm: TotpAlgorithm = TotpAlgorithm.SHA1,
    val digits: Int = 6,
    val periodSeconds: Int = 30
) {
    init {
        require(secret.isNotEmpty()) { "TOTP secret must not be empty." }
        require(digits in 6..8) { "TOTP digits must be 6, 7, or 8." }
        require(periodSeconds > 0) { "TOTP period must be positive." }
    }

    override fun equals(other: Any?): Boolean =
        other is TotpSpec && secret.contentEquals(other.secret) && algorithm == other.algorithm &&
            digits == other.digits && periodSeconds == other.periodSeconds

    override fun hashCode(): Int {
        var result = secret.contentHashCode()
        result = 31 * result + algorithm.hashCode()
        result = 31 * result + digits
        result = 31 * result + periodSeconds
        return result
    }
}

/**
 * HOTP (RFC 4226) and TOTP (RFC 6238). Deliberately takes the current time
 * as a parameter rather than reading the system clock itself — this stays
 * a pure function, testable against the RFCs' published vectors without
 * any platform dependency, and the Android layer supplies the real clock.
 */
object TotpCode {
    /** RFC 4226 §5.3: HMAC the counter, then dynamically truncate to [spec.digits] decimal digits. */
    fun hotp(spec: TotpSpec, counter: Long): String {
        val counterBytes = ByteArray(8) { i -> ((counter ushr (8 * (7 - i))) and 0xFF).toByte() }
        val hash = totpHmac(spec.algorithm, spec.secret, counterBytes)
        val offset = (hash[hash.size - 1].toInt() and 0x0F)
        val binary = ((hash[offset].toInt() and 0x7F) shl 24) or
            ((hash[offset + 1].toInt() and 0xFF) shl 16) or
            ((hash[offset + 2].toInt() and 0xFF) shl 8) or
            (hash[offset + 3].toInt() and 0xFF)
        var mod = 1
        repeat(spec.digits) { mod *= 10 }
        return (binary % mod).toString().padStart(spec.digits, '0')
    }

    /** RFC 6238: the counter is just how many whole periods have elapsed since the Unix epoch. */
    fun code(spec: TotpSpec, unixTimeSeconds: Long): String =
        hotp(spec, unixTimeSeconds / spec.periodSeconds)

    /** How many seconds until [code] rolls over to the next value — for a countdown ring/label. */
    fun secondsRemaining(spec: TotpSpec, unixTimeSeconds: Long): Int =
        spec.periodSeconds - (unixTimeSeconds % spec.periodSeconds).toInt()
}
