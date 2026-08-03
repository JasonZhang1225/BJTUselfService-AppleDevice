package team.bjtuss.bjtuselfservice.shared.data.grade

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import team.bjtuss.bjtuselfservice.shared.network.SchoolHttpRequest
import team.bjtuss.bjtuselfservice.shared.network.SchoolHttpResponse
import team.bjtuss.bjtuselfservice.shared.network.SchoolHttpTransport

class GradeRemoteDataSourceTest {
    @Test
    fun requestsBothTypesAndDeduplicatesStableTriple() = runBlocking {
        val html = table(row("课程A", "95"))
        val transport = QueueTransport(
            mutableListOf(
                response(html),
                response(html),
            ),
        )

        val grades = SchoolGradeRemoteDataSource(transport, requestDelayMillis = 0).fetchGrades()

        assertEquals(1, grades.size)
        assertEquals(2, transport.requests.size)
        assertTrue(transport.requests[0].url.endsWith("ctype=ln"))
        assertTrue(transport.requests[1].url.endsWith("ctype=lr"))
        assertEquals("aa.bjtu.edu.cn", transport.requests[0].headers["Host"])
    }

    @Test
    fun redirectAwayFromAcademicSystemFailsAsExpiredSession() {
        val error = assertFailsWith<GradeRemoteException> {
            runBlocking {
                SchoolGradeRemoteDataSource(
                    QueueTransport(
                        mutableListOf(
                            response(table(row("课程A", "95"))).copy(
                                finalUrl = "https://cas.bjtu.edu.cn/auth/login/",
                            ),
                        ),
                    ),
                    requestDelayMillis = 0,
                ).fetchGrades()
            }
        }

        assertEquals(GradeRemoteFailure.SESSION_EXPIRED, error.reason)
        assertTrue("课程A" !in error.toString())
    }

    private class QueueTransport(
        private val responses: MutableList<SchoolHttpResponse>,
    ) : SchoolHttpTransport {
        val requests = mutableListOf<SchoolHttpRequest>()

        override suspend fun execute(request: SchoolHttpRequest): SchoolHttpResponse {
            requests += request
            return responses.removeAt(0)
        }

        override fun clearSession() = Unit
    }

    private fun response(html: String) = SchoolHttpResponse(
        statusCode = 200,
        finalUrl = "https://aa.bjtu.edu.cn/score/scores/stu/view/",
        body = html.encodeToByteArray(),
    )

    private fun table(vararg rows: String): String =
        "<table><tr><th>header</th></tr>${rows.joinToString("")}</table>"

    private fun row(name: String, score: String): String = """
        <tr><td>1</td><td>2025-2026-1</td><td>$name</td><td>2.0</td>
        <td>$score</td><td>-</td><td>教师</td><td></td></tr>
    """.trimIndent()
}
