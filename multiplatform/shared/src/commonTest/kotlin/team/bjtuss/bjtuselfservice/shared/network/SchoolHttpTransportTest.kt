package team.bjtuss.bjtuselfservice.shared.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SchoolHttpTransportTest {
    @Test
    fun requestToStringRedactsCredentialsCaptchaAndCookies() {
        val request = SchoolHttpRequest(
            method = SchoolHttpMethod.POST,
            url = "https://example.test/login?ticket=url-secret",
            headers = mapOf(
                "Cookie" to "session-secret",
                "sessionid" to "smart-session-secret",
                "Referer" to "https://example.test?ticket=referer-secret",
            ),
            formFields = mapOf(
                "loginname" to "student-fixture",
                "password" to "password-secret",
                "captcha_1" to "captcha-secret",
                "csrfmiddlewaretoken" to "csrf-secret",
                "sesskey" to "moodle-sesskey-secret",
            ),
        ).toString()

        assertFalse(request.contains("student-fixture"))
        assertFalse(request.contains("session-secret"))
        assertFalse(request.contains("smart-session-secret"))
        assertFalse(request.contains("url-secret"))
        assertFalse(request.contains("referer-secret"))
        assertFalse(request.contains("password-secret"))
        assertFalse(request.contains("captcha-secret"))
        assertFalse(request.contains("csrf-secret"))
        assertFalse(request.contains("moodle-sesskey-secret"))
    }

    @Test
    fun responseToStringDoesNotPrintCookieValuesOrBody() {
        val response = SchoolHttpResponse(
            statusCode = 200,
            finalUrl = "https://example.test/home",
            headers = mapOf("Set-Cookie" to listOf("session-secret")),
            body = "private-profile".encodeToByteArray(),
        ).toString()

        assertTrue(response.contains("Set-Cookie"))
        assertFalse(response.contains("session-secret"))
        assertFalse(response.contains("private-profile"))
    }

    @Test
    fun multipartRequestToStringRedactsFileNameBytesAndSubmissionContent() {
        val request = SchoolHttpRequest(
            method = SchoolHttpMethod.POST,
            url = "https://example.test/upload",
            multipartFiles = listOf(
                SchoolMultipartFile(
                    fieldName = "file",
                    fileName = "private-homework.pdf",
                    bytes = "private-file-body".encodeToByteArray(),
                ),
            ),
        ).toString()
        val submission = SchoolHttpRequest(
            method = SchoolHttpMethod.POST,
            url = "https://example.test/submit",
            formFields = mapOf(
                "content" to "private-homework-comment",
                "fileList" to "private-server-file-metadata",
            ),
        ).toString()

        assertFalse("private-homework.pdf" in request)
        assertFalse("private-file-body" in request)
        assertTrue("17 bytes" in request)
        assertFalse("private-homework-comment" in submission)
        assertFalse("private-server-file-metadata" in submission)
    }

    @Test
    fun bodyTextGbkDecodesGbkBytesAndFallsBackForUtf8() {
        // GB18030 编码的“小组全部成员一起协作的照片”字节序列
        val gbkBytes = byteArrayOf(
            0xD0.toByte(), 0xA1.toByte(), 0xD7.toByte(), 0xE9.toByte(),
            0xC8.toByte(), 0xAB.toByte(), 0xB2.toByte(), 0xBF.toByte(),
            0xB3.toByte(), 0xC9.toByte(), 0xD4.toByte(), 0xB1.toByte(),
            0xD2.toByte(), 0xBB.toByte(), 0xC6.toByte(), 0xF0.toByte(),
            0xD0.toByte(), 0xAD.toByte(), 0xD7.toByte(), 0xF7.toByte(),
            0xB5.toByte(), 0xC4.toByte(), 0xD5.toByte(), 0xD5.toByte(),
            0xC6.toByte(), 0xAC.toByte(),
        )
        val gbk = SchoolHttpResponse(
            statusCode = 200,
            finalUrl = "https://example.test/x",
            body = gbkBytes,
        ).bodyTextGbk()
        assertEquals("小组全部成员一起协作的照片", gbk)

        // UTF-8 输入在不支持 GB18030 时也能安全回退，不抛异常
        val utf8 = SchoolHttpResponse(
            statusCode = 200,
            finalUrl = "https://example.test/x",
            body = "hello".encodeToByteArray(),
        ).bodyTextGbk()
        assertEquals("hello", utf8)
    }

    @Test
    fun responseHeaderLookupIsCaseInsensitive() {
        val response = SchoolHttpResponse(
            statusCode = 302,
            finalUrl = "https://example.test/x",
            headers = mapOf("Location" to listOf("https://example.test/next")),
        )
        assertEquals("https://example.test/next", response.header("location"))
        assertEquals("https://example.test/next", response.header("LOCATION"))
        assertEquals(null, response.header("Missing"))
    }
}
