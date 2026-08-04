package team.bjtuss.bjtuselfservice.shared.data.grade

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import team.bjtuss.bjtuselfservice.shared.domain.grade.CourseType
import team.bjtuss.bjtuselfservice.shared.network.SchoolHttpRequest
import team.bjtuss.bjtuselfservice.shared.network.SchoolHttpResponse
import team.bjtuss.bjtuselfservice.shared.network.SchoolHttpTransport

class TrainingProgramRemoteDataSourceTest {
    @Test
    fun mergesCourseTypesAcrossAllStuviewDetailPages() = runBlocking {
        val transport = QueueTransport(
            mutableListOf(
                response(
                    """
                    <table>
                        <tr><td><a href="/training/training/program/stuview/6449/">主修</a></td></tr>
                        <tr><td><a href="stuview/7001/">辅修</a></td></tr>
                    </table>
                    """.trimIndent(),
                ),
                response(programTable("C312009B" to "必修", "M710033B" to "限选")),
                response(programTable("S1100120A" to "任选")),
            ),
        )

        val courseTypes = SchoolTrainingProgramRemoteDataSource(
            transport,
            requestDelayMillis = 0,
        ).fetchCourseTypes()

        assertEquals(
            mapOf(
                "C312009B" to CourseType.REQUIRED,
                "M710033B" to CourseType.LIMITED,
                "S1100120A" to CourseType.ELECTIVE,
            ),
            courseTypes,
        )
        assertEquals(3, transport.requests.size)
        assertEquals(
            "https://aa.bjtu.edu.cn/training/training/program/",
            transport.requests[0].url,
        )
        assertTrue(transport.requests[1].url.endsWith("stuview/6449/"))
        assertTrue(transport.requests[2].url.endsWith("stuview/7001/"))
        assertEquals("aa.bjtu.edu.cn", transport.requests[0].headers["Host"])
    }

    @Test
    fun listPageWithoutProgramLinksFailsAsMalformed() {
        val error = assertFailsWith<GradeRemoteException> {
            runBlocking {
                SchoolTrainingProgramRemoteDataSource(
                    QueueTransport(mutableListOf(response("<html>fixture-secret</html>"))),
                    requestDelayMillis = 0,
                ).fetchCourseTypes()
            }
        }

        assertEquals(GradeRemoteFailure.MALFORMED_RESPONSE, error.reason)
        assertTrue("fixture-secret" !in error.toString())
    }

    @Test
    fun detailPageWithoutCourseTableFailsAsMalformed() {
        val error = assertFailsWith<GradeRemoteException> {
            runBlocking {
                SchoolTrainingProgramRemoteDataSource(
                    QueueTransport(
                        mutableListOf(
                            response(
                                """<a href="/training/training/program/stuview/6449/">主修</a>""",
                            ),
                            response("<html><table><tr><td>没有课程表</td></tr></table></html>"),
                        ),
                    ),
                    requestDelayMillis = 0,
                ).fetchCourseTypes()
            }
        }

        assertEquals(GradeRemoteFailure.MALFORMED_RESPONSE, error.reason)
    }

    @Test
    fun redirectAwayFromAcademicSystemFailsAsExpiredSession() {
        val error = assertFailsWith<GradeRemoteException> {
            runBlocking {
                SchoolTrainingProgramRemoteDataSource(
                    QueueTransport(
                        mutableListOf(
                            response("""<a href="stuview/6449/">主修</a>""").copy(
                                finalUrl = "https://cas.bjtu.edu.cn/auth/login/",
                            ),
                        ),
                    ),
                    requestDelayMillis = 0,
                ).fetchCourseTypes()
            }
        }

        assertEquals(GradeRemoteFailure.SESSION_EXPIRED, error.reason)
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
        finalUrl = "https://aa.bjtu.edu.cn/training/training/program/",
        body = html.encodeToByteArray(),
    )

    private fun programTable(vararg courses: Pair<String, String>): String {
        val rows = courses.joinToString("") { (code, type) ->
            "<tr><td>课程名</td><td>$code</td><td>$type</td><td>2.0</td></tr>"
        }
        return "<table><tr><td>课程号</td><td>课程名</td><td>课程性质</td><td>学分</td></tr>$rows</table>"
    }
}
