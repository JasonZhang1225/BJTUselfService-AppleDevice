package team.bjtuss.bjtuselfservice.shared.domain.grade

import kotlin.math.floor

/** v1.7.0 会去掉服务端课程名前 8、后 4 个字符；这里保留行为但避免短字符串崩溃。 */
fun Grade.displayCourseName(): String = courseName.trim().let { raw ->
    if (raw.length > 12) raw.substring(8, raw.length - 4) else raw
}

/** 与 Android v1.7.0 成绩页一致的筛选规则：空集合表示不过滤。 */
fun filterGradesBySemester(
    grades: List<Grade>,
    selectedSemesters: Set<String>,
): List<Grade> = if (selectedSemesters.isEmpty()) {
    grades
} else {
    grades.filter { it.semester in selectedSemesters }
}

/**
 * 普通模式按学期计算；自选模式始终按全部成绩中的选中 ID 计算，
 * 即使这些成绩被当前学期筛选隐藏也不会丢失。
 */
fun gradesForCalculation(
    grades: List<Grade>,
    selectedSemesters: Set<String>,
    isCourseSelectionMode: Boolean,
    selectedGradeIds: Set<Int>,
): List<Grade> = if (isCourseSelectionMode) {
    grades.filter { it.id in selectedGradeIds }
} else {
    filterGradesBySemester(grades, selectedSemesters)
}

fun sortGrades(
    grades: List<Grade>,
    order: GradeSortOrder,
): List<Grade> = when (order) {
    GradeSortOrder.ORIGINAL -> grades
    GradeSortOrder.ASCENDING -> grades.sortedBy { scoreForSorting(it.courseScore) }
    GradeSortOrder.DESCENDING -> grades.sortedByDescending { scoreForSorting(it.courseScore) }
}

/**
 * 保留 Android v1.7.0 的排序解析方式：去掉逗号和非数字/小数点字符后再取整数。
 * 无法解析时返回 -1，因此无效成绩在升序时排在最前、降序时排在最后。
 */
fun scoreForSorting(score: String): Int {
    val cleanScore = score.replace(",", "").filter { it in '0'..'9' || it == '.' }
    return cleanScore.toDoubleOrNull()?.toInt() ?: -1
}

/**
 * 按有效成绩的学分加权计算平均分。成绩取逗号后的第二段，无法解析的行被忽略。
 */
fun calculateGradeInfo(grades: List<Grade>): GradeInfoResult {
    var totalScore = 0.0
    var totalCredits = 0.0

    grades.forEach { grade ->
        val score = grade.courseScore.split(",").getOrNull(1)?.toDoubleOrNull()
        val credits = grade.courseCredits.toDoubleOrNull()
        if (score != null && credits != null) {
            totalScore += score * credits
            totalCredits += credits
        }
    }

    if (totalCredits == 0.0) return GradeInfoResult.NoGrades

    val average = totalScore / totalCredits
    return GradeInfoResult.Calculated(
        averageScore = average,
        formattedMessage = "你的加权平均分是 ${formatOneDecimal(average)}",
    )
}

private fun formatOneDecimal(value: Double): String {
    // 成绩是非负值；显式使用 half-up，保持 Java String.format("%.1f") 的显示结果。
    val rounded = floor(value * 10.0 + 0.5) / 10.0
    val text = rounded.toString()
    return if ('.' in text) text else "$text.0"
}
