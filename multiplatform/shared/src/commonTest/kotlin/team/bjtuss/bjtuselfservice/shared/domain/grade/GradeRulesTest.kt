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
        assertEquals(
            listOf(3, 2, 1),
            sortGrades(grades, GradeSortOrder.ORIGINAL_REVERSED).map { it.id },
        )
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

    @Test
    fun courseCodeComesFromNamePrefixAndTypeDefaultsToUnknown() {
        val required = grade(1, "2025-2026-1", "A,95", name = "C312009B高级英语视听说[04]")
        val elective = grade(2, "2025-2026-1", "B,79", name = "S1100120A计算机导论[01]")
        val unmapped = grade(3, "2025-2026-1", "C,69", name = "M710033B大学物理[01]")
        val noCode = grade(4, "2025-2026-1", "-,-", name = "英语认定")

        assertEquals("C312009B", required.courseCode())
        assertEquals("S1100120A", elective.courseCode())
        assertEquals(null, noCode.courseCode())

        val typeByCode = mapOf(
            "C312009B" to CourseType.REQUIRED,
            "S1100120A" to CourseType.ELECTIVE,
        )
        assertEquals(CourseType.REQUIRED, courseTypeOfGrade(required, typeByCode))
        assertEquals(CourseType.ELECTIVE, courseTypeOfGrade(elective, typeByCode))
        assertEquals(CourseType.UNKNOWN, courseTypeOfGrade(unmapped, typeByCode))
        assertEquals(CourseType.UNKNOWN, courseTypeOfGrade(noCode, typeByCode))
    }

    @Test
    fun greedyCodeSwallowingLatinLedNameFallsBackToEightCharCode() {
        // 实测：courseName 去空白后课程名以拉丁字母开头，贪婪候选吞掉首字母得到 9 字符。
        val greedyTrapped = grade(1, "2025-2026-1", "A,95", name = "M202015BC语言程序设计[01]")
        val realNineChar = grade(2, "2025-2026-1", "B,79", name = "S1100120A计算机导论[01]")

        assertEquals("M202015BC", greedyTrapped.courseCode())

        // 9 字符候选查不到时回退前 8 字符：M202015B 在方案中是必修。
        assertEquals(
            CourseType.REQUIRED,
            courseTypeOfGrade(greedyTrapped, mapOf("M202015B" to CourseType.REQUIRED)),
        )
        // 真正的 9 字符课程号直接命中，不受回退影响。
        assertEquals(
            CourseType.ELECTIVE,
            courseTypeOfGrade(realNineChar, mapOf("S1100120A" to CourseType.ELECTIVE)),
        )
        // 候选本身命中时优先于回退。
        assertEquals(
            CourseType.ELECTIVE,
            courseTypeOfGrade(
                realNineChar,
                mapOf("S1100120" to CourseType.REQUIRED, "S1100120A" to CourseType.ELECTIVE),
            ),
        )
        // 两级都查不到仍为 UNKNOWN。
        assertEquals(CourseType.UNKNOWN, courseTypeOfGrade(greedyTrapped, emptyMap()))
    }

    @Test
    fun scheduleCourseIdWithSectionSuffixUsesTrainingProgramType() {
        val mapping = mapOf("C108002B" to CourseType.REQUIRED)

        assertEquals(
            CourseType.REQUIRED,
            courseTypeForCourseName("C108002B [04]", mapping),
        )
        assertEquals(
            CourseType.UNKNOWN,
            courseTypeForCourseName("C108002B [04]", emptyMap()),
        )
    }

    @Test
    fun filterGradesByTypeExcludesOnlyRequestedTypes() {
        val grades = listOf(
            grade(1, "2025-2026-1", "A,95", name = "C312009B高级英语视听说[04]"),
            grade(2, "2025-2026-1", "B,79", name = "S1100120A计算机导论[01]"),
            grade(3, "2025-2026-1", "C,69", name = "英语认定"),
        )
        val typeByCode = mapOf(
            "C312009B" to CourseType.REQUIRED,
            "S1100120A" to CourseType.ELECTIVE,
        )

        assertEquals(grades, filterGradesByType(grades, typeByCode, emptySet()))
        assertEquals(
            listOf(1, 3),
            filterGradesByType(grades, typeByCode, setOf(CourseType.ELECTIVE)).map { it.id },
        )
        assertEquals(
            listOf(2),
            filterGradesByType(
                grades,
                typeByCode,
                setOf(CourseType.REQUIRED, CourseType.UNKNOWN),
            ).map { it.id },
        )
    }

    @Test
    fun physicalEducationIsItsOwnCategoryWithLabel() {
        val physical = grade(1, "2025-2026-1", "A,95", name = "P110011B体育Ⅰ[01]")
        val typeByCode = mapOf(
            "P110011B" to CourseType.PHYSICAL_EDUCATION,
            "S1100120A" to CourseType.ELECTIVE,
        )

        assertEquals("体育", CourseType.PHYSICAL_EDUCATION.displayName())
        assertEquals(
            CourseType.PHYSICAL_EDUCATION,
            courseTypeOfGrade(physical, typeByCode),
        )
        assertEquals(
            listOf(2),
            filterGradesByType(
                listOf(
                    physical,
                    grade(2, "2025-2026-1", "B,79", name = "S1100120A计算机导论[01]"),
                ),
                typeByCode,
                setOf(CourseType.PHYSICAL_EDUCATION),
            ).map { it.id },
        )
    }

    @Test
    fun gradesForCalculationCombinesSemesterAndTypeExclusion() {
        val grades = listOf(
            grade(1, "2025-2026-1", "A,95", name = "C312009B高级英语视听说[04]"),
            grade(2, "2025-2026-1", "B,79", name = "S1100120A计算机导论[01]"),
            grade(3, "2025-2026-2", "C,69", name = "M710033B大学物理[01]"),
        )
        val typeByCode = mapOf(
            "C312009B" to CourseType.REQUIRED,
            "S1100120A" to CourseType.ELECTIVE,
            "M710033B" to CourseType.LIMITED,
        )

        assertEquals(
            listOf(1),
            gradesForCalculation(
                grades = grades,
                selectedSemesters = setOf("2025-2026-1"),
                isCourseSelectionMode = false,
                selectedGradeIds = emptySet(),
                typeByCode = typeByCode,
                excludedTypes = setOf(CourseType.ELECTIVE),
            ).map { it.id },
        )
        // 自选模式下性质排除同样叠加在选中集合上。
        assertEquals(
            listOf(1, 3),
            gradesForCalculation(
                grades = grades,
                selectedSemesters = emptySet(),
                isCourseSelectionMode = true,
                selectedGradeIds = setOf(1, 2, 3),
                typeByCode = typeByCode,
                excludedTypes = setOf(CourseType.ELECTIVE),
            ).map { it.id },
        )
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
