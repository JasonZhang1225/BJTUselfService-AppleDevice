package team.bjtuss.bjtuselfservice.shared.domain.course

data class Course(
    val id: Int = 0,
    val courseId: String,
    val courseName: String,
    val courseTeacher: String,
    val courseLocationIndex: Int,
    val courseTime: String,
    val coursePlace: String,
    val isCurrentSemester: Boolean,
)

/**
 * 解析 Android v1.7.0 使用的周次文本，例如“第1-3周,第5周”。
 * 任意一段格式不合法时整条记录返回空集合，与原实现一致。
 */
fun parseCourseWeeks(courseTime: String): List<Int> = try {
    val normalized = courseTime.replace("第", "").replace("周", "")
    buildList {
        normalized.split(",").forEach { segment ->
            if ('-' in segment) {
                val range = segment.split("-")
                for (week in range[0].toInt()..range[1].toInt()) add(week)
            } else {
                add(segment.toInt())
            }
        }
    }
} catch (_: Exception) {
    emptyList()
}

/** 周次 0 表示“全部”，否则只保留包含该教学周的课程。 */
fun coursesForWeek(courses: List<Course>, week: Int): List<Course> = if (week == 0) {
    courses
} else {
    courses.filter { week in parseCourseWeeks(it.courseTime) }
}
