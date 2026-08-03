package team.bjtuss.bjtuselfservice.shared.data.grade

import kotlinx.coroutines.CancellationException
import team.bjtuss.bjtuselfservice.shared.cache.CacheStore
import team.bjtuss.bjtuselfservice.shared.domain.grade.Grade
import team.bjtuss.bjtuselfservice.shared.domain.grade.GradeSelectionRecord
import team.bjtuss.bjtuselfservice.shared.domain.grade.gradeIdsForSelectionRecords
import team.bjtuss.bjtuselfservice.shared.domain.grade.selectionRecordsExcludingSemesters
import team.bjtuss.bjtuselfservice.shared.domain.grade.selectionRecordsForGradeIdsPreservingUnmatched

data class GradeSnapshot(
    val grades: List<Grade>,
    val selectedGradeIds: Set<Int>,
)

enum class GradeSyncFailure {
    NETWORK,
    SESSION_EXPIRED,
    MALFORMED_RESPONSE,
    CACHE,
}

sealed interface GradeRefreshResult {
    data class Success(val snapshot: GradeSnapshot) : GradeRefreshResult
    data class Failure(
        val snapshot: GradeSnapshot,
        val reason: GradeSyncFailure,
    ) : GradeRefreshResult
}

interface GradeLocalDataSource {
    fun grades(accountScope: String): List<Grade>
    fun selections(accountScope: String): List<GradeSelectionRecord>
    fun replaceSnapshot(
        accountScope: String,
        grades: List<Grade>,
        records: List<GradeSelectionRecord>,
    )
    fun replaceSelections(accountScope: String, records: List<GradeSelectionRecord>)
}

class CacheStoreGradeLocalDataSource(
    private val cacheStore: CacheStore,
) : GradeLocalDataSource {
    override fun grades(accountScope: String): List<Grade> = cacheStore.grades(accountScope)

    override fun selections(accountScope: String): List<GradeSelectionRecord> =
        cacheStore.gradeSelections(accountScope)

    override fun replaceSnapshot(
        accountScope: String,
        grades: List<Grade>,
        records: List<GradeSelectionRecord>,
    ) {
        cacheStore.replaceGradeSnapshot(accountScope, grades, records)
    }

    override fun replaceSelections(accountScope: String, records: List<GradeSelectionRecord>) {
        cacheStore.replaceGradeSelections(accountScope, records)
    }
}

interface GradeRepository {
    fun load(): GradeSnapshot
    suspend fun refresh(): GradeRefreshResult
    fun persistSelected(grades: List<Grade>, selectedGradeIds: Set<Int>): GradeSnapshot
    fun clearSelectedSemesters(semesters: Set<String>): GradeSnapshot
    fun clearAllSelections(): GradeSnapshot
}

class DefaultGradeRepository(
    accountScope: String,
    private val local: GradeLocalDataSource,
    private val remote: GradeRemoteDataSource,
) : GradeRepository {
    private val accountScope = accountScope.trim().also {
        require(it.isNotEmpty()) { "accountScope cannot be blank" }
    }

    override fun load(): GradeSnapshot = snapshot(
        grades = local.grades(accountScope),
        records = local.selections(accountScope),
    )

    override suspend fun refresh(): GradeRefreshResult {
        val fallback = runCatching(::load).getOrElse { GradeSnapshot(emptyList(), emptySet()) }
        val remoteGrades = try {
            remote.fetchGrades()
        } catch (error: CancellationException) {
            throw error
        } catch (error: GradeRemoteException) {
            return GradeRefreshResult.Failure(fallback, error.reason.toSyncFailure())
        } catch (_: Exception) {
            return GradeRefreshResult.Failure(fallback, GradeSyncFailure.NETWORK)
        }

        return try {
            val storedRecords = local.selections(accountScope)
            val temporaryGrades = remoteGrades.mapIndexed { index, grade ->
                grade.copy(id = index + 1)
            }
            val temporarySelectedIds = gradeIdsForSelectionRecords(temporaryGrades, storedRecords)
            val normalizedRecords = selectionRecordsForGradeIdsPreservingUnmatched(
                grades = temporaryGrades,
                storedRecords = storedRecords,
                selectedGradeIds = temporarySelectedIds,
            )
            local.replaceSnapshot(accountScope, remoteGrades, normalizedRecords)
            val persistedGrades = local.grades(accountScope)
            val selectedIds = gradeIdsForSelectionRecords(persistedGrades, normalizedRecords)
            GradeRefreshResult.Success(GradeSnapshot(persistedGrades, selectedIds))
        } catch (_: Exception) {
            GradeRefreshResult.Failure(
                snapshot = runCatching(::load).getOrElse { fallback },
                reason = GradeSyncFailure.CACHE,
            )
        }
    }

    override fun persistSelected(
        grades: List<Grade>,
        selectedGradeIds: Set<Int>,
    ): GradeSnapshot {
        val existing = local.selections(accountScope)
        val validIds = grades.mapTo(mutableSetOf()) { it.id }
        val selected = selectedGradeIds intersect validIds
        val records = selectionRecordsForGradeIdsPreservingUnmatched(
            grades = grades,
            storedRecords = existing,
            selectedGradeIds = selected,
        )
        local.replaceSelections(accountScope, records)
        return GradeSnapshot(grades, selected)
    }

    override fun clearSelectedSemesters(semesters: Set<String>): GradeSnapshot {
        val grades = local.grades(accountScope)
        val records = selectionRecordsExcludingSemesters(
            records = local.selections(accountScope),
            semesters = semesters,
        )
        local.replaceSelections(accountScope, records)
        return snapshot(grades, records)
    }

    override fun clearAllSelections(): GradeSnapshot {
        local.replaceSelections(accountScope, emptyList())
        return GradeSnapshot(local.grades(accountScope), emptySet())
    }

    private fun snapshot(
        grades: List<Grade>,
        records: List<GradeSelectionRecord>,
    ): GradeSnapshot = GradeSnapshot(
        grades = grades,
        selectedGradeIds = gradeIdsForSelectionRecords(grades, records),
    )
}

private fun GradeRemoteFailure.toSyncFailure(): GradeSyncFailure = when (this) {
    GradeRemoteFailure.NETWORK -> GradeSyncFailure.NETWORK
    GradeRemoteFailure.SESSION_EXPIRED -> GradeSyncFailure.SESSION_EXPIRED
    GradeRemoteFailure.MALFORMED_RESPONSE -> GradeSyncFailure.MALFORMED_RESPONSE
}
