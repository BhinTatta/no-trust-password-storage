package com.notrust.vault.totp

/** The HMAC hash underlying HOTP/TOTP (RFC 4226 / RFC 6238). SHA1 is the default almost every real-world issuer (Google, GitHub, AWS, ...) uses; SHA256/SHA512 exist for completeness. */
enum class TotpAlgorithm {
    SHA1, SHA256, SHA512;

    companion object {
        fun fromLabel(label: String?): TotpAlgorithm = when (label?.trim()?.uppercase()) {
            "SHA256" -> SHA256
            "SHA512" -> SHA512
            else -> SHA1
        }
    }
}
