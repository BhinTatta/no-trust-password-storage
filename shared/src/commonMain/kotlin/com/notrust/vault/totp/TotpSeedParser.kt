package com.notrust.vault.totp

/**
 * The single entry point for turning whatever a user pasted or a QR code
 * decoded to into a usable [TotpSpec] — accepts either a full
 * `otpauth://totp/...` URI or a bare Base32 secret (the "can't scan? type
 * this code instead" fallback almost every issuer also offers), in that
 * order. What's stored in [com.notrust.vault.model.TotpEntry.seed]
 * is exactly the raw text this was given, verbatim — this same parser
 * runs again at reveal time to turn it back into a [TotpSpec], so nothing
 * about the original input (including a non-default algorithm/digits/
 * period from a full URI) is ever lost by re-deriving it.
 */
object TotpSeedParser {
    fun parse(rawSeed: String): TotpSpec? {
        val trimmed = rawSeed.trim()
        if (trimmed.isEmpty()) return null

        OtpAuthUri.parse(trimmed)?.let { return TotpSpec(it.secret, it.algorithm, it.digits, it.periodSeconds) }

        return try {
            val bytes = Base32.decode(trimmed)
            if (bytes.isEmpty()) null else TotpSpec(bytes)
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    /** For a confirmation preview before saving — the label/issuer a full otpauth:// URI carries, or null for a bare secret. */
    fun previewLabel(rawSeed: String): String? = OtpAuthUri.parse(rawSeed.trim())?.let { parsed ->
        if (parsed.issuer != null) "${parsed.issuer} · ${parsed.label}" else parsed.label
    }
}
