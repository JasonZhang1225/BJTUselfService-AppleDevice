package team.bjtuss.bjtuselfservice.shared.domain.homework

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HomeworkRulesTest {
    private val now = LocalDateTime(2026, 7, 30, 8, 0)

    @Test
    fun parsesSchoolTimestampStrictly() {
        assertEquals(LocalDateTime(2026, 7, 30, 8, 15), parseSchoolLocalDateTime("2026-07-30 08:15"))
        assertNull(parseSchoolLocalDateTime("2026/07/30 08:15"))
    }

    @Test
    fun filterCombinesCourseAndExpiryWhileKeepingInvalidDates() {
        val items = listOf(
            homework(1, "课程A", "2026-07-30 09:00"),
            homework(2, "课程A", "2026-07-29 09:00"),
            homework(3, "课程A", "未知"),
            homework(4, "课程B", "2026-07-30 09:00"),
        )

        assertEquals(
            listOf(1, 3),
            filterHomework(items, setOf("课程A"), hideExpired = true, now = now).map { it.id },
        )
    }

    @Test
    fun sortPlacesInvalidDatesLikeAndroidV170() {
        val items = listOf(
            homework(1, "课程", "2026-08-01 08:00"),
            homework(2, "课程", "未知"),
            homework(3, "课程", "2026-07-31 08:00"),
        )

        assertEquals(listOf(3, 1, 2), sortHomework(items, HomeworkSortOrder.ASCENDING).map { it.id })
        assertEquals(listOf(1, 3, 2), sortHomework(items, HomeworkSortOrder.DESCENDING).map { it.id })
    }

    @Test
    fun dueSoonUsesInclusiveWholeHourWindowAndSkipsSubmittedWork() {
        val dueIn48Hours = homework(1, "课程", "2026-08-01 08:59")
        val dueLater = homework(2, "课程", "2026-08-01 09:01")
        val submitted = homework(3, "课程", "2026-07-30 09:00", subStatus = "已提交")

        assertTrue(isHomeworkDueSoon(dueIn48Hours, now, TimeZone.UTC))
        assertFalse(isHomeworkDueSoon(dueLater, now, TimeZone.UTC))
        assertFalse(isHomeworkDueSoon(submitted, now, TimeZone.UTC))
        assertEquals(1, dueSoonHomeworkCount(listOf(dueIn48Hours, dueLater, submitted), now, TimeZone.UTC))
    }

    private fun homework(
        id: Int,
        course: String,
        deadline: String,
        subStatus: String = "未提交",
    ) = Homework(
        id = id,
        upId = id,
        idSnId = null,
        score = "",
        userId = 1,
        courseId = 1,
        courseName = course,
        title = "作业$id",
        content = "",
        createDate = "2026-07-01 08:00",
        endTime = deadline,
        openDate = "2026-07-01 08:00",
        status = 0,
        submitCount = 0,
        allCount = 0,
        subStatus = subStatus,
        scoreId = 0,
        homeworkType = 0,
    )
}
