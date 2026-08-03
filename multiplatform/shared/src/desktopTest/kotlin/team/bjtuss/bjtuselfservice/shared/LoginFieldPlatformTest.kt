package team.bjtuss.bjtuselfservice.shared

import kotlin.test.Test
import kotlin.test.assertEquals

class LoginFieldPlatformTest {
    @Test
    fun credentialInputKeepsAsciiPrintableCharacters() {
        assertEquals(
            "student.01@example.com P@ssw0rd!",
            "student.01@example.com P@ssw0rd!".sanitizeDesktopCredentialInput(),
        )
    }

    @Test
    fun credentialInputRejectsChineseAndControlCharacters() {
        assertEquals(
            "abc123",
            "a中b\n文c\t123".sanitizeDesktopCredentialInput(),
        )
    }
}
