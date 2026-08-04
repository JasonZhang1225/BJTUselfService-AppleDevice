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

    @Test
    fun clearingCourseTypesRemovesRecordsByMappedType() {
        val grades = listOf(
            grade(1, name = "C312009B高级英语视听说[04]"),
            grade(2, name = "S1100120A计算机导论[01]"),
            grade(3, name = "P110011B体育Ⅰ[01]"),
        )
        val records = selectionRecordsForGradeIds(grades, setOf(1, 2, 3))
        val typeByCode = mapOf(
            "C312009B" to CourseType.REQUIRED,
            "S1100120A" to CourseType.ELECTIVE,
            "P110011B" to CourseType.PHYSICAL_EDUCATION,
        )

        assertEquals(
            listOf("C312009B高级英语视听说[04]", "P110011B体育Ⅰ[01]"),
            selectionRecordsExcludingTypes(records, typeByCode, setOf(CourseType.ELECTIVE))
                .map { it.courseName },
        )
        assertEquals(
            records,
            selectionRecordsExcludingTypes(records, typeByCode, setOf(CourseType.LIMITED)),
        )
        assertEquals(
            listOf("C312009B高级英语视听说[04]"),
            selectionRecordsExcludingTypes(
                records,
                typeByCode,
                setOf(CourseType.ELECTIVE, CourseType.PHYSICAL_EDUCATION),
            ).map { it.courseName },
        )
    }

    @Test
    fun clearingCourseTypesUsesSameGreedyCodeFallbackAsGradeLookup() {
        val grades = listOf(grade(1, name = "M202015BC语言程序设计[01]"))
        val records = selectionRecordsForGradeIds(grades, setOf(1))
        val typeByCode = mapOf("M202015B" to CourseType.REQUIRED)

        assertTrue(
            selectionRecordsExcludingTypes(records, typeByCode, setOf(CourseType.REQUIRED))
                .isEmpty(),
        )
        assertEquals(
            records,
            selectionRecordsExcludingTypes(records, typeByCode, setOf(CourseType.ELECTIVE)),
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
