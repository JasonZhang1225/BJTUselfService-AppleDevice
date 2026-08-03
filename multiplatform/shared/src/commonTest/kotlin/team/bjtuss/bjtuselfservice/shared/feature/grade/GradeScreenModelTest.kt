package team.bjtuss.bjtuselfservice.shared.feature.grade

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import team.bjtuss.bjtuselfservice.shared.data.grade.GradeRefreshResult
import team.bjtuss.bjtuselfservice.shared.data.grade.GradeRepository
import team.bjtuss.bjtuselfservice.shared.data.grade.GradeSnapshot
import team.bjtuss.bjtuselfservice.shared.data.grade.GradeSyncFailure
import team.bjtuss.bjtuselfservice.shared.domain.grade.Grade
import team.bjtuss.bjtuselfservice.shared.domain.grade.GradeInfoResult
import team.bjtuss.bjtuselfservice.shared.domain.grade.GradeSortOrder
import team.bjtuss.bjtuselfservice.shared.domain.change.DataChangeRecorder

class GradeScreenModelTest {
    @Test
    fun initializationShowsCacheThenAppliesNetworkSnapshot() = runBlocking {
        val repository = FakeRepository(
            loaded = GradeSnapshot(listOf(grade(1, score = "B,79")), setOf(1)),
            refreshed = GradeRefreshResult.Success(
                GradeSnapshot(listOf(grade(101, score = "A,95")), setOf(101)),
            ),
        )
        val model = GradeScreenModel(repository)

        model.initialize()

        assertEquals(GradeContentSource.NETWORK, model.state.value.source)
        assertEquals(listOf(101), model.state.value.grades.map(Grade::id))
        assertEquals(setOf(101), model.state.value.selectedGradeIds)
        assertFalse(model.state.value.isLoading)
    }

    @Test
    fun refreshFailureKeepsCacheAndExposesRetryState() = runBlocking {
        val cached = GradeSnapshot(listOf(grade(1)), emptySet())
        val model = GradeScreenModel(
            FakeRepository(
                loaded = cached,
                refreshed = GradeRefreshResult.Failure(cached, GradeSyncFailure.NETWORK),
            ),
        )

        model.initialize()

        assertEquals(GradeContentSource.CACHE, model.state.value.source)
        assertEquals(GradeSyncFailure.NETWORK, model.state.value.failure)
        assertEquals(1, model.state.value.grades.size)
    }

    @Test
    fun filteringSortingAndSelectionCalculationStayIndependent() = runBlocking {
        val first = grade(1, semester = "2025-2026-1", score = "A,95")
        val second = grade(2, semester = "2025-2026-2", score = "C,69")
        val snapshot = GradeSnapshot(listOf(first, second), emptySet())
        val repository = FakeRepository(
            loaded = snapshot,
            refreshed = GradeRefreshResult.Success(snapshot),
        )
        val model = GradeScreenModel(repository)
        model.initialize()

        model.toggleSemester("2025-2026-1")
        model.cycleSortOrder()
        assertEquals(GradeSortOrder.ASCENDING, model.state.value.sortOrder)
        assertEquals(listOf(1), model.state.value.visibleGrades.map(Grade::id))

        model.toggleSelectionMode()
        model.setGradeSelected(2, true)
        val calculated = assertIs<GradeInfoResult.Calculated>(model.state.value.gradeInfo)
        assertEquals(69.0, calculated.averageScore)
        assertEquals(setOf(2), model.state.value.selectedGradeIds)

        model.toggleSelectionMode()
        assertFalse(model.state.value.selectionMode)
        assertTrue(model.state.value.selectedSemesters.isEmpty())
        assertEquals(GradeSortOrder.ORIGINAL, model.state.value.sortOrder)
    }

    @Test
    fun successfulRefreshRecordsBeforeAndAfterSnapshots() = runBlocking {
        val old = grade(1)
        val updated = old.copy(id = 101, courseScore = "A,95")
        var captured: Pair<List<Grade>, List<Grade>>? = null
        val model = GradeScreenModel(
            repository = FakeRepository(
                loaded = GradeSnapshot(listOf(old), emptySet()),
                refreshed = GradeRefreshResult.Success(GradeSnapshot(listOf(updated), emptySet())),
            ),
            changeRecorder = DataChangeRecorder { before, after -> captured = before to after },
        )

        model.initialize()

        assertEquals(listOf(old), captured?.first)
        assertEquals(listOf(updated), captured?.second)
    }

    private class FakeRepository(
        private val loaded: GradeSnapshot,
        private val refreshed: GradeRefreshResult,
    ) : GradeRepository {
        private var snapshot = loaded

        override fun load(): GradeSnapshot = loaded

        override suspend fun refresh(): GradeRefreshResult = refreshed.also { result ->
            snapshot = when (result) {
                is GradeRefreshResult.Success -> result.snapshot
                is GradeRefreshResult.Failure -> result.snapshot
            }
        }

        override fun persistSelected(
            grades: List<Grade>,
            selectedGradeIds: Set<Int>,
        ): GradeSnapshot = GradeSnapshot(grades, selectedGradeIds).also { snapshot = it }

        override fun clearSelectedSemesters(semesters: Set<String>): GradeSnapshot {
            val ids = snapshot.grades
                .filterNot { it.semester in semesters }
                .mapTo(mutableSetOf(), Grade::id)
            return snapshot.copy(selectedGradeIds = snapshot.selectedGradeIds intersect ids)
                .also { snapshot = it }
        }

        override fun clearAllSelections(): GradeSnapshot = snapshot.copy(selectedGradeIds = emptySet())
            .also { snapshot = it }
    }

    private fun grade(
        id: Int,
        semester: String = "2025-2026-1",
        score: String = "B,79",
    ) = Grade(
        id = id,
        courseName = "课程$id",
        courseTeacher = "教师",
        courseScore = score,
        courseCredits = "2.0",
        courseYear = semester,
        semester = semester,
    )
}
