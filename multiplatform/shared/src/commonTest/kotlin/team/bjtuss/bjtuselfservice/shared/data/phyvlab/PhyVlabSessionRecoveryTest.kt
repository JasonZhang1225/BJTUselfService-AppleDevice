package team.bjtuss.bjtuselfservice.shared.data.phyvlab

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.coroutines.startCoroutine
import team.bjtuss.bjtuselfservice.shared.auth.CaptchaRecognition
import team.bjtuss.bjtuselfservice.shared.auth.CaptchaRecognitionResult
import team.bjtuss.bjtuselfservice.shared.auth.CaptchaRecognizer
import team.bjtuss.bjtuselfservice.shared.auth.Credentials
import team.bjtuss.bjtuselfservice.shared.auth.SchoolLoginProtocol
import team.bjtuss.bjtuselfservice.shared.network.SchoolHttpRequest
import team.bjtuss.bjtuselfservice.shared.network.SchoolHttpResponse
import team.bjtuss.bjtuselfservice.shared.network.SchoolHttpTransport

class PhyVlabSessionRecoveryTest {
    @Test
    fun recoveryPerformsFreshCasChallengeAndAuthentication() = runSuspend {
        val transport = QueueTransport(
            response(
                "https://cas.bjtu.edu.cn/auth/login/?next=%2Fauth%2Fsso%2F%3Fnext%3D%2F",
            ),
            response(
                "https://cas.bjtu.edu.cn/auth/login/?next=%2Fauth%2Fsso%2F%3Fnext%3D%2F",
                """
                    <form id="login">
                      <input name="csrfmiddlewaretoken" value="csrf">
                      <input id="id_captcha_0" value="cap">
                    </form>
                """.trimIndent(),
            ),
            response("https://cas.bjtu.edu.cn/image/cap/", body = "fake-image"),
            response(
                "https://mis.bjtu.edu.cn/home",
                """
                    <section class="name_right"><h3><a>测试用户，欢迎</a></h3>
                      <div class="nr_con"><span>身份：学生</span><span>部门：测试学院</span></div>
                    </section>
                """.trimIndent(),
            ),
        )
        val recovery = PhyVlabSessionRecovery(
            protocol = SchoolLoginProtocol(transport),
            captchaRecognizer = CaptchaRecognizer {
                CaptchaRecognitionResult.Success(CaptchaRecognition("1+1=", "2", 1f))
            },
            credentialsProvider = { Credentials("student", "secret") },
        )

        assertTrue(recovery.attempt())
        assertTrue(transport.requests.first().url.contains("auth%2Fsso"))
        assertFalse(transport.requests.last().toString().contains("secret"))
    }

    @Test
    fun recoveryDoesNotAttemptWithoutInMemoryCredentials() = runSuspend {
        val transport = QueueTransport()
        val recovery = PhyVlabSessionRecovery(
            protocol = SchoolLoginProtocol(transport),
            captchaRecognizer = CaptchaRecognizer {
                CaptchaRecognitionResult.Failed(
                    team.bjtuss.bjtuselfservice.shared.auth.CaptchaRecognitionFailure.MODEL_UNAVAILABLE,
                )
            },
            credentialsProvider = { null },
        )

        assertFalse(recovery.attempt())
        assertTrue(transport.requests.isEmpty())
    }

    private class QueueTransport(vararg responses: SchoolHttpResponse) : SchoolHttpTransport {
        private val queue = responses.toMutableList()
        val requests = mutableListOf<SchoolHttpRequest>()

        override suspend fun execute(request: SchoolHttpRequest): SchoolHttpResponse {
            requests += request
            return queue.removeFirst()
        }

        override fun clearSession() = Unit
    }

    private fun response(finalUrl: String, body: String = "", status: Int = 200) = SchoolHttpResponse(
        statusCode = status,
        finalUrl = finalUrl,
        body = body.encodeToByteArray(),
    )
}

private fun runSuspend(block: suspend () -> Unit) {
    var failure: Throwable? = null
    block.startCoroutine(
        object : kotlin.coroutines.Continuation<Unit> {
            override val context = kotlin.coroutines.EmptyCoroutineContext
            override fun resumeWith(result: Result<Unit>) {
                failure = result.exceptionOrNull()
            }
        },
    )
    failure?.let { throw it }
}
