package team.bjtuss.bjtuselfservice.shared.data.course

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
import team.bjtuss.bjtuselfservice.shared.domain.course.Course

enum class CourseScheduleParseFailure {
    TABLE_MISSING,
    MALFORMED_GRID,
}

sealed interface TeacherTableParseResult {
    data class Success(val teachersByCourse: Map<String, String>) : TeacherTableParseResult
    data class Failure(val reason: CourseScheduleParseFailure) : TeacherTableParseResult
}

sealed interface CourseScheduleTableParseResult {
    data class Success(val courses: List<Course>) : CourseScheduleTableParseResult
    data class Failure(val reason: CourseScheduleParseFailure) : CourseScheduleTableParseResult
}

fun parseTeacherTable(html: String): TeacherTableParseResult {
    val table = Ksoup.parse(html).selectFirst("table")
        ?: return TeacherTableParseResult.Failure(CourseScheduleParseFailure.TABLE_MISSING)
    val rows = table.select("tr")
    if (rows.size < 2) {
        return TeacherTableParseResult.Failure(CourseScheduleParseFailure.MALFORMED_GRID)
    }
    val teachers = linkedMapOf<String, String>()
    rows.drop(2).forEach { row ->
        val columns = row.select("td")
        if (columns.size >= 2) {
            val rawName = columns[0].text().trim()
            val teacher = columns[1].text().trim()
            if (rawName.isNotBlank() && teacher.isNotBlank()) {
                val parts = rawName.split(Regex("\\s+")).filter(String::isNotBlank)
                val key = if (parts.size == 3) parts.take(2).joinToString(" ") else rawName
                teachers[key] = teacher
            }
        }
    }
    return TeacherTableParseResult.Success(teachers)
}

/**
 * 解析 v1.7.0 的 7 节次 × 7 天表格。历史字段 isCurrentSemester=true 实际表示“选课课表”。
 */
fun parseCourseScheduleTable(
    html: String,
    isSelectionSchedule: Boolean,
    teachersByCourse: Map<String, String>,
): CourseScheduleTableParseResult {
    val table = Ksoup.parse(html).selectFirst("table")
        ?: return CourseScheduleTableParseResult.Failure(CourseScheduleParseFailure.TABLE_MISSING)
    val rows = table.select("tr").drop(1)
    if (rows.size < 7) {
        return CourseScheduleTableParseResult.Failure(CourseScheduleParseFailure.MALFORMED_GRID)
    }

    val courses = mutableListOf<Course>()
    rows.take(7).forEachIndexed { slotIndex, row ->
        val columns = row.select("td")
        if (columns.size < 8) {
            return CourseScheduleTableParseResult.Failure(CourseScheduleParseFailure.MALFORMED_GRID)
        }
        repeat(7) { dayIndex ->
            val locationIndex = slotIndex * 8 + dayIndex + 1
            columns[dayIndex + 1].children().forEach { child ->
                parseCourseChild(
                    child = child,
                    locationIndex = locationIndex,
                    isSelectionSchedule = isSelectionSchedule,
                    teachersByCourse = teachersByCourse,
                )?.let(courses::add)
            }
        }
    }
    return CourseScheduleTableParseResult.Success(courses)
}

fun parseCurrentWeekFromUrl(url: String): Int =
    Regex("(?:[?&])zc=(\\d+)")
        .find(url)
        ?.groupValues
        ?.getOrNull(1)
        ?.toIntOrNull()
        ?.takeIf { it in 1..26 }
        ?: 0

private fun parseCourseChild(
    child: Element,
    locationIndex: Int,
    isSelectionSchedule: Boolean,
    teachersByCourse: Map<String, String>,
): Course? {
    val rawIdAndName = if (isSelectionSchedule) {
        child.selectFirst("span")?.html().orEmpty()
    } else {
        child.html()
    }
    val idAndName = rawIdAndName.split(Regex("(?i)<br\\s*/?>"), limit = 2)
    if (idAndName.size < 2) return null
    val courseId = Ksoup.parse(idAndName[0]).text().trim()
    val courseName = if (isSelectionSchedule) {
        Ksoup.parse(idAndName[1]).text().trim()
    } else {
        Ksoup.parse(idAndName[1]).selectFirst("span")?.text()?.trim().orEmpty()
    }
    if (courseId.isBlank() || courseName.isBlank()) return null

    val scheduleInfo = child.selectFirst("div[style^=max-width]")
    val scheduleText = scheduleInfo?.text().orEmpty()
    val courseTime = if ('周' in scheduleText) {
        (scheduleText.substringBefore('周') + "周").filterNot(Char::isWhitespace)
    } else {
        "未知"
    }
    val embeddedTeacher = scheduleInfo?.selectFirst("i")?.text()?.trim().orEmpty()
    val teacher = embeddedTeacher.ifBlank {
        teachersByCourse[teacherLookupKey(courseName, courseId)].orEmpty().ifBlank { "?" }
    }
    val place = child.selectFirst("span.text-muted")
        ?.text()
        ?.filterNot(Char::isWhitespace)
        ?.ifBlank { "未知" }
        ?: "未知"

    return Course(
        courseId = courseId,
        courseName = courseName,
        courseTeacher = teacher,
        courseLocationIndex = locationIndex,
        courseTime = courseTime,
        coursePlace = place,
        isCurrentSemester = isSelectionSchedule,
    )
}

private fun teacherLookupKey(courseName: String, courseId: String): String {
    val name = courseName.substringBefore(' ')
    val section = courseId.split(Regex("\\s+"))
        .getOrNull(1)
        ?.drop(1)
        ?.take(2)
        .orEmpty()
    return if (section.isBlank()) name else "$name $section"
}
