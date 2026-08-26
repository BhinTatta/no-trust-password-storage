package com.notrust.vault.totp

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

actual fun totpHmac(algorithm: TotpAlgorithm, key: ByteArray, message: ByteArray): ByteArray {
    val name = when (algorithm) {
        TotpAlgorithm.SHA1 -> "HmacSHA1"
        TotpAlgorithm.SHA256 -> "HmacSHA256"
        TotpAlgorithm.SHA512 -> "HmacSHA512"
    }
    val mac = Mac.getInstance(name)
    mac.init(SecretKeySpec(key, name))
    return mac.doFinal(message)
}
