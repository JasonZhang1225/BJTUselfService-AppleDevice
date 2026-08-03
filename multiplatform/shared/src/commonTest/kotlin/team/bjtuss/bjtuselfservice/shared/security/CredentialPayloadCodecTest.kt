package team.bjtuss.bjtuselfservice.shared.security

import team.bjtuss.bjtuselfservice.shared.auth.Credentials
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class CredentialPayloadCodecTest {
    @Test
    fun roundTripsUnicodeCredentialsWithoutDelimiterAmbiguity() {
        val credentials = Credentials(
            username = "学号:fixture",
            password = "密码;with|delimiters\n第二行",
        )

        assertEquals(credentials, decodeCredentialPayload(encodeCredentialPayload(credentials)))
    }

    @Test
    fun malformedVersionLengthAndUtf8AreRejected() {
        assertNull(decodeCredentialPayload(byteArrayOf()))
        assertNull(decodeCredentialPayload(byteArrayOf(99, 0, 0, 0, 1, 1, 1)))
        assertNull(decodeCredentialPayload(byteArrayOf(1, 0, 0, 0, 20, 1, 2)))
        assertNull(decodeCredentialPayload(byteArrayOf(1, 0, 0, 0, 1, 0xFF.toByte(), 1)))
    }

    @Test
    fun blankCredentialsCannotBeEncoded() {
        assertFailsWith<IllegalArgumentException> {
            encodeCredentialPayload(Credentials("", ""))
        }
    }
}
