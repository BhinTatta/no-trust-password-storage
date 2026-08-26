package com.notrust.vault.totp

/**
 * RFC 4648 §6 Base32 (the alphabet every otpauth:// secret is encoded
 * with) — not the same alphabet as this project's own vault-file Base64
 * (see ByteArrayBase64Serializer), and not available from Kotlin's
 * stdlib, so implemented directly here rather than pulling in a
 * dependency for one small, easily-tested algorithm.
 */
object Base32 {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    /** Case-insensitive; tolerates spaces/hyphens and missing/partial padding, since that's how people paste secrets. */
    fun decode(input: String): ByteArray {
        val clean = input.trim().uppercase().replace(" ", "").replace("-", "").trimEnd('=')
        if (clean.isEmpty()) return ByteArray(0)
        require(clean.all { it in ALPHABET }) { "Not a valid Base32 string." }
        val output = ArrayList<Byte>((clean.length * 5) / 8 + 1)
        var buffer = 0L
        var bitsInBuffer = 0
        for (c in clean) {
            buffer = (buffer shl 5) or ALPHABET.indexOf(c).toLong()
            bitsInBuffer += 5
            if (bitsInBuffer >= 8) {
                bitsInBuffer -= 8
                output.add(((buffer shr bitsInBuffer) and 0xFF).toByte())
            }
        }
        return output.toByteArray()
    }

    fun encode(data: ByteArray): String {
        if (data.isEmpty()) return ""
        val sb = StringBuilder()
        var buffer = 0L
        var bitsInBuffer = 0
        for (b in data) {
            buffer = (buffer shl 8) or (b.toLong() and 0xFF)
            bitsInBuffer += 8
            while (bitsInBuffer >= 5) {
                bitsInBuffer -= 5
                sb.append(ALPHABET[((buffer shr bitsInBuffer) and 0x1F).toInt()])
            }
        }
        if (bitsInBuffer > 0) {
            sb.append(ALPHABET[((buffer shl (5 - bitsInBuffer)) and 0x1F).toInt()])
        }
        while (sb.length % 8 != 0) sb.append('=')
        return sb.toString()
    }
}
