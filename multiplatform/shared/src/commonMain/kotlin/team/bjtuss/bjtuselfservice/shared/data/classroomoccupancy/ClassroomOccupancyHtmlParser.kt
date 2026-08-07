package team.bjtuss.bjtuselfservice.shared.data.classroomoccupancy

import com.fleeksoft.ksoup.Ksoup
import io.ktor.http.decodeURLPart
import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import team.bjtuss.bjtuselfservice.shared.data.homework.StrictJsonValue
import team.bjtuss.bjtuselfservice.shared.data.homework.parseStrictJsonArray
import team.bjtuss.bjtuselfservice.shared.data.homework.string
import team.bjtuss.bjtuselfservice.shared.domain.classroomoccupancy.ClassroomOccupancy
import team.bjtuss.bjtuselfservice.shared.domain.classroomoccupancy.OccupancyKind
import team.bjtuss.bjtuselfservice.shared.domain.classroomoccupancy.OccupancySemester
import team.bjtuss.bjtuselfservice.shared.domain.classroomoccupancy.OccupancyWeekDate

enum class ClassroomOccupancyParseFailure {
    TABLE_MISSING,
    MALFORMED_ROW,
}

sealed interface ClassroomOccupancyParseResult {
    data class Success(val rooms: List<ClassroomOccupancy>) : ClassroomOccupancyParseResult
    data class Failure(val reason: ClassroomOccupancyParseFailure) : ClassroomOccupancyParseResult
}

/** 行首教室单元格 + 星期1-7 × 节次1-7 共 49 格。 */
private const val OCCUPANCY_ROW_CELLS = 50

/**
 * 解析 room_view 教室占用表。失败只返回枚举，不保留响应正文。
 * 学校页无结果时表格无数据行（空 tbody/只有表头），按合法空列表处理。
 */
fun parseClassroomOccupancyTable(html: String): ClassroomOccupancyParseResult {
    val table = Ksoup.parse(html).selectFirst("table")
        ?: return ClassroomOccupancyParseResult.Failure(ClassroomOccupancyParseFailure.TABLE_MISSING)
    val rooms = mutableListOf<ClassroomOccupancy>()
    table.select("tr").forEach { row ->
        if (row.selectFirst("th") != null) return@forEach
        val columns = row.select("td")
        if (columns.isEmpty()) return@forEach
        if (columns.size < OCCUPANCY_ROW_CELLS) {
            return ClassroomOccupancyParseResult.Failure(ClassroomOccupancyParseFailure.MALFORMED_ROW)
        }
        val (room, capacity) = parseRoomHeader(columns[0].text())
            ?: return ClassroomOccupancyParseResult.Failure(ClassroomOccupancyParseFailure.MALFORMED_ROW)
        val cells = HashMap<Pair<Int, Int>, OccupancyKind>(49)
        for (index in 1 until OCCUPANCY_ROW_CELLS) {
            val cell = columns[index]
            // 优先按 title（星期X 第Y节）定位，title 缺失时退化为顺序（一周七天逐节铺开）。
            val position = parseCellPosition(cell.attr("title"))
                ?: ((index - 1) / 7 + 1 to (index - 1) % 7 + 1)
            cells[position] = occupancyKindFromStyle(cell.attr("style"))
        }
        rooms += ClassroomOccupancy(room = room, capacity = capacity, cells = cells)
    }
    return ClassroomOccupancyParseResult.Success(rooms)
}

/** 行首形如 `SY101 (90)`：教室号 + 括号容量；容量缺失时为 0。 */
private fun parseRoomHeader(text: String): Pair<String, Int>? {
    val cleaned = text.trim().replace(Regex("\\s+"), " ")
    val room = cleaned.substringBefore(' ').substringBefore('(').trim()
    if (room.isBlank()) return null
    val capacity = Regex("\\((\\d+)\\)").find(cleaned)?.groupValues?.get(1)?.toIntOrNull() ?: 0
    return room to capacity
}

private val CELL_TITLE = Regex("星期([一二三四五六日天1-7])\\s*第(\\d+)节")

private fun parseCellPosition(title: String): Pair<Int, Int>? {
    val match = CELL_TITLE.find(title.trim()) ?: return null
    val weekday = when (match.groupValues[1]) {
        "一", "1" -> 1
        "二", "2" -> 2
        "三", "3" -> 3
        "四", "4" -> 4
        "五", "5" -> 5
        "六", "6" -> 6
        "日", "天", "7" -> 7
        else -> return null
    }
    val period = match.groupValues[2].toIntOrNull() ?: return null
    return weekday to period
}

/**
 * 底色 → 占用类型。色值依据老安卓 `MisDataManager.getClassroom()` 的 switch 分支
 * （app/ 只读参考），编号语义已与线上核实：aa 教务系统 room_view 页面图例中
 * 色块与文字相邻一一对应（2026-08-07 核对）。其余底色归 UNKNOWN。
 */
internal fun occupancyKindFromStyle(style: String): OccupancyKind {
    val color = Regex("background-color:\\s*(#[0-9a-fA-F]{3,6})")
        .find(style)?.groupValues?.get(1)?.lowercase() ?: return OccupancyKind.FREE
    return when (color) {
        "#fff", "#ffffff" -> OccupancyKind.FREE
        "#e46868" -> OccupancyKind.SCHEDULED
        "#9e6868" -> OccupancyKind.RESCHEDULED
        "#394ed6" -> OccupancyKind.EXAM
        "#77bf6d" -> OccupancyKind.EXPERIMENT
        "#d8cc56" -> OccupancyKind.OTHER
        else -> OccupancyKind.UNKNOWN
    }
}

/**
 * room_view 页 zxjxjhh 学期下拉的解析结果。
 * [selected] 为服务器回填的当前学期（`<option selected>`）——注意下拉顺序是
 * 新到旧，第一项并不等于当前学期，必须按 selected 属性判断。
 */
data class SemesterOptions(
    val selected: OccupancySemester?,
    val all: List<OccupancySemester>,
)

/**
 * 解析 room_view 页 zxjxjhh 学期下拉。找不到 select（或 HTML 异常）返回 null，
 * 由调用方按 MALFORMED_RESPONSE 处理；select 存在但无 option 按空清单处理。
 */
fun parseSemesterOptions(html: String): SemesterOptions? {
    val select = try {
        Ksoup.parse(html).selectFirst("select[name=zxjxjhh]")
    } catch (_: Exception) {
        return null
    } ?: return null
    val options = select.select("option")
    val all = options.mapNotNull { option ->
        val value = option.attr("value").trim()
        val label = option.text().trim()
        if (value.isEmpty() || label.isEmpty()) null else OccupancySemester(value, label)
    }
    // 服务器通常不用 selected 属性回填当前学期，而是脚本 `$("[name=zxjxjhh]").val("…")`；
    // 两种都试，均失败返回 null（调用方按“无当前学期”处理，UI 退化为纯“当前学期”）。
    val selected = options.firstOrNull { it.hasAttr("selected") }
        ?.let { option ->
            val value = option.attr("value").trim()
            val label = option.text().trim()
            if (value.isEmpty() || label.isEmpty()) null else OccupancySemester(value, label)
        }
        ?: currentSemesterFromScript(html, all)
    return SemesterOptions(selected = selected, all = all)
}

/** 从页面脚本 `$("[name=zxjxjhh]").val("2025-2026-2-2")` 提取服务器回填的当前学期。 */
private fun currentSemesterFromScript(
    html: String,
    all: List<OccupancySemester>,
): OccupancySemester? {
    val scriptValue = SCRIPT_CURRENT_SEMESTER.find(html)?.groupValues?.get(1) ?: return null
    return all.firstOrNull { it.id == scriptValue }
}

private val SCRIPT_CURRENT_SEMESTER =
    Regex("\\[name\\s*=\\s*zxjxjhh\\][^)]*\\)\\.val\\(\\s*[\"']([^\"']+)[\"']")

/** 学期标题，如 `第一学期（2026-2027学年）`；全半角括号均可。来自 hidTitle_<Id> 隐藏字段。 */
private val SEMESTER_TITLE = Regex("第(一|二)学期\\s*[（(]\\s*(\\d{4})-(\\d{4})\\s*学年\\s*[)）]")

/** 夏季学期（第二学期表后半段）在 aa 系统 zc 编号里的续编偏移：春季 18 周后从 19 起。 */
private const val SPRING_WEEKS = 18

/**
 * 解析教务处校历页（bksy SemesterTranPage，noRemark=1）的 hidJson 隐藏字段。
 * 29 张周历表格是页面 JS 从该字段动态渲染的，原始 HTML 里的静态表格与学期周历无关。
 *
 * hidJson 是 URL 编码的 JSON 数组，每项 `{"Id":49,"Json":[{"DT":"\/Date(毫秒+0800)\/",
 * "Week":"第1教学周",...},...]}`，学期标题来自同页 `hidTitle_<Id>` 隐藏字段
 * （如 `第一学期（2026-2027学年）`）。返回 学期 label（如 `2025-2026-2`，
 * 与 aa 下拉 option text 一致）→ 周日期列表。
 *
 * 尽力而为：单学期异常只跳过该项，整体失败返回空 Map——日期仅供 UI 参考，
 * 拿不到不阻断查询，也绝不抛异常。
 */
fun parseAcademicWeeks(html: String): Map<String, List<OccupancyWeekDate>> {
    val inputs = try {
        extractHiddenInputValues(html)
    } catch (_: Exception) {
        return emptyMap()
    }
    val payload = inputs.firstOrNull { it.first.equals("hidJson", ignoreCase = true) }?.second
        ?: return emptyMap()
    val decoded = try {
        // value 可能带 HTML 转义（&quot; 等）和 URL 编码（ASP.NET UrlEncode，空格为 +）。
        payload.replace("&quot;", "\"").replace("&#39;", "'").decodeURLPart()
    } catch (_: Exception) {
        return emptyMap()
    }
    val semesters = parseStrictJsonArray(decoded) ?: return emptyMap()
    val titles = inputs.mapNotNull { (name, value) ->
        HID_TITLE_INPUT.find(name)?.groupValues?.get(1)?.toIntOrNull()?.let { it to value }
    }.toMap()

    val result = LinkedHashMap<String, List<OccupancyWeekDate>>()
    semesters.forEach { item ->
        val fields = (item as? StrictJsonValue.ObjectValue)?.fields ?: return@forEach
        val title = fields.string("Id")?.toIntOrNull()?.let { titles[it] } ?: return@forEach
        val titleMatch = SEMESTER_TITLE.find(title) ?: return@forEach
        val isSpring = titleMatch.groupValues[1] == "二"
        val baseYear = titleMatch.groupValues[2].toIntOrNull() ?: return@forEach
        val rows = (fields["Json"] as? StrictJsonValue.ArrayValue)?.items ?: return@forEach
        val weeks = buildWeeks(rows, isSpring)
        if (weeks.isNotEmpty()) result[labelFor(isSpring, baseYear)] = weeks
    }
    return result
}

private val HIDDEN_INPUT = Regex("<input\\b[^>]*>", RegexOption.IGNORE_CASE)
private val HID_TITLE_INPUT = Regex("hidTitle_(\\d+)", RegexOption.IGNORE_CASE)

/** 页面上所有 `<input>` 的 (name, value)；属性顺序任意，value 保持原样（未做实体/URL 解码）。 */
private fun extractHiddenInputValues(html: String): List<Pair<String, String>> {
    val namePattern = Regex("\\bname\\s*=\\s*[\"']([^\"']*)[\"']", RegexOption.IGNORE_CASE)
    val idPattern = Regex("\\bid\\s*=\\s*[\"']([^\"']*)[\"']", RegexOption.IGNORE_CASE)
    val valuePattern = Regex("\\bvalue\\s*=\\s*\"([^\"]*)\"|\\bvalue\\s*=\\s*'([^']*)'", RegexOption.IGNORE_CASE)
    return HIDDEN_INPUT.findAll(html).mapNotNull { input ->
        // 线上页面 hidJson 有 name，而 hidTitle_<Id> 只有 id 没有 name：按 name 取不到时回退 id。
        val key = namePattern.find(input.value)?.groupValues?.get(1)?.trim()
            ?: idPattern.find(input.value)?.groupValues?.get(1)?.trim()
            ?: return@mapNotNull null
        val value = valuePattern.find(input.value)
            ?.let { it.groupValues[1].ifEmpty { it.groupValues[2] } } ?: return@mapNotNull null
        key to value
    }.toList()
}

private val WEEK_NUMBER = Regex("第(\\d+)教学周")
private val SUMMER_WEEK_NUMBER = Regex("第(\\d+)周")
private val DT_MILLIS = Regex("/Date\\((\\d+)[^)]*\\)/")

/** 微软 JSON 日期 `/Date(毫秒+0800)/` 的毫秒是 UTC 时间戳，对应周一 00:00 北京时间；加 8 小时取 UTC 日期即周一。 */
private const val BEIJING_OFFSET_MILLIS = 8 * 60 * 60 * 1000L

/**
 * 一行周历 → aa zc 编号 + 起止日期（月/日，不补零）。zc 规则（已实测）：
 * 第一/二学期春季段 `第N教学周` → N；第二学期夏季段 `第N周`（无“教学”二字）
 * 从 1 重新编号 → 18 + N；“休”、空文本等非周次行跳过。单行异常跳过该行。
 */
private fun buildWeeks(rows: List<StrictJsonValue>, isSpring: Boolean): List<OccupancyWeekDate> {
    val weeks = mutableListOf<OccupancyWeekDate>()
    rows.forEach { row ->
        val fields = (row as? StrictJsonValue.ObjectValue)?.fields ?: return@forEach
        val weekText = fields.string("Week")?.trim() ?: return@forEach
        val zc = when (val match = WEEK_NUMBER.find(weekText)) {
            null -> if (isSpring) {
                SUMMER_WEEK_NUMBER.find(weekText)?.groupValues?.get(1)?.toIntOrNull()?.plus(SPRING_WEEKS)
            } else {
                null
            }
            else -> match.groupValues[1].toIntOrNull()
        } ?: return@forEach
        val dt = fields.string("DT") ?: return@forEach
        val millis = DT_MILLIS.find(dt)?.groupValues?.get(1)?.toLongOrNull() ?: return@forEach
        val monday = Instant.fromEpochMilliseconds(millis + BEIJING_OFFSET_MILLIS)
            .toLocalDateTime(TimeZone.UTC).date
        val end = monday.plus(6, DateTimeUnit.DAY)
        weeks += OccupancyWeekDate(
            week = zc,
            startMonthDay = "${monday.month.ordinal + 1}/${monday.day}",
            endMonthDay = "${end.month.ordinal + 1}/${end.day}",
        )
    }
    return weeks
}

/** 学期 label（与 aa 下拉 option text 一致），如 `2025-2026-2`。 */
private fun labelFor(isSpring: Boolean, baseYear: Int): String =
    "$baseYear-${baseYear + 1}-${if (isSpring) 2 else 1}"
