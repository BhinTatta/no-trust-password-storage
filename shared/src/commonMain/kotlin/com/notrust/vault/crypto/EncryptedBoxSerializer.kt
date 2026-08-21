package com.notrust.vault.crypto

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/** Serializes [EncryptedBox] as a single `"<base64-nonce>:<base64-ciphertext>"` JSON string. */
object EncryptedBoxSerializer : KSerializer<EncryptedBox> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("EncryptedBox", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: EncryptedBox) {
        encoder.encodeString(value.toCompactString())
    }

    override fun deserialize(decoder: Decoder): EncryptedBox {
        return EncryptedBox.fromCompactString(decoder.decodeString())
    }
}
