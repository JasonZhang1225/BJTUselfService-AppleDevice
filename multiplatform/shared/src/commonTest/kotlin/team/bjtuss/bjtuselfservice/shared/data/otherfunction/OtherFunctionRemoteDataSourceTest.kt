package team.bjtuss.bjtuselfservice.shared.data.otherfunction

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import team.bjtuss.bjtuselfservice.shared.domain.otherfunction.ReportCardLanguage
import team.bjtuss.bjtuselfservice.shared.network.SchoolHttpRequest
import team.bjtuss.bjtuselfservice.shared.network.SchoolHttpResponse
import team.bjtuss.bjtuselfservice.shared.network.SchoolHttpTransport

class OtherFunctionRemoteDataSourceTest {

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
