package team.bjtuss.bjtuselfservice.shared.domain.grade

/**
 * 不依赖 Room 或任何平台 API 的成绩领域模型。
 *
 * 字符串字段暂时保留 Android v1.7.0 的原始数据形态，避免在尚未迁移解析层时
 * 擅自改变服务端数据的兼容语义。
 */
data class Grade(
    val id: Int = 0,
    val courseName: String,
    val courseTeacher: String,
    val courseScore: String,
    val courseCredits: String,
    val courseYear: String,
    val semester: String,
    val detail: String = "",
)

data class GradeSelectionRecord(
    val courseName: String,
    val courseTeacher: String,
    val courseYear: String,
    val semester: String,
    val lastKnownScore: String,
    val lastKnownCredits: String,
    val occurrence: Int,
)

enum class GradeSortOrder {
    ORIGINAL,
    ASCENDING,
    DESCENDING,
}

sealed interface GradeInfoResult {
    data object NoGrades : GradeInfoResult

    data class Calculated(
        val averageScore: Double,
        val formattedMessage: String,
    ) : GradeInfoResult
}
