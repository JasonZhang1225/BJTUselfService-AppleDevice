package team.bjtuss.bjtuselfservice.shared.feature.mailbox

import kotlinx.coroutines.runBlocking
import team.bjtuss.bjtuselfservice.shared.network.SchoolHttpRequest
import team.bjtuss.bjtuselfservice.shared.network.SchoolHttpResponse
import team.bjtuss.bjtuselfservice.shared.network.SchoolHttpTransport
import team.bjtuss.bjtuselfservice.shared.network.SchoolSessionCookie
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

class MailboxScreenModelTest {
    @Test
    fun preparesMailboxWithNarrowedMisCookies() {
        runBlocking {
            val model = MailboxScreenModel(
                CookieTransport(listOf(SchoolSessionCookie("session", "secret", "/module/"))),
            )
            model.initialize()
            val ready = assertIs<MailboxUiState.Ready>(model.state.value)
            assertEquals("https://mis.bjtu.edu.cn/module/module/26/", ready.request.url)
            assertEquals("mis.bjtu.edu.cn", ready.request.cookies.single().domain)
            assertEquals("/module/", ready.request.cookies.single().path)
            assertFalse(ready.request.toString().contains("secret"))
        }
    }

    @Test
    fun emptySessionIsReported() {
        runBlocking {
            val model = MailboxScreenModel(CookieTransport(emptyList()))
            model.refresh()
            assertIs<MailboxUiState.SessionUnavailable>(model.state.value)
        }
    }

    @Test
    fun cookieReadFailureIsReported() {
        runBlocking {
            val model = MailboxScreenModel(CookieTransport(failure = true))
            model.refresh()
            assertIs<MailboxUiState.SessionUnavailable>(model.state.value)
        }
    }
}

private class CookieTransport(
    private val cookies: List<SchoolSessionCookie> = emptyList(),
    private val failure: Boolean = false,
) : SchoolHttpTransport {
    override suspend fun execute(request: SchoolHttpRequest) = SchoolHttpResponse(200, request.url)

    override suspend fun sessionCookiesFor(url: String): List<SchoolSessionCookie> {
        if (failure) error("cookie storage")
        return cookies
    }

    override fun clearSession() = Unit
}
