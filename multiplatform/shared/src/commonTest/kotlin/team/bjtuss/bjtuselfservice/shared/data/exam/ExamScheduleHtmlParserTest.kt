package team.bjtuss.bjtuselfservice.shared.data.exam

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ExamScheduleHtmlParserTest {
    @Test
    fun parsesFiveDisplayedFieldsAndNormalizesWhitespace() {
        val result = assertIs<ExamScheduleParseResult.Success>(
            parseExamScheduleTable(examTable(examRow("期末考试", "高等数学"))),
        )

        val exam = result.exams.single()
        assertEquals("期末考试", exam.examType)
        assertEquals("高等数学", exam.courseName)
        assertEquals("2026-01-10 08:00 思源101", exam.examTimeAndPlace)
        assertEquals("正常", exam.examStatus)
        assertEquals("座位 12", exam.detail)
    }

    @Test
    fun emptyBodyIsValidButMissingOrMalformedTableFails() {
        assertTrue(
            assertIs<ExamScheduleParseResult.Success>(
                parseExamScheduleTable("<table><tbody></tbody></table>"),
            ).exams.isEmpty(),
        )
        assertEquals(
            ExamScheduleParseFailure.TABLE_MISSING,
            assertIs<ExamScheduleParseResult.Failure>(
                parseExamScheduleTable("<main>none</main>"),
            ).reason,
        )
        val failure = assertIs<ExamScheduleParseResult.Failure>(
            parseExamScheduleTable("<table><tbody><tr><td>private</td></tr></tbody></table>"),
        )
        assertEquals(ExamScheduleParseFailure.MALFORMED_ROW, failure.reason)
        assertTrue("private" !in failure.toString())
    }
}

internal fun examTable(vararg rows: String): String =
    "<table><tbody>${rows.joinToString("")}</tbody></table>"

internal fun examRow(type: String, course: String): String = """
    <tr><td>1</td><td>$type</td><td>$course</td>
    <td>2026-01-10 08:00
    思源101</td><td>正常</td><td>座位 12</td></tr>
""".trimIndent()
