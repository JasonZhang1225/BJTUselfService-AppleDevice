package team.bjtuss.bjtuselfservice.shared.data.exam

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import team.bjtuss.bjtuselfservice.shared.network.SchoolHttpRequest
import team.bjtuss.bjtuselfservice.shared.network.SchoolHttpResponse
import team.bjtuss.bjtuselfservice.shared.network.SchoolHttpTransport

class ExamScheduleRemoteDataSourceTest {
    @Test
    fun requestsExamEndpointAndParsesRows() = runBlocking {
        val transport = FakeTransport(response(examTable(examRow("期末考试", "高等数学"))))

        val exams = SchoolExamScheduleRemoteDataSource(
            transport,
            requestDelayMillis = 0,
        ).fetchExams()

        assertEquals(1, exams.size)
        assertTrue(transport.request.url.endsWith("/examine/examplanstudent/stulist/"))
        assertEquals("aa.bjtu.edu.cn", transport.request.headers["Host"])
    }

    @Test
    fun redirectToCasIsSessionExpiryWithoutBodyLeak() {
        val error = assertFailsWith<ExamScheduleRemoteException> {
            runBlocking {
                SchoolExamScheduleRemoteDataSource(
                    FakeTransport(
                        response(examTable(examRow("敏感类型", "敏感课程"))).copy(
                            finalUrl = "https://cas.bjtu.edu.cn/auth/login/",
                        ),
                    ),
                    requestDelayMillis = 0,
                ).fetchExams()
            }
        }

        assertEquals(ExamScheduleRemoteFailure.SESSION_EXPIRED, error.reason)
        assertTrue("敏感课程" !in error.toString())
    }

    private class FakeTransport(private val response: SchoolHttpResponse) : SchoolHttpTransport {
        lateinit var request: SchoolHttpRequest
        override suspend fun execute(request: SchoolHttpRequest): SchoolHttpResponse {
            this.request = request
            return response
        }
        override fun clearSession() = Unit
    }

    private fun response(html: String) = SchoolHttpResponse(
        statusCode = 200,
        finalUrl = "https://aa.bjtu.edu.cn/examine/examplanstudent/stulist/",
        body = html.encodeToByteArray(),
    )
}
