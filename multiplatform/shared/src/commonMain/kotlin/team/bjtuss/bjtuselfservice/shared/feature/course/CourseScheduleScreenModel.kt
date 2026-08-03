package team.bjtuss.bjtuselfservice.shared.feature.course

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import team.bjtuss.bjtuselfservice.shared.data.course.CourseScheduleRefreshResult
import team.bjtuss.bjtuselfservice.shared.data.course.CourseScheduleRepository
import team.bjtuss.bjtuselfservice.shared.data.course.CourseScheduleSnapshot
import team.bjtuss.bjtuselfservice.shared.data.course.CourseScheduleSyncFailure
import team.bjtuss.bjtuselfservice.shared.domain.course.Course
import team.bjtuss.bjtuselfservice.shared.domain.course.coursesForWeek
import team.bjtuss.bjtuselfservice.shared.domain.change.DataChangeRecorder
import team.bjtuss.bjtuselfservice.shared.domain.change.recordSafely

enum class CourseScheduleType {
    CURRENT,
    SELECTION,
}

enum class CourseScheduleContentSource {
    CACHE,
    NETWORK,
}

data class CourseScheduleUiState(
    val courses: List<Course> = emptyList(),
    val currentWeek: Int = 0,
    val scheduleType: CourseScheduleType = CourseScheduleType.CURRENT,
    val selectedWeek: Int = 0,
    val selectedDay: Int = 0,
    val selectedCourseId: Int? = null,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val source: CourseScheduleContentSource? = null,
    val failure: CourseScheduleSyncFailure? = null,
    val followCurrentWeek: Boolean = true,
) {
    val scheduleCourses: List<Course>
        get() = courses.filter { course ->
            course.isCurrentSemester == (scheduleType == CourseScheduleType.SELECTION)
        }

    val visibleCourses: List<Course>
        get() = coursesForWeek(scheduleCourses, selectedWeek)

    val selectedCourse: Course?
        get() = courses.firstOrNull { it.id == selectedCourseId }
}

class CourseScheduleScreenModel(
    private val repository: CourseScheduleRepository,
    private val changeRecorder: DataChangeRecorder<Course>? = null,
) {
    private val mutableState = MutableStateFlow(CourseScheduleUiState())
    val state: StateFlow<CourseScheduleUiState> = mutableState.asStateFlow()

    private var initialized = false
    private var refreshInFlight = false

    suspend fun initialize(refreshFromNetwork: Boolean = true) {
        if (initialized) return
        initialized = true
        val cached = runCatching(repository::load).getOrNull()
        if (cached != null) {
            applySnapshot(
                snapshot = cached,
                source = if (cached.courses.isEmpty()) null else CourseScheduleContentSource.CACHE,
                failure = null,
            )
        } else {
            mutableState.value = mutableState.value.copy(
                isLoading = true,
                failure = CourseScheduleSyncFailure.CACHE,
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
            isLoading = before.courses.isEmpty(),
            isRefreshing = before.courses.isNotEmpty(),
            failure = null,
        )
        try {
            when (val result = repository.refresh()) {
                is CourseScheduleRefreshResult.Success -> {
                    changeRecorder.recordSafely(before.courses, result.snapshot.courses)
                    applySnapshot(result.snapshot, CourseScheduleContentSource.NETWORK, null)
                }
                is CourseScheduleRefreshResult.Failure -> applySnapshot(
                    result.snapshot,
                    if (result.snapshot.courses.isEmpty()) null else CourseScheduleContentSource.CACHE,
                    result.reason,
                )
            }
        } finally {
            refreshInFlight = false
        }
    }

    fun selectScheduleType(type: CourseScheduleType) {
        val current = mutableState.value
        if (type == current.scheduleType) return
        val targetWeek = if (type == CourseScheduleType.CURRENT) current.currentWeek else 0
        mutableState.value = current.copy(
            scheduleType = type,
            selectedWeek = targetWeek,
            followCurrentWeek = type == CourseScheduleType.CURRENT && targetWeek == 0,
            selectedCourseId = null,
        )
    }

    fun selectWeek(week: Int) {
        if (week !in 0..26) return
        mutableState.value = mutableState.value.copy(
            selectedWeek = week,
            followCurrentWeek = false,
            selectedCourseId = null,
        )
    }

    fun selectDay(day: Int) {
        if (day !in 0..6) return
        mutableState.value = mutableState.value.copy(selectedDay = day, selectedCourseId = null)
    }

    fun showCourseDetails(courseId: Int) {
        mutableState.value = mutableState.value.copy(selectedCourseId = courseId)
    }

    fun dismissCourseDetails() {
        mutableState.value = mutableState.value.copy(selectedCourseId = null)
    }

    fun dismissFailure() {
        mutableState.value = mutableState.value.copy(failure = null)
    }

    private fun applySnapshot(
        snapshot: CourseScheduleSnapshot,
        source: CourseScheduleContentSource?,
        failure: CourseScheduleSyncFailure?,
    ) {
        val current = mutableState.value
        val shouldApplyCurrentWeek = current.scheduleType == CourseScheduleType.CURRENT &&
            current.followCurrentWeek && snapshot.currentWeek in 1..26
        val selectedWeek = if (shouldApplyCurrentWeek) snapshot.currentWeek else current.selectedWeek
        val visibleIds = snapshot.courses.mapTo(mutableSetOf(), Course::id)
        mutableState.value = current.copy(
            courses = snapshot.courses,
            currentWeek = snapshot.currentWeek,
            selectedWeek = selectedWeek,
            followCurrentWeek = current.followCurrentWeek && !shouldApplyCurrentWeek,
            selectedCourseId = current.selectedCourseId?.takeIf(visibleIds::contains),
            isLoading = false,
            isRefreshing = false,
            source = source,
            failure = failure,
        )
    }
}
