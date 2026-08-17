package team.bjtuss.bjtuselfservice.shared.data.course

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CourseScheduleHtmlParserTest {
    @Test
    fun parsesCurrentAndSelectionSchedulesWithTeacherFallback() {
        val teachers = assertIs<TeacherTableParseResult.Success>(
            parseTeacherTable(teacherTable("程序设计 02", "李老师")),
        ).teachersByCourse
        val current = assertIs<CourseScheduleTableParseResult.Success>(
            parseCourseScheduleTable(
                scheduleTable(
                    slot = 0,
                    day = 0,
                    children = currentCourseChild(),
                ),
                isSelectionSchedule = false,
                teachersByCourse = teachers,
            ),
        ).courses.single()
        val selection = assertIs<CourseScheduleTableParseResult.Success>(
            parseCourseScheduleTable(
                scheduleTable(
                    slot = 1,
                    day = 1,
                    children = selectionCourseChild(),
                ),
                isSelectionSchedule = true,
                teachersByCourse = teachers,
            ),
        ).courses.single()

        assertEquals(1, current.courseLocationIndex)
        assertEquals("张老师", current.courseTeacher)
        assertEquals("第1-3,5周", current.courseTime)
        assertEquals("思源101", current.coursePlace)
        assertTrue(!current.isCurrentSemester)

        assertEquals(10, selection.courseLocationIndex)
        assertEquals("李老师", selection.courseTeacher)
        assertEquals("第2-4周", selection.courseTime)
        assertTrue(selection.isCurrentSemester)
    }

    @Test
    fun keepsMultipleCoursesInOneCellAndSkipsMalformedChildren() {
        val html = scheduleTable(
            slot = 0,
            day = 0,
            children = currentCourseChild() + "<div>无法解析的占位</div>" + currentCourseChild(
                id = "C101 (03)",
                name = "线性代数",
            ),
        )

        val courses = assertIs<CourseScheduleTableParseResult.Success>(
            parseCourseScheduleTable(html, false, emptyMap()),
        ).courses

        assertEquals(2, courses.size)
        assertEquals(listOf(1, 1), courses.map { it.courseLocationIndex })
    }

    @Test
    fun rejectsMissingOrShortGridWithoutRetainingHtml() {
        assertEquals(
            CourseScheduleParseFailure.TABLE_MISSING,
            assertIs<CourseScheduleTableParseResult.Failure>(
                parseCourseScheduleTable("<main>none</main>", false, emptyMap()),
            ).reason,
        )
        val short = "<table><tr><th>header</th></tr><tr><td>one</td></tr></table>"
        val failure = assertIs<CourseScheduleTableParseResult.Failure>(
            parseCourseScheduleTable(short, false, emptyMap()),
        )
        assertEquals(CourseScheduleParseFailure.MALFORMED_GRID, failure.reason)
        assertTrue("one" !in failure.toString())
    }

    @Test
    fun parsesOnlySafeCurrentWeekRange() {
        assertEquals(14, parseCurrentWeekFromUrl("https://aa.bjtu.edu.cn/path/?zc=14&x=1"))
        assertEquals(0, parseCurrentWeekFromUrl("https://aa.bjtu.edu.cn/path/?zc=27"))
        assertEquals(0, parseCurrentWeekFromUrl("https://aa.bjtu.edu.cn/path/"))
    }

    @Test
    fun parsesTimeListWeekCodeAsStringOrNumber() {
        assertEquals(25, parseCurrentWeekFromTimeList("""{"weekCode":"25"}"""))
        assertEquals(14, parseCurrentWeekFromTimeList("""{"weekCode":14}"""))
        assertEquals(8, parseCurrentWeekFromTimeList("""{"week_code":"8"}"""))
        assertEquals(0, parseCurrentWeekFromTimeList("""{"weekCode":"27"}"""))
        assertEquals(0, parseCurrentWeekFromTimeList("""{"weekCode":"0"}"""))
        assertEquals(0, parseCurrentWeekFromTimeList("<html>login</html>"))
        assertEquals(0, parseCurrentWeekFromTimeList("""{"STATUS":"0"}"""))
    }
}

internal fun teacherTable(name: String = "程序设计 02", teacher: String = "李老师") = """
    <table>
      <tr><th>标题</th></tr><tr><th>课程</th><th>教师</th></tr>
      <tr><td>$name</td><td>$teacher</td></tr>
    </table>
""".trimIndent()

internal fun scheduleTable(slot: Int, day: Int, children: String): String {
    val rows = (0 until 7).joinToString("") { row ->
        val cells = (0 until 7).joinToString("") { column ->
            "<td>${if (row == slot && column == day) children else ""}</td>"
        }
        "<tr><td>第${row + 1}节</td>$cells</tr>"
    }
    return "<table><tr><th>时间</th>${(1..7).joinToString("") { "<th>$it</th>" }}</tr>$rows</table>"
}

internal fun currentCourseChild(
    id: String = "C100 (01)",
    name: String = "离散数学",
) = """
    <div>$id<br><span>$name</span>
      <div style="max-width: 180px">第1-3,5周 <i>张老师</i></div>
      <span class="text-muted">思源 101</span>
    </div>
""".trimIndent()

internal fun selectionCourseChild() = """
    <div><span>C200 (02)<br>程序设计</span>
      <div style="max-width: 180px">第2-4周</div>
      <span class="text-muted">逸夫 201</span>
    </div>
""".trimIndent()
