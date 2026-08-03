package team.bjtuss.bjtuselfservice.shared.security

import team.bjtuss.bjtuselfservice.shared.auth.Credentials

private const val FORMAT_VERSION: Byte = 1
private const val HEADER_SIZE = 5
private const val MAX_USERNAME_BYTES = 4 * 1024
private const val MAX_PASSWORD_BYTES = 64 * 1024

fun encodeCredentialPayload(credentials: Credentials): ByteArray {
    require(credentials.isValid) { "Credentials must not be blank" }
    val username = credentials.username.encodeToByteArray()
    val password = credentials.password.encodeToByteArray()
    require(username.size <= MAX_USERNAME_BYTES) { "Username is too long" }
    require(password.size <= MAX_PASSWORD_BYTES) { "Password is too long" }

    return ByteArray(HEADER_SIZE + username.size + password.size).also { payload ->
        payload[0] = FORMAT_VERSION
        writeInt(payload, 1, username.size)
        username.copyInto(payload, HEADER_SIZE)
        password.copyInto(payload, HEADER_SIZE + username.size)
    }
}

fun decodeCredentialPayload(payload: ByteArray): Credentials? {
    if (payload.size < HEADER_SIZE || payload[0] != FORMAT_VERSION) return null
    val usernameSize = readInt(payload, 1)
    if (usernameSize !in 1..MAX_USERNAME_BYTES) return null
    val passwordSize = payload.size - HEADER_SIZE - usernameSize
    if (passwordSize !in 1..MAX_PASSWORD_BYTES) return null

    return try {
        Credentials(
            username = payload.copyOfRange(HEADER_SIZE, HEADER_SIZE + usernameSize).decodeToString(
                throwOnInvalidSequence = true,
            ),
            password = payload.copyOfRange(HEADER_SIZE + usernameSize, payload.size).decodeToString(
                throwOnInvalidSequence = true,
            ),
        ).takeIf { it.isValid }
    } catch (_: CharacterCodingException) {
        null
    }
}

private fun writeInt(target: ByteArray, offset: Int, value: Int) {
    target[offset] = (value ushr 24).toByte()
    target[offset + 1] = (value ushr 16).toByte()
    target[offset + 2] = (value ushr 8).toByte()
    target[offset + 3] = value.toByte()
}

private fun readInt(source: ByteArray, offset: Int): Int =
    ((source[offset].toInt() and 0xFF) shl 24) or
        ((source[offset + 1].toInt() and 0xFF) shl 16) or
        ((source[offset + 2].toInt() and 0xFF) shl 8) or
        (source[offset + 3].toInt() and 0xFF)
