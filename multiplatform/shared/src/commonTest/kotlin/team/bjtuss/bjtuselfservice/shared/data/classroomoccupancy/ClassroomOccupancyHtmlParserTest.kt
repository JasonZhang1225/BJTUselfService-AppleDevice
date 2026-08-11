package team.bjtuss.bjtuselfservice.shared.data.classroomoccupancy

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import team.bjtuss.bjtuselfservice.shared.domain.classroomoccupancy.OccupancyKind
import team.bjtuss.bjtuselfservice.shared.domain.classroomoccupancy.OccupancySemester
import team.bjtuss.bjtuselfservice.shared.domain.classroomoccupancy.OccupancyWeekDate

class ClassroomOccupancyHtmlParserTest {
    @Test
    fun parsesMultipleRoomsWithCapacityAndCells() {
        val result = assertIs<ClassroomOccupancyParseResult.Success>(
            parseClassroomOccupancyTable(
                occupancyTable(
                    occupancyRow("SY101 (90)", "background-color: #fff"),
                    occupancyRow("DQ105 (40)", "background-color: #e46868"),
                ),
            ),
        )

        assertEquals(2, result.rooms.size)
        val first = result.rooms[0]
        assertEquals("SY101", first.room)
        assertEquals(90, first.capacity)
        assertEquals(49, first.cells.size)
        assertEquals(OccupancyKind.FREE, first.kindAt(1, 1))
        assertEquals(OccupancyKind.FREE, first.kindAt(7, 7))
        assertEquals(OccupancyKind.SCHEDULED, result.rooms[1].kindAt(3, 5))
    }

    @Test
    fun mapsAllFiveOccupancyColorsAndFreeAndUnknown() {
        assertEquals(OccupancyKind.FREE, occupancyKindFromStyle("background-color: #fff"))
        assertEquals(OccupancyKind.FREE, occupancyKindFromStyle("background-color: #FFFFFF"))
        assertEquals(OccupancyKind.SCHEDULED, occupancyKindFromStyle("background-color: #e46868"))
        assertEquals(OccupancyKind.RESCHEDULED, occupancyKindFromStyle("background-color: #9e6868"))
        assertEquals(OccupancyKind.EXAM, occupancyKindFromStyle("background-color: #394ed6"))
        assertEquals(OccupancyKind.EXPERIMENT, occupancyKindFromStyle("background-color: #77bf6d"))
        assertEquals(OccupancyKind.OTHER, occupancyKindFromStyle("background-color: #d8cc56"))
        assertEquals(OccupancyKind.UNKNOWN, occupancyKindFromStyle("background-color: #123456"))
        assertEquals(OccupancyKind.FREE, occupancyKindFromStyle(""))
    }

    @Test
    fun cellPositionComesFromTitleNotOrder() {
        // 49 格中第 2 格的 title 故意写成星期日第7节：定位以 title 为准，不是顺序。
        val cells = (1..49).joinToString("") { index ->
            if (index == 2) td(7, 7, "background-color: #394ed6") else td(1, 1, "background-color: #fff")
        }
        val result = assertIs<ClassroomOccupancyParseResult.Success>(
            parseClassroomOccupancyTable(occupancyTable("<tr><td>SY101 (90)</td>$cells</tr>")),
        )
        val room = result.rooms.single()
        assertEquals(OccupancyKind.EXAM, room.kindAt(7, 7))
        assertEquals(OccupancyKind.FREE, room.kindAt(1, 2))
    }

    @Test
    fun emptyTableIsValidEmptyResult() {
        // 学校页无结果时为无数据行的空表，按合法空列表处理。
        val result = assertIs<ClassroomOccupancyParseResult.Success>(
            parseClassroomOccupancyTable("<table><tbody></tbody></table>"),
        )
        assertTrue(result.rooms.isEmpty())
    }

    @Test
    fun missingTableOrMalformedRowFails() {
        assertEquals(
            ClassroomOccupancyParseFailure.TABLE_MISSING,
            assertIs<ClassroomOccupancyParseResult.Failure>(
                parseClassroomOccupancyTable("<main>none</main>"),
            ).reason,
        )
        val failure = assertIs<ClassroomOccupancyParseResult.Failure>(
            parseClassroomOccupancyTable("<table><tr><td>SY101 秘密</td><td>格</td></tr></table>"),
        )
        assertEquals(ClassroomOccupancyParseFailure.MALFORMED_ROW, failure.reason)
        assertTrue("秘密" !in failure.toString())
    }

    @Test
    fun semesterOptionsParsesValuesLabelsAndServerSelected() {
        // 下拉顺序新到旧，当前学期（2025-2026-2）由服务器回填 selected 属性标识。
        val options = assertNotNull(parseSemesterOptions(semesterSelectHtml()))

        assertEquals(
            listOf("2026-2027-2", "2026-2027-1", "2025-2026-2", "2025-2026-1"),
            options.all.map { it.label },
        )
        assertEquals(
            listOf("2026-2027-2-2", "2026-2027-1-2", "2025-2026-2-2", "2025-2026-1-2"),
            options.all.map { it.id },
        )
        assertEquals(OccupancySemester("2025-2026-2-2", "2025-2026-2"), options.selected)
    }

    @Test
    fun semesterOptionsToleratesBooleanSelectedAttribute() {
        val options = assertNotNull(
            parseSemesterOptions("<select name=\"zxjxjhh\"><option value=\"2025-2026-2-2\" selected>2025-2026-2</option></select>"),
        )
        assertEquals(OccupancySemester("2025-2026-2-2", "2025-2026-2"), options.selected)
    }

    @Test
    fun semesterOptionsFallsBackToScriptValWithoutSelectedAttribute() {
        // 线上 room_view 页不回填 selected 属性，而是脚本 `$("[name=zxjxjhh]").val("…")` 回填当前学期。
        val html = """
            <select name="zxjxjhh" id="zxjxjhh">
                <option value="2026-2027-2-2">2026-2027-2</option>
                <option value="2026-2027-1-2">2026-2027-1</option>
                <option value="2025-2026-2-2">2025-2026-2</option>
                <option value="2025-2026-1-2">2025-2026-1</option>
            </select>
            <script>$$("[name=zxjxjhh]").val("2025-2026-2-2");</script>
        """.trimIndent()
        val options = assertNotNull(parseSemesterOptions(html))
        assertEquals(OccupancySemester("2025-2026-2-2", "2025-2026-2"), options.selected)
    }

    @Test
    fun semesterOptionsScriptValNotInOptionsReturnsNullSelected() {
        // 脚本回填值不在下拉里（异常页面）：selected 为 null，但 all 仍完整。
        val html = """
            <select name="zxjxjhh"><option value="2026-2027-2-2">2026-2027-2</option></select>
            <script>$$("[name=zxjxjhh]").val("2030-2031-1-2");</script>
        """.trimIndent()
        val options = assertNotNull(parseSemesterOptions(html))
        assertNull(options.selected)
        assertEquals(listOf("2026-2027-2"), options.all.map { it.label })
    }

    @Test
    fun semesterOptionsMissingSelectReturnsNull() {
        assertNull(parseSemesterOptions("<main>没有下拉</main>"))
    }

    @Test
    fun fallSemesterWeeksFromHidJsonSkipHolidayAndCrossYear() {
        // 2026-2027-1（Id=49，value 为 ASP.NET 风格 URL 编码，覆盖解码路径）：第 1 行
        // Week="" 占位跳过，国庆“休”行跳过，第 18 教学周跨年到 2027-01-11；
        // 页面上的旧静态表格（“开学：2023年2月20日…”）被忽略。
        val weeks = parseAcademicWeeks(
            hidJsonPage(
                semesters = listOf(semesterJson(49, fall2026Rows())),
                titles = mapOf(49 to "第一学期（2026-2027学年）"),
                encodeValue = true,
                extraHtml = "<table><tr><td>开学：2023年2月20日</td></tr></table>",
            ),
        )

        assertEquals(setOf("2026-2027-1"), weeks.keys)
        val fall = assertNotNull(weeks["2026-2027-1"])
        assertEquals(18, fall.size)
        assertEquals(weekDate(1, "9/7", "9/13", 2026, 9, 7), fall.first())
        assertEquals(weekDate(2, "9/14", "9/20", 2026, 9, 14), fall.first { it.week == 2 })
        assertEquals(weekDate(3, "9/21", "9/27", 2026, 9, 21), fall.first { it.week == 3 })
        // “休”行（9/28）被跳过，第 4 周是 10/5 那周，而不是 9/28。
        assertEquals(weekDate(4, "10/5", "10/11", 2026, 10, 5), fall.first { it.week == 4 })
        // 12/28 周跨月，1/11 周跨年（2027）。
        assertEquals(weekDate(16, "12/28", "1/3", 2026, 12, 28), fall.first { it.week == 16 })
        assertEquals(weekDate(17, "1/4", "1/10", 2027, 1, 4), fall.first { it.week == 17 })
        assertEquals(weekDate(18, "1/11", "1/17", 2027, 1, 11), fall.last())
    }

    @Test
    fun springSemesterNumbersSummerWeeksFromNineteen() {
        // 2025-2026-2（Id=48，value 为解码后 JSON + &quot; 实体转义，覆盖直读路径）：
        // 春季 1-18 教学周，夏季段“第N周”从 1 重新编号 → aa zc 19-27
        // （2026-08-03 那周为第 23 教学周，已线上核对）。
        val weeks = parseAcademicWeeks(
            hidJsonPage(
                semesters = listOf(semesterJson(48, springSummer2026Rows())),
                titles = mapOf(48 to "第二学期(2025-2026学年)"),
                encodeValue = false,
            ),
        )

        val spring = assertNotNull(weeks["2025-2026-2"])
        assertEquals(27, spring.size)
        assertEquals(weekDate(1, "3/2", "3/8", 2026, 3, 2), spring.first())
        assertEquals(weekDate(18, "6/29", "7/5", 2026, 6, 29), spring.first { it.week == 18 })
        // 夏季段第 1 周 = aa zc 19，周次文本回落到 1。
        assertEquals(weekDate(19, "7/6", "7/12", 2026, 7, 6), spring.first { it.week == 19 })
        assertEquals(weekDate(23, "8/3", "8/9", 2026, 8, 3), spring.first { it.week == 23 })
        assertEquals(weekDate(27, "8/31", "9/6", 2026, 8, 31), spring.last())
    }

    @Test
    fun halfWidthBracketTitleSemesterIsLabeled() {
        // 半角括号标题：第二学期(2024-2025学年) → label 2024-2025-2。
        val weeks = parseAcademicWeeks(
            hidJsonPage(
                semesters = listOf(semesterJson(46, spring2025MiniRows())),
                titles = mapOf(46 to "第二学期(2024-2025学年)"),
            ),
        )

        assertEquals(setOf("2024-2025-2"), weeks.keys)
        assertEquals(
            listOf(
                weekDate(1, "3/3", "3/9", 2025, 3, 3),
                weekDate(19, "7/7", "7/13", 2025, 7, 7),
            ),
            assertNotNull(weeks["2024-2025-2"]),
        )
    }

    @Test
    fun academicWeeksWithoutHidJsonReturnsEmptyMap() {
        assertTrue(parseAcademicWeeks("<main>不是校历</main>").isEmpty())
        // hidJson 存在但内容不可解码/非法 JSON。
        assertTrue(parseAcademicWeeks("<input type=\"hidden\" name=\"hidJson\" value=\"%zz\" />").isEmpty())
        assertTrue(parseAcademicWeeks("<input type=\"hidden\" name=\"hidJson\" value=\"%5b%7b%22Id%22%3a49%7d%5d\" />").isEmpty())
        // 学期项缺少对应标题（真实页面缺 Id=30）→ 该项跳过。
        assertTrue(
            parseAcademicWeeks(
                hidJsonPage(
                    semesters = listOf(semesterJson(30, fall2026Rows())),
                    titles = emptyMap(),
                ),
            ).isEmpty(),
        )
    }
}

private fun weekDate(
    week: Int,
    startMonthDay: String,
    endMonthDay: String,
    year: Int,
    month: Int,
    day: Int,
) = OccupancyWeekDate(week, startMonthDay, endMonthDay, LocalDate(year, month, day))

private fun occupancyTable(vararg rows: String): String =
    "<table><tr><th>教室</th></tr>${rows.joinToString("")}</table>"

private fun occupancyRow(header: String, style: String): String {
    val cells = StringBuilder()
    for (weekday in 1..7) {
        for (period in 1..7) {
            cells.append(td(weekday, period, if ((weekday + period) % 2 == 0) style else "background-color: #fff"))
        }
    }
    return "<tr><td>$header</td>$cells</tr>"
}

private fun td(weekday: Int, period: Int, style: String): String =
    "<td title=\"星期${"一二三四五六日"[weekday - 1]} 第${period}节\" style=\"$style\"></td>"

// ---- 学期下拉 / 校历页（bksy SemesterTranPage）样本 ----

private fun semesterSelectHtml(): String = """
    <select name="zxjxjhh" id="zxjxjhh">
        <option value="2026-2027-2-2">2026-2027-2</option>
        <option value="2026-2027-1-2">2026-2027-1</option>
        <option value="2025-2026-2-2" selected="selected">2025-2026-2</option>
        <option value="2025-2026-1-2">2025-2026-1</option>
    </select>
""".trimIndent()

/**
 * 校历页样本：hidJson 隐藏字段（URL 编码或解码后 JSON）+ hidTitle_<Id> 学期标题。
 * 行数据对齐线上实抓（2026-08-07）：每行 `DT` 为 `/Date(毫秒+0800)/`（毫秒是周一
 * 00:00 北京时间的 UTC 时间戳），`Week` 为周次文本；页面旧的静态表格与解析无关。
 */
private fun hidJsonPage(
    semesters: List<String>,
    titles: Map<Int, String>,
    encodeValue: Boolean = true,
    extraHtml: String = "",
): String = buildString {
    append("<html><head><title>校历</title></head><body>")
    append(extraHtml)
    titles.forEach { (id, title) ->
        // 与线上一致：hidTitle 只有 id 没有 name（提取按 name 取不到时回退 id）。
        append("<input type=\"hidden\" id=\"hidTitle_$id\" value=\"$title\" />")
    }
    val json = "[${semesters.joinToString(",")}]"
    append("<input type=\"hidden\" name=\"hidJson\" id=\"hidJson\" value=\"")
    append(if (encodeValue) aspNetUrlEncode(json) else json.replace("\"", "&quot;"))
    append("\" />")
    append("</body></html>")
}

/** 一个学期项的解码后 JSON 文本，行 (毫秒, Week 文本) 保持线上字段结构。 */
private fun semesterJson(id: Int, rows: List<Pair<Long, String>>): String =
    """{"Id":$id,"Json":[${rows.joinToString(",") { (millis, week) -> weekRowJson(millis, week) }}]}"""

private fun weekRowJson(dtMillis: Long, week: String): String {
    val monday = Instant.fromEpochMilliseconds(dtMillis + 8 * 3_600_000L).toLocalDateTime(TimeZone.UTC).date
    val sunday = monday.plus(6, DateTimeUnit.DAY)
    return """{"DT":"\/Date($dtMillis+0800)\/","Month":"${monday.month.ordinal + 1}月","Week":"$week","Mon":${monday.day},"Sun":${sunday.day}}"""
}

/** ASP.NET UrlEncode 风格：空格 → `+`，其余非保留字符按 UTF-8 百分号编码（与线上 value 一致）。 */
private fun aspNetUrlEncode(text: String): String = buildString {
    text.encodeToByteArray().forEach { byte ->
        val code = byte.toInt() and 0xFF
        val character = code.toChar()
        when {
            character in 'a'..'z' || character in 'A'..'Z' || character in '0'..'9' ||
                character == '-' || character == '_' || character == '.' || character == '~' -> append(character)
            character == ' ' -> append('+')
            else -> append('%').append(HEX_DIGITS[code shr 4]).append(HEX_DIGITS[code and 0xF])
        }
    }
}

private const val HEX_DIGITS = "0123456789ABCDEF"
private const val WEEK_MILLIS = 7L * 86_400_000L

/** 实抓：2026-09-07 周一 00:00 北京时间 = /Date(1788710400000+0800)/。 */
private const val MONDAY_2026_09_07 = 1788710400000L

/**
 * 2026-2027-1（Id=49）实抓 20 行：第 1 行 Week=""（8/31，学期前占位），
 * 第 1-3 教学周（9/7、9/14、9/21），国庆“休”（9/28），第 4-18 教学周
 * （10/5 … 2027-01-11 跨年）。毫秒由实抓锚点按 7 天递增。
 */
private fun fall2026Rows(): List<Pair<Long, String>> = buildList {
    add(MONDAY_2026_09_07 - WEEK_MILLIS to "")
    for (week in 1..3) add(MONDAY_2026_09_07 + (week - 1) * WEEK_MILLIS to "第${week}教学周")
    add(MONDAY_2026_09_07 + 3 * WEEK_MILLIS to "休")
    for (week in 4..18) add(MONDAY_2026_09_07 + week * WEEK_MILLIS to "第${week}教学周")
}

/**
 * 2025-2026-2（Id=48）实抓 28 行：第 1 行 Week=""（2/23），春季第 1-18 教学周
 * （3/2 … 6/29），夏季段“第N周”（无“教学”二字）第 1-9 周（7/6 … 8/31）。
 */
private fun springSummer2026Rows(): List<Pair<Long, String>> = buildList {
    val springStart = MONDAY_2026_09_07 - 27 * WEEK_MILLIS // 2026-03-02
    add(springStart - WEEK_MILLIS to "")
    for (week in 1..18) add(springStart + (week - 1) * WEEK_MILLIS to "第${week}教学周")
    for (week in 1..9) add(springStart + (17 + week) * WEEK_MILLIS to "第${week}周")
}

/** 2024-2025-2（Id=46，半角括号标题）最小样本：春季第 1 周 + 夏季第 1 周。 */
private fun spring2025MiniRows(): List<Pair<Long, String>> = listOf(
    mondayMillis(2025, 3, 3) to "第1教学周",
    mondayMillis(2025, 7, 7) to "第1周",
)

/** 周一 00:00 北京时间的 epoch 毫秒（与线上 /Date(毫秒+0800)/ 一致）。 */
private fun mondayMillis(year: Int, month: Int, day: Int): Long =
    LocalDate(year, month, day).toEpochDays() * 86_400_000L - 8 * 3_600_000L
