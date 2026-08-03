package team.bjtuss.bjtuselfservice.shared.domain.grade

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GradeSelectionTest {
    @Test
    fun syncIgnoresGeneratedIdsButDetectsChangedOrDifferentGrades() {
        val network = grade(0, name = "课程A", score = "A,95")
        val local = network.copy(id = 42)

        assertFalse(gradeDataNeedsSync(listOf(network), listOf(local)))
        assertTrue(gradeDataNeedsSync(listOf(network.copy(courseScore = "A-,87")), listOf(local)))
        assertTrue(gradeDataNeedsSync(listOf(grade(0, name = "课程B")), listOf(local)))
    }

    @Test
    fun restoresSelectionWhenDatabaseIdsOrScoreAndCreditsChange() {
        val original = listOf(grade(1, score = "B,79", credits = "2.0"))
        val records = selectionRecordsForGradeIds(original, setOf(1))
        val refreshed = listOf(grade(101, score = "A,95", credits = "3.0"))

        assertEquals(setOf(101), gradeIdsForSelectionRecords(refreshed, records))
    }

    @Test
    fun duplicateRecordsUseOccurrenceAndDoNotSelectOneGradeTwice() {
        val original = listOf(
            grade(1, score = "C,69"),
            grade(2, score = "A,95"),
        )
        val records = selectionRecordsForGradeIds(original, setOf(1, 2))
        val refreshed = listOf(
            grade(101, score = "A,95"),
            grade(102, score = "C,69"),
        )

        assertEquals(setOf(102, 101), gradeIdsForSelectionRecords(refreshed, records))
    }

    @Test
    fun sameCourseInDifferentSemestersRemainsIndependent() {
        val original = listOf(
            grade(1, semester = "2025-2026-1"),
            grade(2, semester = "2025-2026-2"),
        )
        val records = selectionRecordsForGradeIds(original, setOf(2))
        val refreshed = listOf(
            grade(101, semester = "2025-2026-1"),
            grade(102, semester = "2025-2026-2"),
        )

        assertEquals(setOf(102), gradeIdsForSelectionRecords(refreshed, records))
    }

    @Test
    fun partialSnapshotPreservesDormantSelectionAndRefreshesVisibleRecord() {
        val original = listOf(
            grade(1, name = "Course A", score = "B,79", semester = "2025-2026-1"),
            grade(2, name = "Course B", score = "C,69", semester = "2025-2026-2"),
        )
        val records = selectionRecordsForGradeIds(original, setOf(1, 2))
        val partial = listOf(
            grade(101, name = "Course A", score = "A,95", semester = "2025-2026-1"),
        )

        val preserved = selectionRecordsForGradeIdsPreservingUnmatched(
            grades = partial,
            storedRecords = records,
            selectedGradeIds = setOf(101),
        )

        assertEquals(2, preserved.size)
        assertTrue(preserved.any { it.courseName == "Course A" && it.lastKnownScore == "A,95" })
        assertTrue(preserved.any { it.courseName == "Course B" })
        assertEquals(
            setOf(201, 202),
            gradeIdsForSelectionRecords(
                grades = listOf(
                    grade(201, name = "Course A", score = "A,95", semester = "2025-2026-1"),
                    grade(202, name = "Course B", score = "C,69", semester = "2025-2026-2"),
                ),
                records = preserved,
            ),
        )
    }

    @Test
    fun clearingSemestersRemovesVisibleAndDormantRecordsInScope() {
        val records = selectionRecordsForGradeIds(
            grades = listOf(
                grade(1, semester = "2025-2026-1"),
                grade(2, semester = "2025-2026-2"),
            ),
            selectedGradeIds = setOf(1, 2),
        )

        assertEquals(
            listOf("2025-2026-2"),
            selectionRecordsExcludingSemesters(records, setOf("2025-2026-1")).map { it.semester },
        )
    }

    private fun grade(
        id: Int,
        semester: String = "2025-2026-1",
        score: String = "B,79",
        credits: String = "2.0",
        name: String = "同名课程",
    ) = Grade(
        id = id,
        courseName = name,
        courseTeacher = "测试教师",
        courseScore = score,
        courseCredits = credits,
        courseYear = semester,
        semester = semester,
    )
}
