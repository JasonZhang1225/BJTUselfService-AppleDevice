package team.bjtuss.bjtuselfservice.shared.data.home

import team.bjtuss.bjtuselfservice.shared.domain.change.DataChangeKind
import team.bjtuss.bjtuselfservice.shared.domain.change.detectDataChanges
import team.bjtuss.bjtuselfservice.shared.domain.grade.Grade
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GradeSemanticEqualityTest {
    @Test
    fun ignoresIdAndDetailDifferences() {
        val old = Grade(
            id = 1,
            courseName = "高等数学",
            courseTeacher = "张老师",
            courseScore = "A,95",
            courseCredits = "3.0",
            courseYear = "2025-2026-1",
            semester = "2025-2026-1",
            detail = "平时成绩 40 期末成绩 60",
        )
        val new = old.copy(
            id = 99,
            detail = "平时成绩：40\n期末成绩：60",
        )
        assertTrue(gradesSemanticallyEqual(old, new))
        val changes = detectDataChanges(
            before = listOf(old),
            after = listOf(new),
            identity = { listOf(it.courseName, it.courseTeacher, it.courseYear, it.semester) },
            equivalent = ::gradesSemanticallyEqual,
        )
        assertEquals(emptyList(), changes)
    }

    @Test
    fun detectsRealScoreChange() {
        val old = Grade(
            id = 1,
            courseName = "高等数学",
            courseTeacher = "张老师",
            courseScore = "A,95",
            courseCredits = "3.0",
            courseYear = "2025-2026-1",
            semester = "2025-2026-1",
        )
        val new = old.copy(id = 2, courseScore = "A-,87")
        val changes = detectDataChanges(
            before = listOf(old),
            after = listOf(new),
            identity = { listOf(it.courseName, it.courseTeacher, it.courseYear, it.semester) },
            equivalent = ::gradesSemanticallyEqual,
        )
        assertEquals(listOf(DataChangeKind.MODIFIED), changes.map { it.kind })
    }
}
