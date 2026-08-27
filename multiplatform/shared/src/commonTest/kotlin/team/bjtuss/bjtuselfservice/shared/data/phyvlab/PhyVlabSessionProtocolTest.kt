package team.bjtuss.bjtuselfservice.shared.data.phyvlab

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import team.bjtuss.bjtuselfservice.shared.network.SchoolHttpRequest
import team.bjtuss.bjtuselfservice.shared.network.SchoolHttpResponse
import team.bjtuss.bjtuselfservice.shared.network.SchoolHttpTransport

class PhyVlabSessionProtocolTest {
    @Test
    fun authenticatedLoginPageIsReadyWithoutOauth() = runBlocking {
        val transport = ProtocolQueueTransport(
            SchoolHttpResponse(
                200,
                "https://phyvlab.bjtu.edu.cn/login/index.php",
                body = """
                    <html><body>
                      <a href="/login/logout.php?sesskey=abc">退出登录</a>
                      <a href="/my">个人主页</a>
                    </body></html>
                """.trimIndent().encodeToByteArray(),
            ),
        )
        val result = PhyVlabSessionProtocol(transport).establishSession()

        assertIs<PhyVlabSessionResult.Ready>(result)
        assertEquals(1, transport.requests.size)
    }

    @Test
    fun casLoginFormMeansBrowserAuthenticationRequired() = runBlocking {
        val transport = ProtocolQueueTransport(
            SchoolHttpResponse(
                200,
                "https://phyvlab.bjtu.edu.cn/login/index.php",
                body = loginPageHtml().encodeToByteArray(),
            ),
            SchoolHttpResponse(
                302,
                "https://phyvlab.bjtu.edu.cn/auth/oauth2/login.php?id=1&sesskey=sesssecret&wantsurl=%2F",
                headers = mapOf(
                    "Location" to listOf(
                        "https://cas.bjtu.edu.cn/o/authorize/?client_id=fake&response_type=code&redirect_uri=https%3A%2F%2Fphyvlab.bjtu.edu.cn%2Fadmin%2Foauth2callback.php",
                    ),
                ),
            ),
            SchoolHttpResponse(
                200,
                "https://cas.bjtu.edu.cn/auth/login/?next=%2Fo%2Fauthorize%2F",
                body = """
                    <form id="login">
                        <input name="csrfmiddlewaretoken" value="csrf">
                        <input id="id_captcha_0" value="captcha">
                        <input name="loginname">
                        <input name="password">
                    </form>
                """.trimIndent().encodeToByteArray(),
            ),
        )
        val result = PhyVlabSessionProtocol(transport).establishSession()

        assertEquals(PhyVlabSessionResult.CasLoginRequired, result)
        assertEquals(
            "https://phyvlab.bjtu.edu.cn/auth/oauth2/login.php?id=1&sesskey=sesssecret",
            transport.requests[1].url,
        )
    }

    @Test
    fun dynamicOauthHrefIsUsedWhenMoodleConfigIsAbsent() = runBlocking {
        val transport = ProtocolQueueTransport(
            SchoolHttpResponse(
                200,
                "https://phyvlab.bjtu.edu.cn/login/index.php",
                body = """
                    <a href="/auth/oauth2/login.php?id=1&amp;sesskey=hrefsecret&amp;wantsurl=%2F">
                        北京交通大学统一身份认证
                    </a>
                """.trimIndent().encodeToByteArray(),
            ),
            SchoolHttpResponse(
                302,
                "https://phyvlab.bjtu.edu.cn/auth/oauth2/login.php?id=1&amp;sesskey=hrefsecret",
                headers = mapOf(
                    "Location" to listOf("https://cas.bjtu.edu.cn/auth/login/?next=%2Fo%2Fauthorize%2F"),
                ),
            ),
            SchoolHttpResponse(
                200,
                "https://cas.bjtu.edu.cn/auth/login/?next=%2Fo%2Fauthorize%2F",
                body = """
                    <form id="login">
                        <input name="csrfmiddlewaretoken" value="csrf">
                        <input id="id_captcha_0" value="captcha">
                    </form>
                """.trimIndent().encodeToByteArray(),
            ),
        )

        val result = PhyVlabSessionProtocol(transport).establishSession()

        assertEquals(PhyVlabSessionResult.CasLoginRequired, result)
        assertEquals(
            "https://phyvlab.bjtu.edu.cn/auth/oauth2/login.php?id=1&sesskey=hrefsecret&wantsurl=%2F",
            transport.requests[1].url,
        )
    }

    @Test
    fun initialLoginRedirectIsFollowedBeforeParsing() = runBlocking {
        val transport = ProtocolQueueTransport(
            SchoolHttpResponse(
                302,
                "https://phyvlab.bjtu.edu.cn/login/index.php",
                headers = mapOf("Location" to listOf("/login/index.php?lang=zh_cn")),
            ),
            SchoolHttpResponse(
                200,
                "https://phyvlab.bjtu.edu.cn/login/index.php?lang=zh_cn",
                body = loginPageHtml().encodeToByteArray(),
            ),
            SchoolHttpResponse(
                302,
                "https://phyvlab.bjtu.edu.cn/auth/oauth2/login.php?id=1&sesskey=sesssecret&wantsurl=%2F",
                headers = mapOf(
                    "Location" to listOf("https://cas.bjtu.edu.cn/auth/login/?next=%2Fo%2Fauthorize%2F"),
                ),
            ),
            SchoolHttpResponse(
                200,
                "https://cas.bjtu.edu.cn/auth/login/?next=%2Fo%2Fauthorize%2F",
                body = """
                    <form id="login">
                        <input name="csrfmiddlewaretoken" value="csrf">
                        <input id="id_captcha_0" value="captcha">
                    </form>
                """.trimIndent().encodeToByteArray(),
            ),
        )

        val result = PhyVlabSessionProtocol(transport).establishSession()

        assertEquals(PhyVlabSessionResult.CasLoginRequired, result)
        assertEquals(4, transport.requests.size)
    }

    @Test
    fun stopsAtForeignRedirectHost() = runBlocking {
        val transport = ProtocolQueueTransport(
            SchoolHttpResponse(
                200,
                "https://phyvlab.bjtu.edu.cn/login/index.php",
                body = loginPageHtml().encodeToByteArray(),
            ),
            SchoolHttpResponse(
                302,
                "https://phyvlab.bjtu.edu.cn/auth/oauth2/login.php?id=1&sesskey=sesssecret&wantsurl=%2F",
                headers = mapOf(
                    "Location" to listOf("https://evil.example.com/steal"),
                ),
            ),
        )
        val result = PhyVlabSessionProtocol(transport).establishSession()

        assertIs<PhyVlabSessionResult.Failed>(result)
        assertEquals(2, transport.requests.size)
    }

    @Test
    fun activeCasSessionLandsOnPhyVlabAfterOauth() = runBlocking {
        val transport = ProtocolQueueTransport(
            SchoolHttpResponse(
                200,
                "https://phyvlab.bjtu.edu.cn/login/index.php",
                body = loginPageHtml().encodeToByteArray(),
            ),
            SchoolHttpResponse(
                302,
                "https://phyvlab.bjtu.edu.cn/auth/oauth2/login.php?id=1&sesskey=sesssecret&wantsurl=%2F",
                headers = mapOf(
                    "Location" to listOf(
                        "https://cas.bjtu.edu.cn/o/authorize/?client_id=fake&response_type=code&redirect_uri=https%3A%2F%2Fphyvlab.bjtu.edu.cn%2Fadmin%2Foauth2callback.php",
                    ),
                ),
            ),
            SchoolHttpResponse(
                302,
                "https://cas.bjtu.edu.cn/o/authorize/?client_id=fake&response_type=code&redirect_uri=https%3A%2F%2Fphyvlab.bjtu.edu.cn%2Fadmin%2Foauth2callback.php",
                headers = mapOf(
                    "Location" to listOf(
                        "https://phyvlab.bjtu.edu.cn/admin/oauth2callback.php?code=fake&state=fake",
                    ),
                ),
            ),
            SchoolHttpResponse(
                200,
                "https://phyvlab.bjtu.edu.cn/admin/oauth2callback.php?code=fake&state=fake",
                body = "<a href=\"/login/logout.php\">退出登录</a>".encodeToByteArray(),
            ),
        )
        val result = PhyVlabSessionProtocol(transport).establishSession()

        assertIs<PhyVlabSessionResult.Ready>(result)
        assertEquals(4, transport.requests.size)
    }

    @Test
    fun oauthCallbackWithoutAuthenticatedLandingIsNotReady() = runBlocking {
        val transport = ProtocolQueueTransport(
            SchoolHttpResponse(
                200,
                "https://phyvlab.bjtu.edu.cn/login/index.php",
                body = loginPageHtml().encodeToByteArray(),
            ),
            SchoolHttpResponse(
                302,
                "https://phyvlab.bjtu.edu.cn/auth/oauth2/login.php?id=1&sesskey=sesssecret&wantsurl=%2F",
                headers = mapOf(
                    "Location" to listOf(
                        "https://cas.bjtu.edu.cn/o/authorize/?client_id=fake&response_type=code&redirect_uri=https%3A%2F%2Fphyvlab.bjtu.edu.cn%2Fadmin%2Foauth2callback.php",
                    ),
                ),
            ),
            SchoolHttpResponse(
                302,
                "https://cas.bjtu.edu.cn/o/authorize/?client_id=fake&response_type=code&redirect_uri=https%3A%2F%2Fphyvlab.bjtu.edu.cn%2Fadmin%2Foauth2callback.php",
                headers = mapOf(
                    "Location" to listOf(
                        "https://phyvlab.bjtu.edu.cn/admin/oauth2callback.php?code=fake&state=fake",
                    ),
                ),
            ),
            SchoolHttpResponse(
                200,
                "https://phyvlab.bjtu.edu.cn/admin/oauth2callback.php?code=fake&state=fake",
                body = "<html>callback</html>".encodeToByteArray(),
            ),
            SchoolHttpResponse(
                200,
                "https://phyvlab.bjtu.edu.cn/my/courses.php",
                body = loginPageHtml().encodeToByteArray(),
            ),
        )

        val result = PhyVlabSessionProtocol(transport).establishSession()

        val failed = assertIs<PhyVlabSessionResult.Failed>(result)
        assertEquals(PhyVlabRemoteFailure.SESSION_EXPIRED, failed.reason)
        assertEquals("phyvlab-login-session-missing", failed.detail)
        assertEquals(5, transport.requests.size)
    }

    private fun loginPageHtml(): String = """
        <html><head><script>
            var M = {}; M.cfg = {"wwwroot":"https://phyvlab.bjtu.edu.cn","sesskey":"sesssecret"};
        </script></head><body>
            <a href="/auth/oauth2/login.php?id=1">北京交通大学统一身份认证</a>
        </body></html>
    """.trimIndent()

    private class ProtocolQueueTransport(vararg responses: SchoolHttpResponse) : SchoolHttpTransport {
        private val queue = responses.toMutableList()
        val requests = mutableListOf<SchoolHttpRequest>()

        override suspend fun execute(request: SchoolHttpRequest): SchoolHttpResponse = queue.removeFirst()

        override suspend fun executeWithoutRedirects(request: SchoolHttpRequest): SchoolHttpResponse {
            requests += request
            return queue.removeFirst()
        }

        override fun clearSession() = Unit
    }
}
