package team.bjtuss.bjtuselfservice.shared.data.exam

import com.fleeksoft.ksoup.Ksoup
import team.bjtuss.bjtuselfservice.shared.domain.exam.ExamSchedule

enum class ExamScheduleParseFailure {
    TABLE_MISSING,
    MALFORMED_ROW,
}

sealed interface ExamScheduleParseResult {
    data class Success(val exams: List<ExamSchedule>) : ExamScheduleParseResult
    data class Failure(val reason: ExamScheduleParseFailure) : ExamScheduleParseResult
}

/** 解析 v1.7.0 教务考试表。失败只返回枚举，不保留响应正文。 */
fun parseExamScheduleTable(html: String): ExamScheduleParseResult {
    val body = Ksoup.parse(html).selectFirst("tbody")
        ?: return ExamScheduleParseResult.Failure(ExamScheduleParseFailure.TABLE_MISSING)
    val exams = mutableListOf<ExamSchedule>()
    body.select("tr").forEach { row ->
        val columns = row.select("td")
        if (columns.size < 6) {
            return ExamScheduleParseResult.Failure(ExamScheduleParseFailure.MALFORMED_ROW)
        }
        exams += ExamSchedule(
            examType = columns[1].text().normalizedSchoolText(),
            courseName = columns[2].text().normalizedSchoolText(),
            examTimeAndPlace = columns[3].text().normalizedSchoolText(),
            examStatus = columns[4].text().normalizedSchoolText(),
            detail = columns[5].text().normalizedSchoolText(),
        )
    }
    return ExamScheduleParseResult.Success(exams)
}

private fun String.normalizedSchoolText(): String = trim().replace(Regex("\\s+"), " ")
