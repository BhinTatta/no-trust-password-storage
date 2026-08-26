package com.notrust.vault.totp

/**
 * The result of successfully parsing an `otpauth://totp/...` URI — from a
 * scanned QR code or a pasted link. [secret] is already-decoded raw key
 * bytes, ready for [TotpSpec]; nothing here is a secret's Base32 *text*
 * once this returns.
 */
data class ParsedOtpAuth(
    val label: String,
    val issuer: String?,
    val secret: ByteArray,
    val algorithm: TotpAlgorithm,
    val digits: Int,
    val periodSeconds: Int
) {
    override fun equals(other: Any?): Boolean =
        other is ParsedOtpAuth && label == other.label && issuer == other.issuer &&
            secret.contentEquals(other.secret) && algorithm == other.algorithm &&
            digits == other.digits && periodSeconds == other.periodSeconds

    override fun hashCode(): Int {
        var result = label.hashCode()
        result = 31 * result + (issuer?.hashCode() ?: 0)
        result = 31 * result + secret.contentHashCode()
        result = 31 * result + algorithm.hashCode()
        result = 31 * result + digits
        result = 31 * result + periodSeconds
        return result
    }
}

/**
 * Parses the `otpauth://totp/Label?secret=...&issuer=...` URI format every
 * major authenticator (Google Authenticator, Authy, 1Password, ...) uses
 * for QR provisioning — this is what a scanned QR code's payload, or a
 * pasted link, actually contains. Deliberately hand-rolled percent-decoding
 * and query parsing rather than a platform URI class, so this stays in
 * commonMain with no platform dependency and no risk from a
 * locale-sensitive or platform-specific URI parser.
 *
 * `otpauth://hotp/...` (counter-based, not time-based) is deliberately
 * rejected — this app only ever offers TOTP.
 */
object OtpAuthUri {
    private const val SCHEME = "otpauth://"

    fun parse(raw: String): ParsedOtpAuth? {
        val trimmed = raw.trim()
        if (!trimmed.startsWith(SCHEME, ignoreCase = true)) return null
        val withoutScheme = trimmed.substring(SCHEME.length)

        val slashIndex = withoutScheme.indexOf('/')
        if (slashIndex < 0) return null
        val type = withoutScheme.substring(0, slashIndex)
        if (!type.equals("totp", ignoreCase = true)) return null

        val rest = withoutScheme.substring(slashIndex + 1)
        val queryIndex = rest.indexOf('?')
        val labelPart = if (queryIndex >= 0) rest.substring(0, queryIndex) else rest
        val queryPart = if (queryIndex >= 0) rest.substring(queryIndex + 1) else ""
        val params = parseQuery(queryPart)

        val secretParam = params["secret"]?.takeIf { it.isNotBlank() } ?: return null
        val secretBytes = try {
            Base32.decode(secretParam)
        } catch (e: IllegalArgumentException) {
            return null
        }
        if (secretBytes.isEmpty()) return null

        val decodedLabel = percentDecode(labelPart)
        val colonIndex = decodedLabel.indexOf(':')
        val labelIssuer = if (colonIndex >= 0) decodedLabel.substring(0, colonIndex).trim() else null
        val accountName = (if (colonIndex >= 0) decodedLabel.substring(colonIndex + 1) else decodedLabel).trim()

        val issuer = params["issuer"]?.trim()?.takeIf { it.isNotEmpty() } ?: labelIssuer?.takeIf { it.isNotEmpty() }
        val algorithm = TotpAlgorithm.fromLabel(params["algorithm"])
        val digits = params["digits"]?.toIntOrNull()?.takeIf { it in 6..8 } ?: 6
        val period = params["period"]?.toIntOrNull()?.takeIf { it > 0 } ?: 30

        return ParsedOtpAuth(
            label = accountName.ifEmpty { issuer ?: "Authenticator" },
            issuer = issuer,
            secret = secretBytes,
            algorithm = algorithm,
            digits = digits,
            periodSeconds = period
        )
    }

    private fun parseQuery(query: String): Map<String, String> {
        if (query.isEmpty()) return emptyMap()
        val result = LinkedHashMap<String, String>()
        for (pair in query.split("&")) {
            if (pair.isEmpty()) continue
            val eqIndex = pair.indexOf('=')
            if (eqIndex < 0) {
                result[percentDecode(pair)] = ""
            } else {
                result[percentDecode(pair.substring(0, eqIndex))] = percentDecode(pair.substring(eqIndex + 1))
            }
        }
        return result
    }

    private fun percentDecode(s: String): String {
        if ('%' !in s) return s
        val bytes = ArrayList<Byte>(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '%' && i + 2 < s.length) {
                val value = s.substring(i + 1, i + 3).toIntOrNull(16)
                if (value != null) {
                    bytes.add(value.toByte())
                    i += 3
                    continue
                }
            }
            for (b in c.toString().encodeToByteArray()) bytes.add(b)
            i++
        }
        return bytes.toByteArray().decodeToString()
    }
}
