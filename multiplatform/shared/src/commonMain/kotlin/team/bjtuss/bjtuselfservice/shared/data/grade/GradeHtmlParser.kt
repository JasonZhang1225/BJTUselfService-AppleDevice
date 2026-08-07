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
        // 教务 data-content 多为带 <br> 的 HTML；保留换行，勿用 .text() 压成一行。
        val detail = parseGradeDetailHtml(detailHtml)

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

/**
 * 从成绩详情 data-content HTML 抽出多行纯文本。
 * 学校页面常见：`平时成绩…<br>期末成绩…<br>最终成绩…<br>备注信息…`。
 */
internal fun parseGradeDetailHtml(detailHtml: String): String {
    if (detailHtml.isBlank()) return ""
    val root = Ksoup.parse(detailHtml)
    val container = root.selectFirst("div") ?: root.body()
    return htmlFragmentToMultilineText(container.html())
}

/**
 * 把片段 HTML 转成可读多行文本：`<br>`/`</p>`/`</div>` 等变成换行，其余标签去掉。
 * 无标签的纯文本也会走 [formatGradeDetailForDisplay] 按成绩字段再断行。
 */
internal fun htmlFragmentToMultilineText(html: String): String {
    if (html.isBlank()) return ""
    val withBreaks = html
        .replace(Regex("(?i)<br\\s*/?>"), "\n")
        .replace(Regex("(?i)</p\\s*>"), "\n")
        .replace(Regex("(?i)</div\\s*>"), "\n")
        .replace(Regex("(?i)</li\\s*>"), "\n")
        .replace(Regex("(?i)</tr\\s*>"), "\n")
        .replace(Regex("(?i)</h[1-6]\\s*>"), "\n")
        .replace(Regex("(?i)<[^>]+>"), "")
        .replace("&nbsp;", " ")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
    val lines = withBreaks
        .lines()
        .map { it.replace(Regex("[ \\t\\u00a0]+"), " ").trim() }
        .filter { it.isNotEmpty() }
    val joined = lines.joinToString("\n")
    return formatGradeDetailForDisplay(joined)
}

/**
 * 展示层断行：即使缓存里已是单行（旧解析压扁，或源端无 br），
 * 也在「平时成绩 / 期末成绩 / 最终成绩 / 备注信息」等字段前插入换行。
 */
fun formatGradeDetailForDisplay(detail: String): String {
    if (detail.isBlank()) return detail
    val labels = listOf(
        "平时成绩",
        "期中成绩",
        "期末成绩",
        "实验成绩",
        "最终成绩",
        "总评成绩",
        "备注信息",
        "备注",
    )
    var text = detail.replace("\r\n", "\n").replace('\r', '\n')
    // 已有换行则只做行级 trim
    if (!text.contains('\n')) {
        for (label in labels) {
            text = text.replace(Regex("(?<!^|\\n)(?=$label)"), "\n")
        }
    }
    return text.lines()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .joinToString("\n")
}
