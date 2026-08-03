package team.bjtuss.bjtuselfservice.shared.domain.grade

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class GradeRulesTest {
    @Test
    fun normalModeCalculatesOnlySelectedSemesters() {
        val result = gradesForCalculation(
            grades = listOf(
                grade(1, "2025-2026-1", "A,95"),
                grade(2, "2025-2026-2", "C,69"),
            ),
            selectedSemesters = setOf("2025-2026-1"),
            isCourseSelectionMode = false,
            selectedGradeIds = emptySet(),
        )

        assertEquals(listOf(1), result.map { it.id })
    }

    @Test
    fun selectionModeKeepsFilteredOutSelectedGradesInCalculation() {
        val grades = listOf(
            grade(1, "2025-2026-1", "A,95"),
            grade(2, "2025-2026-2", "C,69"),
            grade(3, "2025-2026-2", "B,79"),
        )
        val selected = gradesForCalculation(
            grades = grades,
            selectedSemesters = setOf("2025-2026-1"),
            isCourseSelectionMode = true,
            selectedGradeIds = setOf(1, 2),
        )

        assertEquals(listOf(1, 2), selected.map { it.id })
        val result = assertIs<GradeInfoResult.Calculated>(calculateGradeInfo(selected))
        assertEquals(82.0, result.averageScore)
        assertEquals("你的加权平均分是 82.0", result.formattedMessage)
    }

    @Test
    fun invalidRowsAreSkippedAndEmptySelectionHasNoGrades() {
        val grades = listOf(
            grade(1, "2025-2026-1", "-,-"),
            grade(2, "2025-2026-1", "A,95", credits = "not-a-credit"),
            grade(3, "2025-2026-1", "B,79", credits = "0"),
        )

        assertEquals(GradeInfoResult.NoGrades, calculateGradeInfo(grades))
        assertEquals(
            GradeInfoResult.NoGrades,
            calculateGradeInfo(
                gradesForCalculation(grades, emptySet(), true, emptySet()),
            ),
        )
    }

    @Test
    fun weightedAverageUsesCreditsAndFormatsOneDecimal() {
        val result = assertIs<GradeInfoResult.Calculated>(
            calculateGradeInfo(
                listOf(
                    grade(1, "2025-2026-1", "A,95", credits = "1.0"),
                    grade(2, "2025-2026-1", "B,79", credits = "2.0"),
                ),
            ),
        )

        assertEquals(84.33333333333333, result.averageScore)
        assertEquals("你的加权平均分是 84.3", result.formattedMessage)
    }

    @Test
    fun sortingMatchesLegacyScoreParsingAndKeepsOriginalOrder() {
        val grades = listOf(
            grade(1, "2025-2026-1", "B,79"),
            grade(2, "2025-2026-1", "-,-"),
            grade(3, "2025-2026-1", "A,95"),
        )

        assertEquals(listOf(1, 2, 3), sortGrades(grades, GradeSortOrder.ORIGINAL).map { it.id })
        assertEquals(listOf(2, 1, 3), sortGrades(grades, GradeSortOrder.ASCENDING).map { it.id })
        assertEquals(listOf(3, 1, 2), sortGrades(grades, GradeSortOrder.DESCENDING).map { it.id })
        assertEquals(95, scoreForSorting("A,95"))
        assertEquals(-1, scoreForSorting("-,-"))
    }

    @Test
    fun displayNameKeepsLegacyTrimButDoesNotCrashOnShortNames() {
        assertEquals("高等数学", grade(1, "2025-2026-1", "A,95", name = "12345678高等数学1234").displayCourseName())
        assertEquals("短课程", grade(2, "2025-2026-1", "A,95", name = "短课程").displayCourseName())
    }

    private fun grade(
        id: Int,
        semester: String,
        score: String,
        credits: String = "2.0",
        name: String = "测试课程",
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
