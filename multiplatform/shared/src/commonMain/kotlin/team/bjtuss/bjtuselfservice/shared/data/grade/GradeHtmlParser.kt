package team.bjtuss.bjtuselfservice.shared.data.grade

import com.fleeksoft.ksoup.Ksoup
import team.bjtuss.bjtuselfservice.shared.domain.grade.Grade

enum class GradeParseFailure {
    TABLE_MISSING,
    MALFORMED_ROW,
}

sealed interface GradeTableParseResult {
    data class Success(val grades: List<Grade>) : GradeTableParseResult
    data class Failure(val reason: GradeParseFailure) : GradeTableParseResult
}

/**
 * 解析教务系统成绩表。失败结果只包含枚举，不保留或输出响应正文。
 */
fun parseGradeTable(html: String): GradeTableParseResult {
    val table = Ksoup.parse(html).selectFirst("table")
        ?: return GradeTableParseResult.Failure(GradeParseFailure.TABLE_MISSING)
    val rows = table.select("tr")
    if (rows.isEmpty()) {
        return GradeTableParseResult.Failure(GradeParseFailure.MALFORMED_ROW)
    }

    val grades = mutableListOf<Grade>()
    rows.drop(1).forEach { row ->
        val columns = row.select("td")
        if (columns.size < 8) {
            return GradeTableParseResult.Failure(GradeParseFailure.MALFORMED_ROW)
        }

        val year = columns[1].text().compactSchoolText()
        val courseName = columns[2].text().compactSchoolText()
        val credits = columns[3].text().compactSchoolText().ifBlank { "0.0" }
        val rawScore = columns[4].text().compactSchoolText()
        val teacher = columns[6].text().compactSchoolText()
        if (year.isBlank() || courseName.isBlank()) {
            return GradeTableParseResult.Failure(GradeParseFailure.MALFORMED_ROW)
        }

        val detailHtml = columns[7]
            .selectFirst("span[data-content]")
            ?.attr("data-content")
            .orEmpty()
        val detail = if (detailHtml.isBlank()) {
            ""
        } else {
            Ksoup.parse(detailHtml).selectFirst("div")?.text().orEmpty().trim()
        }

        grades += Grade(
            courseName = courseName,
            courseTeacher = teacher,
            courseScore = formatGradeScore(rawScore),
            courseCredits = credits,
            courseYear = year,
            semester = year,
            detail = detail,
        )
    }
    return GradeTableParseResult.Success(grades)
}

private val representativeScoreByLetter = mapOf(
    "A" to 95,
    "A-" to 87,
    "B+" to 83,
    "B" to 79,
    "B-" to 76,
    "C+" to 73,
    "C" to 69,
    "C-" to 66,
    "D+" to 63,
    "D" to 60,
    "F" to 30,
)

/** 保留 Android v1.7.0 的“等级,代表分数”存储形式。 */
fun formatGradeScore(rawScore: String): String {
    val score = rawScore.toIntOrNull()
    if (score != null) return "${letterForScore(score)},$score"
    val representative = representativeScoreByLetter[rawScore] ?: return "-,-"
    return "$rawScore,$representative"
}

private fun letterForScore(score: Int): String = when {
    score >= 90 -> "A"
    score >= 85 -> "A-"
    score >= 81 -> "B+"
    score >= 78 -> "B"
    score >= 75 -> "B-"
    score >= 71 -> "C+"
    score >= 68 -> "C"
    score >= 65 -> "C-"
    score >= 61 -> "D+"
    score == 60 -> "D"
    else -> "F"
}

private fun String.compactSchoolText(): String = filterNot(Char::isWhitespace)
