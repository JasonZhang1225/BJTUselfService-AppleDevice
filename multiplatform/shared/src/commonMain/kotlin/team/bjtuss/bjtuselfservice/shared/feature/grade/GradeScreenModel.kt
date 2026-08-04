package team.bjtuss.bjtuselfservice.shared.feature.grade

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import team.bjtuss.bjtuselfservice.shared.data.grade.GradeRefreshResult
import team.bjtuss.bjtuselfservice.shared.data.grade.GradeRepository
import team.bjtuss.bjtuselfservice.shared.data.grade.GradeSyncFailure
import team.bjtuss.bjtuselfservice.shared.domain.change.DataChangeRecorder
import team.bjtuss.bjtuselfservice.shared.domain.change.recordSafely
import team.bjtuss.bjtuselfservice.shared.domain.grade.CourseType
import team.bjtuss.bjtuselfservice.shared.domain.grade.Grade
import team.bjtuss.bjtuselfservice.shared.domain.grade.GradeInfoResult
import team.bjtuss.bjtuselfservice.shared.domain.grade.GradeSortOrder
import team.bjtuss.bjtuselfservice.shared.domain.grade.calculateGradeInfo
import team.bjtuss.bjtuselfservice.shared.domain.grade.courseTypeOfGrade
import team.bjtuss.bjtuselfservice.shared.domain.grade.filterGradesBySemester
import team.bjtuss.bjtuselfservice.shared.domain.grade.gradesForCalculation
import team.bjtuss.bjtuselfservice.shared.domain.grade.sortGrades

enum class GradeContentSource {
    CACHE,
    NETWORK,
}

data class GradeUiState(
    val grades: List<Grade> = emptyList(),
    val selectedGradeIds: Set<Int> = emptySet(),
    val selectedSemesters: Set<String> = emptySet(),
    val courseTypesByCode: Map<String, CourseType> = emptyMap(),
    val sortOrder: GradeSortOrder = GradeSortOrder.ORIGINAL,
    val selectionMode: Boolean = false,
    val selectedGradeId: Int? = null,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val source: GradeContentSource? = null,
    val failure: GradeSyncFailure? = null,
) {
    val semesterOptions: List<String>
        get() = grades.map(Grade::semester).filter(String::isNotBlank).distinct()

    val visibleGrades: List<Grade>
        get() = sortGrades(filterGradesBySemester(grades, selectedSemesters), sortOrder)

    val gradeInfo: GradeInfoResult
        get() = calculateGradeInfo(
            gradesForCalculation(
                grades = grades,
                selectedSemesters = selectedSemesters,
                isCourseSelectionMode = selectionMode,
                selectedGradeIds = selectedGradeIds,
            ),
        )

    val selectedGrade: Grade?
        get() = grades.firstOrNull { it.id == selectedGradeId }

    /** 映射查不到课程号的按未知处理，UI 不显示标签。 */
    fun courseTypeOf(grade: Grade): CourseType = courseTypeOfGrade(grade, courseTypesByCode)

    val courseTypeCounts: Map<CourseType, Int>
        get() = grades.groupingBy { grade -> courseTypeOf(grade) }.eachCount()

    /**
     * 某性质课程在自选模式下的三态：全部选中 / 部分选中 / 未选中。
     * UNKNOWN（其他类别）同样参与，避免“勾了未知课程却没有入口取消”的误导。
     */
    fun selectionStateForType(type: CourseType): CourseTypeSelectionState {
        val ofType = grades.filter { courseTypeOf(it) == type }
        if (ofType.isEmpty()) return CourseTypeSelectionState.NONE
        val selected = ofType.count { it.id in selectedGradeIds }
        return when {
            selected == 0 -> CourseTypeSelectionState.NONE
            selected == ofType.size -> CourseTypeSelectionState.ALL
            else -> CourseTypeSelectionState.PARTIAL
        }
    }

    fun allSelectedForType(type: CourseType): Boolean =
        selectionStateForType(type) == CourseTypeSelectionState.ALL
}

enum class CourseTypeSelectionState {
    ALL,
    PARTIAL,
    NONE,
}

class GradeScreenModel(
    private val repository: GradeRepository,
    private val changeRecorder: DataChangeRecorder<Grade>? = null,
) {
    private val mutableState = MutableStateFlow(GradeUiState())
    val state: StateFlow<GradeUiState> = mutableState.asStateFlow()

    private var initialized = false
    private var refreshInFlight = false

    suspend fun initialize(refreshFromNetwork: Boolean = true) {
        if (initialized) return
        initialized = true
        val cached = runCatching(repository::load).getOrNull()
        if (cached != null) {
            mutableState.value = mutableState.value.copy(
                grades = cached.grades,
                selectedGradeIds = cached.selectedGradeIds,
                courseTypesByCode = cached.courseTypesByCode,
                isLoading = cached.grades.isEmpty(),
                source = if (cached.grades.isEmpty()) null else GradeContentSource.CACHE,
                failure = null,
            )
        } else {
            mutableState.value = mutableState.value.copy(
                isLoading = true,
                failure = GradeSyncFailure.CACHE,
            )
        }
        if (refreshFromNetwork) {
            refresh()
        } else {
            mutableState.value = mutableState.value.copy(isLoading = false, isRefreshing = false)
        }
    }

    suspend fun refresh() {
        if (refreshInFlight) return
        refreshInFlight = true
        val before = mutableState.value
        mutableState.value = before.copy(
            isLoading = before.grades.isEmpty(),
            isRefreshing = before.grades.isNotEmpty(),
            failure = null,
        )
        try {
            when (val result = repository.refresh()) {
                is GradeRefreshResult.Success -> {
                    changeRecorder.recordSafely(before.grades, result.snapshot.grades)
                    applySnapshot(
                        grades = result.snapshot.grades,
                        selectedIds = result.snapshot.selectedGradeIds,
                        courseTypesByCode = result.snapshot.courseTypesByCode,
                        source = GradeContentSource.NETWORK,
                        failure = null,
                    )
                }
                is GradeRefreshResult.Failure -> applySnapshot(
                    grades = result.snapshot.grades,
                    selectedIds = result.snapshot.selectedGradeIds,
                    courseTypesByCode = result.snapshot.courseTypesByCode,
                    source = if (result.snapshot.grades.isEmpty()) null else GradeContentSource.CACHE,
                    failure = result.reason,
                )
            }
        } finally {
            refreshInFlight = false
        }
    }

    fun toggleSemester(semester: String) {
        val current = mutableState.value
        mutableState.value = current.copy(
            selectedSemesters = if (semester in current.selectedSemesters) {
                current.selectedSemesters - semester
            } else {
                current.selectedSemesters + semester
            },
        )
    }

    fun clearSemesterFilter() {
        mutableState.value = mutableState.value.copy(selectedSemesters = emptySet())
    }

    fun cycleSortOrder() {
        val current = mutableState.value
        mutableState.value = current.copy(
            sortOrder = when (current.sortOrder) {
                GradeSortOrder.ORIGINAL -> GradeSortOrder.ASCENDING
                GradeSortOrder.ASCENDING -> GradeSortOrder.DESCENDING
                GradeSortOrder.DESCENDING -> GradeSortOrder.ORIGINAL
            },
        )
    }

    fun toggleSelectionMode() {
        val current = mutableState.value
        mutableState.value = if (current.selectionMode) {
            current.copy(
                selectionMode = false,
                selectedSemesters = emptySet(),
                sortOrder = GradeSortOrder.ORIGINAL,
            )
        } else {
            current.copy(
                selectionMode = true,
                sortOrder = GradeSortOrder.ORIGINAL,
            )
        }
    }

    fun setGradeSelected(gradeId: Int, selected: Boolean) {
        val current = mutableState.value
        val updated = if (selected) {
            current.selectedGradeIds + gradeId
        } else {
            current.selectedGradeIds - gradeId
        }
        persistSelection(current.grades, updated)
    }

    fun selectAllVisible() {
        val current = mutableState.value
        persistSelection(
            grades = current.grades,
            selectedIds = current.selectedGradeIds + current.visibleGrades.map(Grade::id),
        )
    }

    fun selectAllByType(type: CourseType) {
        val current = mutableState.value
        val idsOfType = current.grades
            .filter { current.courseTypeOf(it) == type }
            .map(Grade::id)
        if (idsOfType.isEmpty()) return
        persistSelection(
            grades = current.grades,
            selectedIds = current.selectedGradeIds + idsOfType,
        )
    }

    fun deselectByType(type: CourseType) {
        val current = mutableState.value
        if (current.courseTypeCounts[type] == null) return
        runCatching {
            repository.clearSelectedCourseTypes(setOf(type))
        }.onSuccess { snapshot ->
            mutableState.value = current.copy(
                grades = snapshot.grades,
                selectedGradeIds = snapshot.selectedGradeIds,
                courseTypesByCode = snapshot.courseTypesByCode,
                failure = null,
            )
        }.onFailure {
            mutableState.value = current.copy(failure = GradeSyncFailure.CACHE)
        }
    }

    fun clearSelectedSemesters() {
        val current = mutableState.value
        if (current.selectedSemesters.isEmpty()) return
        runCatching {
            repository.clearSelectedSemesters(current.selectedSemesters)
        }.onSuccess { snapshot ->
            mutableState.value = current.copy(
                grades = snapshot.grades,
                selectedGradeIds = snapshot.selectedGradeIds,
                courseTypesByCode = snapshot.courseTypesByCode,
                failure = null,
            )
        }.onFailure {
            mutableState.value = current.copy(failure = GradeSyncFailure.CACHE)
        }
    }

    fun clearAllSelections() {
        val current = mutableState.value
        runCatching(repository::clearAllSelections).onSuccess { snapshot ->
            mutableState.value = current.copy(
                grades = snapshot.grades,
                selectedGradeIds = snapshot.selectedGradeIds,
                courseTypesByCode = snapshot.courseTypesByCode,
                selectedSemesters = emptySet(),
                sortOrder = GradeSortOrder.ORIGINAL,
                failure = null,
            )
        }.onFailure {
            mutableState.value = current.copy(failure = GradeSyncFailure.CACHE)
        }
    }

    fun showGradeDetails(gradeId: Int) {
        mutableState.value = mutableState.value.copy(selectedGradeId = gradeId)
    }

    fun dismissGradeDetails() {
        mutableState.value = mutableState.value.copy(selectedGradeId = null)
    }

    fun dismissFailure() {
        mutableState.value = mutableState.value.copy(failure = null)
    }

    private fun persistSelection(grades: List<Grade>, selectedIds: Set<Int>) {
        val current = mutableState.value
        runCatching {
            repository.persistSelected(grades, selectedIds)
        }.onSuccess { snapshot ->
            mutableState.value = current.copy(
                grades = snapshot.grades,
                selectedGradeIds = snapshot.selectedGradeIds,
                courseTypesByCode = snapshot.courseTypesByCode,
                failure = null,
            )
        }.onFailure {
            mutableState.value = current.copy(failure = GradeSyncFailure.CACHE)
        }
    }

    private fun applySnapshot(
        grades: List<Grade>,
        selectedIds: Set<Int>,
        courseTypesByCode: Map<String, CourseType>,
        source: GradeContentSource?,
        failure: GradeSyncFailure?,
    ) {
        val current = mutableState.value
        val semesters = grades.map(Grade::semester).toSet()
        mutableState.value = current.copy(
            grades = grades,
            selectedGradeIds = selectedIds,
            courseTypesByCode = courseTypesByCode,
            selectedSemesters = current.selectedSemesters intersect semesters,
            selectedGradeId = current.selectedGradeId?.takeIf { id -> grades.any { it.id == id } },
            isLoading = false,
            isRefreshing = false,
            source = source,
            failure = failure,
        )
    }
}
