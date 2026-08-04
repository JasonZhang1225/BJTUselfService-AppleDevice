package team.bjtuss.bjtuselfservice.shared.data.grade

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import team.bjtuss.bjtuselfservice.shared.domain.grade.CourseType

class TrainingProgramHtmlParserTest {
    @Test
    fun extractsAndDeduplicatesStuviewLinks() {
        val links = parseProgramLinks(
            """
            <html><body><table>
                <tr><td><a href="/training/training/program/stuview/6449/">主修培养方案</a></td></tr>
                <tr><td><a href="stuview/7001/">辅修培养方案</a></td></tr>
                <tr><td><a href="/training/training/program/stuview/6449/">重复链接</a></td></tr>
                <tr><td><a href="/training/training/program/">无 id 链接</a></td></tr>
            </table></body></html>
            """.trimIndent(),
        )

        assertEquals(
            listOf(
                "https://aa.bjtu.edu.cn/training/training/program/stuview/6449/",
                "https://aa.bjtu.edu.cn/training/training/program/stuview/7001/",
            ),
            links,
        )
    }

    @Test
    fun parsesIrregularRowsByLocatingCourseCodeCell() {
        val result = assertIs<TrainingProgramParseResult.Success>(
            parseProgramCourseTypes(programPage()),
        )

        assertEquals(
            mapOf(
                "C312009B" to "必修",
                "M710033B" to "限选",
                "S1100120A" to "任选",
            ),
            result.courseTypes,
        )
    }

    @Test
    fun rejectsPageWithoutCourseTable() {
        val result = parseProgramCourseTypes("<html>fixture-secret</html>")

        assertEquals(
            TrainingProgramParseResult.Failure(GradeParseFailure.TABLE_MISSING),
            result,
        )
        assertTrue("fixture-secret" !in result.toString())
    }

    @Test
    fun storedTextConversionRoundTripsKnownTypesOnly() {
        assertEquals(CourseType.REQUIRED, courseTypeForStoredText("必修"))
        assertEquals(CourseType.LIMITED, courseTypeForStoredText("限选"))
        assertEquals(CourseType.ELECTIVE, courseTypeForStoredText("任选"))
        assertEquals(CourseType.PHYSICAL_EDUCATION, courseTypeForStoredText("体育"))
        assertEquals(null, courseTypeForStoredText("未知"))
        assertEquals("必修", CourseType.REQUIRED.storedText())
        assertEquals("体育", CourseType.PHYSICAL_EDUCATION.storedText())
        assertEquals(null, CourseType.UNKNOWN.storedText())
    }

    @Test
    fun sportsGroupCoursesAreAllClassifiedAsPhysicalEducationViaRowspanCarry() {
        val result = assertIs<TrainingProgramParseResult.Success>(
            parseProgramCourseTypes(groupedProgramPage()),
        )

        assertEquals(
            mapOf(
                // 体育类课组（rowspan + colspan 标签）：体育Ⅰ（必修）、专项课（任选）
                // 在组首行与 carry 延续的后续行都一律归“体育”。
                "P110011B" to "体育",
                "P110022B" to "体育",
                // 紧随其后的第二个含“体育”课组：验证组替换后 carry 不被误消耗。
                "P110077B" to "体育",
                "P110088B" to "体育",
                // 美育/其他素养类保持课程性质列原值。
                "A110033B" to "任选",
                "A110055B" to "任选",
                "B110044B" to "任选",
                // 组外课程不受标签影响。
                "C312009B" to "必修",
            ),
            result.courseTypes,
        )
    }

    /**
     * 复现实测课组结构：组标题只出现在每组首行且带 rowspan（体育组还叠加 colspan），
     * 组内后续行 td 数更少且不重复组标签；体育Ⅰ、专项课在同一课组内且课程性质列不一致。
     */
    private fun groupedProgramPage(): String = """
        <html><body>
        <table>
            <tr><td>课程号</td><td>课程名</td><td>课程性质</td><td>学分</td></tr>
            <tr>
                <td rowspan="2" colspan="2">体育类课程【4.0】</td>
                <td>体育Ⅰ [01]</td><td>P110011B</td><td>必修</td><td>1.0</td>
            </tr>
            <tr>
                <td>篮球专项 [01]</td><td>P110022B</td><td>任选</td><td>1.0</td>
            </tr>
            <tr>
                <td rowspan="2">体育保健类课程【2.0】</td>
                <td>保健一 [01]</td><td>P110077B</td><td>任选</td><td>1.0</td>
            </tr>
            <tr>
                <td>保健二 [01]</td><td>P110088B</td><td>必修</td><td>1.0</td>
            </tr>
            <tr>
                <td rowspan="2">美育素养类课程【2.0】</td>
                <td>美术鉴赏 [01]</td><td>A110033B</td><td>任选</td><td>2.0</td>
            </tr>
            <tr>
                <td>书法鉴赏 [01]</td><td>A110055B</td><td>任选</td><td>2.0</td>
            </tr>
            <tr>
                <td rowspan="1">其他素养类课程【3.0】</td>
                <td>心理健康 [01]</td><td>B110044B</td><td>任选</td><td>1.0</td>
            </tr>
            <tr>
                <td>高级英语视听说 [04]</td><td>C312009B</td><td>必修</td><td>2.0</td>
            </tr>
        </table>
        </body></html>
    """.trimIndent()

    /**
     * 复现实测页面结构：课程表是含“课程性质”表头的那张，表头用 td 书写；
     * 平台/课组标题只出现在每组首行且带 colspan，单行 td 数有 8/9/10 三种。
     */
    private fun programPage(): String = """
        <html><body>
        <table>
            <tr><td>方案说明</td></tr>
        </table>
        <table>
            <tr>
                <td>课程类别</td><td>课程号</td><td>课程名</td><td>课程性质</td>
                <td>学分</td><td>学时</td><td>开课学期</td><td>备注</td>
            </tr>
            <tr>
                <td colspan="2">学科基础平台</td>
                <td>高级英语视听说 [04]</td><td>C312009B</td><td>必 修</td>
                <td>2.0</td><td>32</td><td>1</td><td></td><td></td><td></td>
            </tr>
            <tr>
                <td>序号</td><td>大学物理 [01]</td><td>M710033B</td><td>限选</td>
                <td>4.0</td><td>64</td><td>2</td><td></td><td></td>
            </tr>
            <tr>
                <td>计算机导论 [01]</td><td>S1100120A</td><td>任 选</td>
                <td>2.0</td><td>32</td><td>3</td><td></td><td></td>
            </tr>
            <tr>
                <td>体育俱乐部 [01]</td><td>P210045B</td><td>其他性质</td>
                <td>1.0</td><td>16</td><td>1</td><td></td>
            </tr>
        </table>
        </body></html>
    """.trimIndent()
}
