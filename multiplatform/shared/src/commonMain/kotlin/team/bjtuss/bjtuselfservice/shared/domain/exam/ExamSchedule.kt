package team.bjtuss.bjtuselfservice.shared.domain.exam

data class ExamSchedule(
    val id: Int = 0,
    val examType: String,
    val courseName: String,
    val examTimeAndPlace: String,
    val examStatus: String,
    val detail: String,
)
