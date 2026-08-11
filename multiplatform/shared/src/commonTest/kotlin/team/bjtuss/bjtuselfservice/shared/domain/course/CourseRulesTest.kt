package team.bjtuss.bjtuselfservice.shared.domain.course

import kotlin.test.Test
import kotlin.test.assertEquals

class CourseRulesTest {
    @Test
    fun schoolLocationHierarchyIsReversedForDisplayRegardlessOfBroadPlaceName() {
        assertEquals(
            "SY101-思源楼-海淀西校区",
            displayCoursePlace("海淀西校区，思源楼，SY101"),
        )
        assertEquals(
            "A101-教学楼-研究生唐山研究院",
            displayCoursePlace("研究生唐山研究院, 教学楼, A101"),
        )
        assertEquals("思源101", displayCoursePlace("思源101"))
    }

    @Test
    fun parsesRangesAndIndividualWeeks() {
        assertEquals(listOf(1, 2, 3, 5, 8, 9), parseCourseWeeks("第1-3周,第5周,第8-9周"))
    }

    @Test
    fun malformedWeekTextReturnsEmptyAndWeekZeroShowsAll() {
        val valid = course(1, "第1-3周")
        val malformed = course(2, "未知")

        assertEquals(emptyList(), parseCourseWeeks(malformed.courseTime))
        assertEquals(listOf(1, 2), coursesForWeek(listOf(valid, malformed), 0).map { it.id })
        assertEquals(listOf(1), coursesForWeek(listOf(valid, malformed), 2).map { it.id })
    }

    private fun course(id: Int, weeks: String) = Course(
        id = id,
        courseId = "course-$id",
        courseName = "测试课程$id",
        courseTeacher = "测试教师",
        courseLocationIndex = 1,
        courseTime = weeks,
        coursePlace = "测试教室",
        isCurrentSemester = true,
    )
}
