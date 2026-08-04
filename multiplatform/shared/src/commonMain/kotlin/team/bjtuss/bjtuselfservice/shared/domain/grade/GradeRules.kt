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

/** 课程号（如 `C312009B`）位于课程名开头，与培养方案映射的键同格式。 */
private val courseCodePrefixPattern = Regex("^[A-Z]\\d{3}[A-Z0-9]{4,5}")

fun courseCodeOf(courseName: String): String? =
    courseCodePrefixPattern.find(courseName.trim())?.value

fun Grade.courseCode(): String? = courseCodeOf(courseName)

/**
 * map 感知的性质查询。前缀正则是贪婪的：课程名以拉丁字母开头时
 * （如 `M202015BC语言程序设计[01]`），字母会被吞进候选得到 9 字符的 `M202015BC`。
 * 实测方案页课程号全部 8 字符，因此候选查不到且长度 > 8 时回退取前 8 字符再查。
 * 所有按课程名查性质映射的入口统一走这里，映射查不到按 UNKNOWN 处理，绝不误判为任选。
 */
fun courseTypeForCourseName(
    courseName: String,
    typeByCode: Map<String, CourseType>,
): CourseType {
    val candidate = courseCodeOf(courseName) ?: return CourseType.UNKNOWN
    typeByCode[candidate]?.let { return it }
    if (candidate.length > 8) {
        typeByCode[candidate.substring(0, 8)]?.let { return it }
    }
    return CourseType.UNKNOWN
}

fun courseTypeOfGrade(grade: Grade, typeByCode: Map<String, CourseType>): CourseType =
    courseTypeForCourseName(grade.courseName, typeByCode)

fun CourseType.displayName(): String = when (this) {
    CourseType.REQUIRED -> "必修"
    CourseType.LIMITED -> "限选"
    CourseType.ELECTIVE -> "任选"
    CourseType.PHYSICAL_EDUCATION -> "体育"
    CourseType.UNKNOWN -> "未知"
}

/** 按课程性质排除成绩；空集合表示不排除。 */
fun filterGradesByType(
    grades: List<Grade>,
    typeByCode: Map<String, CourseType>,
    excludedTypes: Set<CourseType>,
): List<Grade> = if (excludedTypes.isEmpty()) {
    grades
} else {
    grades.filter { courseTypeOfGrade(it, typeByCode) !in excludedTypes }
}

/**
 * 普通模式按学期计算；自选模式始终按全部成绩中的选中 ID 计算，
 * 即使这些成绩被当前学期筛选隐藏也不会丢失。
 * 两种模式都会再叠加课程性质排除（核心诉求：排除任选课再算加权平均）。
 */
fun gradesForCalculation(
    grades: List<Grade>,
    selectedSemesters: Set<String>,
    isCourseSelectionMode: Boolean,
    selectedGradeIds: Set<Int>,
    typeByCode: Map<String, CourseType> = emptyMap(),
    excludedTypes: Set<CourseType> = emptySet(),
): List<Grade> {
    val candidates = if (isCourseSelectionMode) {
        grades.filter { it.id in selectedGradeIds }
    } else {
        filterGradesBySemester(grades, selectedSemesters)
    }
    return filterGradesByType(candidates, typeByCode, excludedTypes)
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
