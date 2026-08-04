package team.bjtuss.bjtuselfservice.shared.data.grade

import com.fleeksoft.ksoup.Ksoup
import team.bjtuss.bjtuselfservice.shared.domain.grade.CourseType

sealed interface TrainingProgramParseResult {
    data class Success(val courseTypes: Map<String, String>) : TrainingProgramParseResult
    data class Failure(val reason: GradeParseFailure) : TrainingProgramParseResult
}

/**
 * 课程号单元格文本必须完整匹配该格式（如 `C312009B`），
 * 实测课程表行结构不规则（单行 td 数有 8/9/10 三种、组标题带 colspan），
 * 禁止按固定列下标解析，只能先定位课程号单元格再取右邻单元格。
 */
private val courseCodeCellPattern = Regex("^[A-Z]\\d{3}[A-Z0-9]{4,5}$")

private const val COURSE_TYPE_HEADER = "课程性质"

private const val PROGRAM_LINK_PREFIX = "https://aa.bjtu.edu.cn/training/training/program/stuview/"

private val programIdPattern = Regex("stuview/(\\d+)/?")

/** 性质值只接受白名单内的中文原文；td 写的表头行被白名单自然过滤。 */
private val courseTypeByStoredText = mapOf(
    "必修" to CourseType.REQUIRED,
    "限选" to CourseType.LIMITED,
    "任选" to CourseType.ELECTIVE,
    "体育" to CourseType.PHYSICAL_EDUCATION,
)

fun courseTypeForStoredText(text: String): CourseType? = courseTypeByStoredText[text]

/** UNKNOWN 没有对应原文，永远不会写入缓存。 */
fun CourseType.storedText(): String? =
    courseTypeByStoredText.entries.firstOrNull { it.value == this }?.key

/**
 * 提取方案列表页里的全部 stuview 详情页链接（辅修学生可能多条），
 * 按详情页 id 去重后返回绝对地址。
 */
fun parseProgramLinks(html: String): List<String> =
    Ksoup.parse(html)
        .select("a[href*=stuview]")
        .mapNotNull { link -> programIdPattern.find(link.attr("href"))?.groupValues?.get(1) }
        .distinct()
        .map { id -> "$PROGRAM_LINK_PREFIX$id/" }

/**
 * 解析培养方案详情页的课程表，产出 课程号 → 性质中文原文 的映射。
 * 课程表是含“课程性质”表头的那张；失败结果只包含枚举，不保留或输出响应正文。
 *
 * 课组上下文：组标题单元格（如 `体育类课程【4.0】`）只出现在每组首行且带
 * rowspan/colspan，后续行不再重复。按列维护 rowspan carry（列索引 → 标签+剩余行数），
 * 每门课取其最内层（列序最后一个）组标签。
 * 业务规则：学校对体育课口径混乱（培养方案 PDF 标体育专项“必修”、教务系统记“任选”、
 * 成绩单记任选），体育课独立为一类——最内层课组名含“体育”的课程，
 * 无论其课程性质列写的是什么（必修/任选），一律记为“体育”；
 * 美育/其他素养类保持其课程性质列的原值。
 */
fun parseProgramCourseTypes(html: String): TrainingProgramParseResult {
    val table = Ksoup.parse(html).select("table").firstOrNull { candidate ->
        candidate.select("tr").any { row ->
            row.select("th, td").any { it.text().compactProgramText() == COURSE_TYPE_HEADER }
        }
    } ?: return TrainingProgramParseResult.Failure(GradeParseFailure.TABLE_MISSING)

    val courseTypes = linkedMapOf<String, String>()
    val groupCarry = mutableMapOf<Int, Pair<String, Int>>()
    table.select("tr").forEach { row ->
        val carriesAtStart = groupCarry.toMap()
        val labelsByColumn = mutableListOf<Pair<Int, String>>()
        groupCarry.forEach { (column, carry) ->
            val (label, remaining) = carry
            if (remaining > 0) labelsByColumn += column to label
        }

        val cells = row.select("td")
        var column = 0
        cells.forEach { cell ->
            while (groupCarry[column]?.second == 0) {
                groupCarry.remove(column)
            }
            while (column in groupCarry) {
                column++
            }
            val text = cell.text().compactProgramText()
            val colspan = cell.attr("colspan").toIntOrNull()?.coerceAtLeast(1) ?: 1
            val rowspan = cell.attr("rowspan").toIntOrNull() ?: 1
            if (rowspan > 1 && GROUP_LABEL_MARK in text) {
                groupCarry[column] = text to (rowspan - 1)
                labelsByColumn += column to text
            }
            column += colspan
        }
        // 只递减行首已存在且未被本行替换/清理的 carry（新组的 carry 从下一行才开始消耗）。
        carriesAtStart.forEach { (key, carry) ->
            if (groupCarry[key] === carry) {
                groupCarry[key] = carry.first to carry.second - 1
            }
        }

        val codeIndex = cells.indexOfFirst {
            it.text().compactProgramText().matches(courseCodeCellPattern)
        }
        if (codeIndex < 0 || codeIndex + 1 >= cells.size) return@forEach
        val rawType = cells[codeIndex + 1].text().compactProgramText()

        val innermostGroup = labelsByColumn
            .sortedBy { it.first }
            .lastOrNull()
            ?.second
            .orEmpty()
        // 体育类课组内的课程（组首行与 rowspan carry 延续的后续行）一律归“体育”，
        // 不依赖课程性质列：体育Ⅰ（必修）、专项课（任选）、体育健康测试课（必修）口径不一。
        val storedType = if (SPORTS_GROUP_MARK in innermostGroup) {
            PHYSICAL_EDUCATION_STORED_TEXT
        } else {
            if (rawType !in courseTypeByStoredText) return@forEach
            rawType
        }
        courseTypes[cells[codeIndex].text().compactProgramText()] = storedType
    }
    return TrainingProgramParseResult.Success(courseTypes)
}

private const val GROUP_LABEL_MARK = "【"
private const val SPORTS_GROUP_MARK = "体育"
private const val PHYSICAL_EDUCATION_STORED_TEXT = "体育"

private fun String.compactProgramText(): String = filterNot(Char::isWhitespace)
