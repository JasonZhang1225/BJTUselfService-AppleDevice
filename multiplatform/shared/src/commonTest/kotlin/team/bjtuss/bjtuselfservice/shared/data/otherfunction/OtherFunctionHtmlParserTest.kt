package team.bjtuss.bjtuselfservice.shared.data.otherfunction

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import team.bjtuss.bjtuselfservice.shared.domain.otherfunction.ReportCardLanguage

class OtherFunctionHtmlParserTest {

    @Test
    fun parsesCalendarPostfixWithDoubleQuotes() {
        val html = """
            <html><head><script>
                var data = [{
                    title: "2024-2025校历",
                    url: "/New/Semester/2024-2025校历.pdf",
                    start: "2024-09-01"
                }];
            </script></head><body></body></html>
        """.trimIndent()

        val result = parseSchoolCalendarPostfix(html)

        assertTrue(result is CalendarUrlParseResult.Success)
        assertEquals("/New/Semester/2024-2025校历.pdf", result.postfix)
    }

    @Test
    fun parsesCalendarPostfixWithSingleQuotes() {
        val html = """
            <script>
                var rows = [ { title: '校历', url: '/Upload/Calendar/2025.pdf' } ];
            </script>
        """.trimIndent()

        val result = parseSchoolCalendarPostfix(html)

        assertTrue(result is CalendarUrlParseResult.Success)
        assertEquals("/Upload/Calendar/2025.pdf", result.postfix)
    }

    @Test
    fun reportsMissingScriptAsFailure() {
        val result = parseSchoolCalendarPostfix("<html><body><p>no script</p></body></html>")

        assertEquals(
            CalendarUrlParseResult.Failure(CalendarParseFailure.MISSING_SCRIPT),
            result,
        )
    }

    @Test
    fun reportsMissingUrlFieldAsFailure() {
        val html = "<script>var rows = [{ title: \"校历\" }];</script>"

        val result = parseSchoolCalendarPostfix(html)

        assertEquals(
            CalendarUrlParseResult.Failure(CalendarParseFailure.MISSING_URL_FIELD),
            result,
        )
    }

    @Test
    fun reportsUnclosedQuoteAsMissingUrlField() {
        val html = "<script>var rows = [{ url: \"/New/Semester/x.pdf }];</script>"

        val result = parseSchoolCalendarPostfix(html)

        assertEquals(
            CalendarUrlParseResult.Failure(CalendarParseFailure.MISSING_URL_FIELD),
            result,
        )
    }

    @Test
    fun reportsEmptyUrlValueAsMalformed() {
        val html = "<script>var rows = [{ url: \"\" }];</script>"

        val result = parseSchoolCalendarPostfix(html)

        assertEquals(
            CalendarUrlParseResult.Failure(CalendarParseFailure.MALFORMED_URL_VALUE),
            result,
        )
    }

    @Test
    fun keepsRelativePostfixForDomainBoundaryCheck() {
        val html = """<script>var rows = [{ url: "/a/b.pdf" }];</script>"""

        val result = parseSchoolCalendarPostfix(html)

        assertTrue(result is CalendarUrlParseResult.Success)
        assertTrue(result.postfix.startsWith("/"))
    }

    @Test
    fun findsUrlFieldAcrossMultipleScriptBlocks() {
        // 真实 bksy 页面有多个 script 块，url 字段不一定在第一个。
        val html = """
            <html><head>
            <script src="/js/jquery.js"></script>
            <script src="/js/layui.js"></script>
            <script>var helper = 1;</script>
            <script>
                var data = [{ title: "校历", url: "/New/Semester/2024-2025校历.pdf" }];
            </script>
            </head><body></body></html>
        """.trimIndent()

        val result = parseSchoolCalendarPostfix(html)

        assertTrue(result is CalendarUrlParseResult.Success)
        assertEquals("/New/Semester/2024-2025校历.pdf", result.postfix)
    }

    @Test
    fun buildsChineseReportCardUrl() {
        val url = reportCardDownloadUrl(ReportCardLanguage.CHINESE)

        assertEquals(
            "https://aa.bjtu.edu.cn/score/scorecard/stu/5201314/download_pdf/?type=card_cn_sign&has_advance_query=",
            url,
        )
    }

    @Test
    fun buildsEnglishReportCardUrl() {
        val url = reportCardDownloadUrl(ReportCardLanguage.ENGLISH)

        assertEquals(
            "https://aa.bjtu.edu.cn/score/scorecard/stu/5201314/download_pdf/?type=card_en_sign&has_advance_query=",
            url,
        )
    }
}
