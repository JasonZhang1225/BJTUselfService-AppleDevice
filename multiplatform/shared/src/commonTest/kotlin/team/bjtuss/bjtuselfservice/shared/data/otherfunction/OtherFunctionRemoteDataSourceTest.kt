package team.bjtuss.bjtuselfservice.shared.data.otherfunction

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import team.bjtuss.bjtuselfservice.shared.domain.otherfunction.ReportCardLanguage
import team.bjtuss.bjtuselfservice.shared.network.SchoolHttpRequest
import team.bjtuss.bjtuselfservice.shared.network.SchoolHttpResponse
import team.bjtuss.bjtuselfservice.shared.network.SchoolHttpTransport

class OtherFunctionRemoteDataSourceTest {

    @Test
    fun downloadsCalendarFromParsedPostfix() = runBlocking {
        val transport = QueueTransport(
            SchoolHttpResponse(
                statusCode = 200,
                finalUrl = "https://bksy.bjtu.edu.cn/Admin/SemesterTranPage.aspx",
                body = calendarPageHtml().encodeToByteArray(),
            ),
            SchoolHttpResponse(
                statusCode = 200,
                finalUrl = "https://bksy.bjtu.edu.cn/New/Semester/2024-2025校历.pdf",
                headers = mapOf("Content-Type" to listOf("application/pdf")),
                body = pdfBytes(),
            ),
        )
        val remote = SchoolOtherFunctionRemoteDataSource(transport)

        val file = remote.fetchCalendarFile()

        assertEquals("2024-2025校历.pdf", file.fileName)
        assertEquals("application/pdf", file.contentType)
        assertContentEquals(pdfBytes(), file.bytes)
        assertEquals(
            "https://bksy.bjtu.edu.cn/Admin/SemesterTranPage.aspx?noRemark=1",
            transport.requests[0].url,
        )
        assertEquals(
            "https://bksy.bjtu.edu.cn/New/Semester/2024-2025%E6%A0%A1%E5%8E%86.pdf",
            transport.requests[1].url,
        )
    }

    @Test
    fun rejectsCalendarPostfixPointingToForeignHost() = runBlocking {
        val transport = QueueTransport(
            SchoolHttpResponse(
                statusCode = 200,
                finalUrl = "https://bksy.bjtu.edu.cn/Admin/SemesterTranPage.aspx",
                body = "<script>var rows = [{ url: \"https://example.com/x.pdf\" }];</script>".encodeToByteArray(),
            ),
        )
        val remote = SchoolOtherFunctionRemoteDataSource(transport)

        val error = assertFailsWith<OtherFunctionRemoteException> {
            remote.fetchCalendarFile()
        }
        assertEquals(OtherFunctionRemoteFailure.PARSE, error.reason)
        assertEquals(1, transport.requests.size)
    }

    @Test
    fun reportsParseFailureWhenScriptMissingUrl() = runBlocking {
        val transport = QueueTransport(
            SchoolHttpResponse(
                statusCode = 200,
                finalUrl = "https://bksy.bjtu.edu.cn/Admin/SemesterTranPage.aspx",
                body = "<html><body>no script</body></html>".encodeToByteArray(),
            ),
        )
        val remote = SchoolOtherFunctionRemoteDataSource(transport)

        val error = assertFailsWith<OtherFunctionRemoteException> {
            remote.fetchCalendarFile()
        }
        assertEquals(OtherFunctionRemoteFailure.PARSE, error.reason)
    }

    @Test
    fun reportsLatestCalendarFileNameFromPage() = runBlocking {
        val transport = QueueTransport(
            SchoolHttpResponse(
                statusCode = 200,
                finalUrl = "https://bksy.bjtu.edu.cn/Admin/SemesterTranPage.aspx",
                body = calendarPageHtml().encodeToByteArray(),
            ),
        )
        val remote = SchoolOtherFunctionRemoteDataSource(transport)

        val name = remote.fetchCalendarFileName()

        assertEquals("2024-2025校历.pdf", name)
        assertEquals(
            "https://bksy.bjtu.edu.cn/Admin/SemesterTranPage.aspx?noRemark=1",
            transport.requests.single().url,
        )
    }

    @Test
    fun calendarFileNameDecodesEncodedPath() = runBlocking {
        val transport = QueueTransport(
            SchoolHttpResponse(
                statusCode = 200,
                finalUrl = "https://bksy.bjtu.edu.cn/Admin/SemesterTranPage.aspx",
                body = "<script>var rows = [{ url: \"/New/Semester/2024-2025%E6%A0%A1%E5%8E%86.pdf\" }];</script>"
                    .encodeToByteArray(),
            ),
        )
        val remote = SchoolOtherFunctionRemoteDataSource(transport)

        assertEquals("2024-2025校历.pdf", remote.fetchCalendarFileName())
    }

    @Test
    fun calendarFileNameParseFailureThrows() = runBlocking {
        val transport = QueueTransport(
            SchoolHttpResponse(
                statusCode = 200,
                finalUrl = "https://bksy.bjtu.edu.cn/Admin/SemesterTranPage.aspx",
                body = "<html><body>no script</body></html>".encodeToByteArray(),
            ),
        )
        val remote = SchoolOtherFunctionRemoteDataSource(transport)

        val error = assertFailsWith<OtherFunctionRemoteException> {
            remote.fetchCalendarFileName()
        }
        assertEquals(OtherFunctionRemoteFailure.PARSE, error.reason)
    }

    @Test
    fun downloadsChineseReportCardWithSession() = runBlocking {
        val transport = QueueTransport(
            SchoolHttpResponse(
                statusCode = 200,
                finalUrl = "https://aa.bjtu.edu.cn/score/scorecard/stu/5201314/download_pdf/",
                headers = mapOf("Content-Type" to listOf("application/pdf")),
                body = pdfBytes(),
            ),
        )
        val remote = SchoolOtherFunctionRemoteDataSource(transport)

        val file = remote.fetchReportCardFile(ReportCardLanguage.CHINESE)

        assertEquals("中文成绩单.pdf", file.fileName)
        assertEquals("application/pdf", file.contentType)
        assertContentEquals(pdfBytes(), file.bytes)
        assertEquals(
            "https://aa.bjtu.edu.cn/score/scorecard/stu/5201314/download_pdf/?type=card_cn_sign&has_advance_query=",
            transport.requests.single().url,
        )
    }

    @Test
    fun treatsHtmlResponseAsSessionExpiredForReportCard() = runBlocking {
        val transport = QueueTransport(
            SchoolHttpResponse(
                statusCode = 200,
                finalUrl = "https://aa.bjtu.edu.cn/score/scorecard/stu/5201314/download_pdf/",
                headers = mapOf("Content-Type" to listOf("text/html; charset=UTF-8")),
                body = "<html><body>login</body></html>".encodeToByteArray(),
            ),
        )
        val remote = SchoolOtherFunctionRemoteDataSource(transport)

        val error = assertFailsWith<OtherFunctionRemoteException> {
            remote.fetchReportCardFile(ReportCardLanguage.ENGLISH)
        }
        assertEquals(OtherFunctionRemoteFailure.SESSION_EXPIRED, error.reason)
    }

    @Test
    fun rejectsReportCardRedirectToForeignHost() = runBlocking {
        val transport = QueueTransport(
            SchoolHttpResponse(
                statusCode = 200,
                finalUrl = "https://example.com/score/scorecard/stu/5201314/download_pdf/",
                headers = mapOf("Content-Type" to listOf("application/pdf")),
                body = pdfBytes(),
            ),
        )
        val remote = SchoolOtherFunctionRemoteDataSource(transport)

        val error = assertFailsWith<OtherFunctionRemoteException> {
            remote.fetchReportCardFile(ReportCardLanguage.CHINESE)
        }
        assertEquals(OtherFunctionRemoteFailure.SESSION_EXPIRED, error.reason)
    }

    private fun calendarPageHtml(): String = """
        <html><head><script type="text/javascript">
            var data = [{
                title: "2024-2025校历",
                url: "/New/Semester/2024-2025校历.pdf",
                start: "2024-09-01"
            }];
        </script></head><body><div>校历</div></body></html>
    """.trimIndent()

    private fun pdfBytes(): ByteArray = "%PDF-1.4 fake-pdf-body".encodeToByteArray()

    private class QueueTransport(vararg responses: SchoolHttpResponse) : SchoolHttpTransport {
        private val queue = responses.toMutableList()
        val requests = mutableListOf<SchoolHttpRequest>()

        override suspend fun execute(request: SchoolHttpRequest): SchoolHttpResponse {
            requests += request
            return queue.removeFirst()
        }

        override fun clearSession() = Unit
    }
}
