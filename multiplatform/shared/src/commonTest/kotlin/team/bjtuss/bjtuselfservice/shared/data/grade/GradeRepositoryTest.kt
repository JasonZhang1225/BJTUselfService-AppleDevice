package team.bjtuss.bjtuselfservice.shared.data.grade

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import team.bjtuss.bjtuselfservice.shared.domain.grade.Grade
import team.bjtuss.bjtuselfservice.shared.domain.grade.GradeSelectionRecord
import team.bjtuss.bjtuselfservice.shared.domain.grade.selectionRecordsForGradeIds

class GradeRepositoryTest {
    @Test
    fun refreshReplacesCompleteSnapshotAndRestoresSelectionAfterIdsChange() = runBlocking {
        val original = grade(id = 7, score = "B,79")
        val local = FakeLocal(
            storedGrades = listOf(original),
            storedSelections = selectionRecordsForGradeIds(listOf(original), setOf(7)),
        )
        val repository = DefaultGradeRepository(
            accountScope = "student-a",
            local = local,
            remote = FakeRemote(listOf(grade(id = 0, score = "A,95"))),
        )

        val result = assertIs<GradeRefreshResult.Success>(repository.refresh())

        assertEquals(listOf(100), result.snapshot.grades.map(Grade::id))
        assertEquals(setOf(100), result.snapshot.selectedGradeIds)
        assertEquals("A,95", result.snapshot.grades.single().courseScore)
        assertEquals(listOf("student-a"), local.replacedSnapshotAccounts)
    }

    @Test
    fun remoteFailureKeepsCachedSnapshotUntouched() = runBlocking {
        val cached = grade(id = 42, name = "完整缓存")
        val local = FakeLocal(storedGrades = listOf(cached))
        val repository = DefaultGradeRepository(
            accountScope = "student-a",
            local = local,
            remote = FakeRemote(error = GradeRemoteException(GradeRemoteFailure.NETWORK)),
        )

        val result = assertIs<GradeRefreshResult.Failure>(repository.refresh())

        assertEquals(GradeSyncFailure.NETWORK, result.reason)
        assertEquals(listOf(cached), result.snapshot.grades)
        assertTrue(local.replacedSnapshotAccounts.isEmpty())
    }

    @Test
    fun localSnapshotFailureLeavesPreviousGradesAndSelectionsUntouched() = runBlocking {
        val cached = grade(id = 42, name = "完整缓存")
        val selected = selectionRecordsForGradeIds(listOf(cached), setOf(42))
        val local = FakeLocal(
            storedGrades = listOf(cached),
            storedSelections = selected,
            failSnapshotReplace = true,
        )
        val repository = DefaultGradeRepository(
            accountScope = "student-a",
            local = local,
            remote = FakeRemote(listOf(grade(id = 0, name = "远端新数据"))),
        )

        val result = assertIs<GradeRefreshResult.Failure>(repository.refresh())

        assertEquals(GradeSyncFailure.CACHE, result.reason)
        assertEquals(listOf(cached), result.snapshot.grades)
        assertEquals(setOf(42), result.snapshot.selectedGradeIds)
        assertEquals(listOf(cached), local.storedGrades)
        assertEquals(selected, local.storedSelections)
    }

    @Test
    fun selectionActionsPreserveDormantRecordsAndCanClearScopedOrAll() {
        val first = grade(id = 1, name = "课程A", semester = "2025-2026-1")
        val second = grade(id = 2, name = "课程B", semester = "2025-2026-2")
        val dormant = GradeSelectionRecord(
            courseName = "暂不可见课程",
            courseTeacher = "教师",
            courseYear = "2024-2025-2",
            semester = "2024-2025-2",
            lastKnownScore = "B,79",
            lastKnownCredits = "2.0",
            occurrence = 0,
        )
        val local = FakeLocal(
            storedGrades = listOf(first, second),
            storedSelections = listOf(dormant),
        )
        val repository = DefaultGradeRepository(
            accountScope = "student-a",
            local = local,
            remote = FakeRemote(emptyList()),
        )

        val selected = repository.persistSelected(listOf(first, second), setOf(1, 2))
        assertEquals(setOf(1, 2), selected.selectedGradeIds)
        assertTrue(local.storedSelections.any { it.courseName == "暂不可见课程" })

        val semesterCleared = repository.clearSelectedSemesters(setOf("2025-2026-1"))
        assertEquals(setOf(2), semesterCleared.selectedGradeIds)
        assertTrue(local.storedSelections.any { it.courseName == "暂不可见课程" })

        assertTrue(repository.clearAllSelections().selectedGradeIds.isEmpty())
        assertTrue(local.storedSelections.isEmpty())
    }

    private class FakeRemote(
        private val grades: List<Grade> = emptyList(),
        private val error: Exception? = null,
    ) : GradeRemoteDataSource {
        override suspend fun fetchGrades(): List<Grade> {
            error?.let { throw it }
            return grades
        }
    }

    private class FakeLocal(
        var storedGrades: List<Grade> = emptyList(),
        var storedSelections: List<GradeSelectionRecord> = emptyList(),
        private val failSnapshotReplace: Boolean = false,
    ) : GradeLocalDataSource {
        val replacedSnapshotAccounts = mutableListOf<String>()

        override fun grades(accountScope: String): List<Grade> = storedGrades

        override fun selections(accountScope: String): List<GradeSelectionRecord> = storedSelections

        override fun replaceSnapshot(
            accountScope: String,
            grades: List<Grade>,
            records: List<GradeSelectionRecord>,
        ) {
            if (failSnapshotReplace) error("synthetic snapshot failure")
            replacedSnapshotAccounts += accountScope
            storedGrades = grades.mapIndexed { index, grade -> grade.copy(id = 100 + index) }
            storedSelections = records
        }

        override fun replaceSelections(
            accountScope: String,
            records: List<GradeSelectionRecord>,
        ) {
            storedSelections = records
        }
    }

    private fun grade(
        id: Int,
        score: String = "B,79",
        name: String = "课程A",
        semester: String = "2025-2026-1",
    ) = Grade(
        id = id,
        courseName = name,
        courseTeacher = "教师",
        courseScore = score,
        courseCredits = "2.0",
        courseYear = semester,
        semester = semester,
    )
}
