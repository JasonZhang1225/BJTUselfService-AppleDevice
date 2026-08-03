package team.bjtuss.bjtuselfservice.shared.domain.home

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import team.bjtuss.bjtuselfservice.shared.domain.exam.ExamSchedule
import team.bjtuss.bjtuselfservice.shared.domain.homework.Homework

class HomeAgendaTest {
    @Test
    fun agendaAlwaysCoversTheCurrentMondayToSunday() {
        val agenda = buildHomeAgenda(
            homework = emptyList(),
            exams = emptyList(),
            today = LocalDate(2026, 7, 30),
            now = LocalDateTime(2026, 7, 30, 10, 0),
            timeZone = TimeZone.UTC,
        )

        assertEquals(LocalDate(2026, 7, 27), agenda.days.first().date)
        assertEquals(LocalDate(2026, 8, 2), agenda.days.last().date)
    }

    @Test
    fun homeworkStartAndMidnightDeadlineUseAndroidBaselineDates() {
        val homework = homework(
            openDate = "2026-07-29 08:00",
            endTime = "2026-07-31 00:00",
        )
        val agenda = buildHomeAgenda(
            homework = listOf(homework),
            exams = emptyList(),
            today = LocalDate(2026, 7, 30),
            now = LocalDateTime(2026, 7, 30, 10, 0),
            timeZone = TimeZone.UTC,
        )

        assertEquals(listOf(homework), agenda.days.single { it.date == LocalDate(2026, 7, 29) }.homeworkStarting)
        assertEquals(listOf(homework), agenda.days.single { it.date == LocalDate(2026, 7, 30) }.homeworkDue)
    }

    @Test
    fun examDateUsesTheLeadingSchoolDateAndRejectsMalformedRows() {
        val exam = exam("2026-08-01 09:00 思源楼")
        val malformed = exam("待通知")

        assertEquals(LocalDate(2026, 8, 1), examDate(exam))
        assertNull(examDate(malformed))
    }

    @Test
    fun dueSoonListExcludesSubmittedAndSortsByDeadline() {
        val later = homework(title = "later", endTime = "2026-07-31 09:00")
        val sooner = homework(title = "sooner", endTime = "2026-07-30 12:00")
        val submitted = homework(title = "done", endTime = "2026-07-30 11:00", subStatus = "已提交")

        val agenda = buildHomeAgenda(
            homework = listOf(later, submitted, sooner),
            exams = emptyList(),
            today = LocalDate(2026, 7, 30),
            now = LocalDateTime(2026, 7, 30, 10, 0),
            timeZone = TimeZone.UTC,
        )

        assertEquals(listOf("sooner", "later"), agenda.dueSoonHomework.map(Homework::title))
    }

    private fun homework(
        title: String = "作业",
        openDate: String = "2026-07-29 08:00",
        endTime: String = "2026-07-30 12:00",
        subStatus: String = "未提交",
    ) = Homework(
        upId = title.hashCode(),
        idSnId = null,
        score = "",
        userId = 1,
        courseId = 1,
        courseName = "程序设计",
        title = title,
        content = "",
        createDate = "",
        endTime = endTime,
        openDate = openDate,
        status = 0,
        submitCount = 0,
        allCount = 1,
        subStatus = subStatus,
        scoreId = 0,
        homeworkType = 0,
    )

    private fun exam(timeAndPlace: String) = ExamSchedule(
        examType = "期末考试",
        courseName = "高等数学",
        examTimeAndPlace = timeAndPlace,
        examStatus = "正常",
        detail = "",
    )
}
