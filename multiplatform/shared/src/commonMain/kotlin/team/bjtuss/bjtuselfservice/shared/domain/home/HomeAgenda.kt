package team.bjtuss.bjtuselfservice.shared.domain.home

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.minutes
import team.bjtuss.bjtuselfservice.shared.domain.exam.ExamSchedule
import team.bjtuss.bjtuselfservice.shared.domain.homework.Homework
import team.bjtuss.bjtuselfservice.shared.domain.homework.isHomeworkDueSoon
import team.bjtuss.bjtuselfservice.shared.domain.homework.parseSchoolLocalDateTime

data class HomeAgendaDay(
    val date: LocalDate,
    val homeworkStarting: List<Homework>,
    val homeworkDue: List<Homework>,
    val exams: List<ExamSchedule>,
) {
    val eventCount: Int
        get() = homeworkStarting.size + homeworkDue.size + exams.size
}

data class HomeAgenda(
    val days: List<HomeAgendaDay>,
    val dueSoonHomework: List<Homework>,
) {
    init {
        require(days.size == 7)
    }
}

/**
 * Builds the Monday-to-Sunday agenda used by the v1.7.0 home screen.
 * A deadline at exactly 00:00 belongs to the previous calendar day, matching the Android baseline.
 */
fun buildHomeAgenda(
    homework: List<Homework>,
    exams: List<ExamSchedule>,
    today: LocalDate,
    now: LocalDateTime,
    timeZone: TimeZone,
): HomeAgenda {
    val monday = today.minus(today.dayOfWeek.isoDayNumber - 1, DateTimeUnit.DAY)
    val days = (0..6).map { offset ->
        val date = monday.plus(offset, DateTimeUnit.DAY)
        HomeAgendaDay(
            date = date,
            homeworkStarting = homework.filter { homeworkStartDate(it) == date }
                .sortedWith(compareBy(Homework::courseName, Homework::title)),
            homeworkDue = homework.filter { homeworkDueDate(it, timeZone) == date }
                .sortedBy(Homework::endTime),
            exams = exams.filter { examDate(it) == date }
                .sortedBy(ExamSchedule::examTimeAndPlace),
        )
    }
    val dueSoon = homework.filter { isHomeworkDueSoon(it, now, timeZone) }
        .sortedWith(compareBy<Homework> { parseSchoolLocalDateTime(it.endTime) == null }
            .thenBy { parseSchoolLocalDateTime(it.endTime) })
    return HomeAgenda(days, dueSoon)
}

fun homeworkStartDate(homework: Homework): LocalDate? =
    parseSchoolLocalDateTime(homework.openDate)?.date

fun homeworkDueDate(homework: Homework, timeZone: TimeZone): LocalDate? =
    parseSchoolLocalDateTime(homework.endTime)
        ?.toInstant(timeZone)
        ?.minus(1.minutes)
        ?.toLocalDateTime(timeZone)
        ?.date

fun examDate(exam: ExamSchedule): LocalDate? = try {
    LocalDate.parse(exam.examTimeAndPlace.trim().substringBefore(' '))
} catch (_: IllegalArgumentException) {
    null
}
