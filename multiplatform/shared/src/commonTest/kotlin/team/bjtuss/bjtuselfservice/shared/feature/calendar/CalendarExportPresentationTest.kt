package team.bjtuss.bjtuselfservice.shared.feature.calendar

import kotlin.test.Test
import kotlin.test.assertEquals

class CalendarExportPresentationTest {
    @Test
    fun courseImportSuccessNamesBothScheduleAndCalendar() {
        assertEquals(
            "本学期课表已导入日历“本学期课表”，新增 0 项，更新 21 项。",
            calendarInstallSuccessMessage(
                calendarName = "本学期课表",
                successSubject = "本学期课表",
                insertedCount = 0,
                updatedCount = 21,
            ),
        )
    }

    @Test
    fun examImportSuccessNamesExamCalendarWithoutBatchClaim() {
        assertEquals(
            "已导入日历“考试安排”，新增 1 项，更新 0 项。",
            calendarInstallSuccessMessage(
                calendarName = "考试安排",
                successSubject = null,
                insertedCount = 1,
                updatedCount = 0,
            ),
        )
    }
}
