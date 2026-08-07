package team.bjtuss.bjtuselfservice.shared.data.grade

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GradeHtmlParserTest {
    @Test
    fun parsesNumericAndLetterScoresAndDetail() {
        val result = assertIs<GradeTableParseResult.Success>(
            parseGradeTable(
                gradeTable(
                    row(
                        year = "2025 - 2026 - 1",
                        name = "[123456]高等数学[01]",
                        credits = "3.0",
                        score = "95",
                        teacher = "张 老师",
                        detail = "平时成绩(0.6)： 40&lt;br&gt;期末成绩(0.4)： 60&lt;br&gt;最终成绩： 95&lt;br&gt;备注信息： 无",
                    ),
                    row(
                        year = "2025-2026-2",
                        name = "[654321]大学物理[02]",
                        credits = "",
                        score = "B+",
                        teacher = "李老师",
                    ),
                ),
            ),
        )

        assertEquals(2, result.grades.size)
        assertEquals("2025-2026-1", result.grades[0].semester)
        assertEquals("A,95", result.grades[0].courseScore)
        assertEquals("张老师", result.grades[0].courseTeacher)
        // <br> 保留为换行，平时/期末分行
        assertTrue(result.grades[0].detail.contains("平时成绩"))
        assertTrue(result.grades[0].detail.contains('\n'))
        assertTrue(result.grades[0].detail.lines().any { it.startsWith("期末成绩") })
        assertEquals("B+,83", result.grades[1].courseScore)
        assertEquals("0.0", result.grades[1].courseCredits)
    }

    @Test
    fun formatGradeDetailBreaksKnownLabelsEvenWhenFlat() {
        val flat = "平时成绩(0.6)： 97 期末成绩(0.4)： 88 最终成绩： 93 备注信息： 无"
        val formatted = formatGradeDetailForDisplay(flat)
        assertEquals(
            listOf(
                "平时成绩(0.6)： 97",
                "期末成绩(0.4)： 88",
                "最终成绩： 93",
                "备注信息： 无",
            ),
            formatted.lines(),
        )
    }

    @Test
    fun rejectsMissingTableOrShortRowWithoutReturningHtml() {
        assertEquals(
            GradeTableParseResult.Failure(GradeParseFailure.TABLE_MISSING),
            parseGradeTable("<html>fixture-secret</html>"),
        )
        assertEquals(
            GradeTableParseResult.Failure(GradeParseFailure.MALFORMED_ROW),
            parseGradeTable("<table><tr><th>header</th></tr><tr><td>short</td></tr></table>"),
        )
    }

    @Test
    fun scoreFormattingMatchesVersion170() {
        assertEquals("A,100", formatGradeScore("100"))
        assertEquals("D,60", formatGradeScore("60"))
        assertEquals("F,59", formatGradeScore("59"))
        assertEquals("A-,87", formatGradeScore("A-"))
        assertEquals("-,-", formatGradeScore("通过"))
    }

    private fun gradeTable(vararg rows: String): String = buildString {
        append("<table><tr><th>序号</th></tr>")
        rows.forEach(::append)
        append("</table>")
    }

    private fun row(
        year: String,
        name: String,
        credits: String,
        score: String,
        teacher: String,
        detail: String = "",
    ): String = """
        <tr>
          <td>1</td><td>$year</td><td>$name</td><td>$credits</td>
          <td>$score</td><td>-</td><td>$teacher</td>
          <td><span data-content="&lt;div&gt;$detail&lt;/div&gt;"></span></td>
        </tr>
    """.trimIndent()
}
