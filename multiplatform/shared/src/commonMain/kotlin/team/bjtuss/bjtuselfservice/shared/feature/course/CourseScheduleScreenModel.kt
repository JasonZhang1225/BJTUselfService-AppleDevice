package team.bjtuss.bjtuselfservice.shared.feature.course

import kotlinx.coroutines.delay
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

/** 登录后自动同步：首轮 + 失败后再试 2 次（瞬时网络抖动常见）。 */
internal const val AUTO_SYNC_MAX_ATTEMPTS = 3
internal const val AUTO_SYNC_RETRY_DELAY_MILLIS = 700L

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

    /** 本地缓存是否已灌入 UI。可在登录完成前执行。 */
    private var cacheLoaded = false
    /**
     * 网络自动同步是否已启动。必须在登录成功后由 shell 以 refreshFromNetwork=true 触发，
     * 避免静默登录未完成时白白失败多次。
     */
    private var networkAutoSyncStarted = false
    private var refreshInFlight = false

    /**
     * @param refreshFromNetwork false：只读缓存（页面打开即可）；true：登录成功后的自动同步（可重试）。
     * 两阶段可分别调用，且顺序无关：先缓存后网络，或直接网络（内部仍会先灌缓存）。
     */
    suspend fun initialize(refreshFromNetwork: Boolean = true) {
        if (!cacheLoaded) {
            cacheLoaded = true
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
            if (!refreshFromNetwork) {
                mutableState.value = mutableState.value.copy(isLoading = false, isRefreshing = false)
            }
        }
        if (refreshFromNetwork && !networkAutoSyncStarted) {
            networkAutoSyncStarted = true
            // 登录后自动同步：失败再试，避免首包抖动就弹「同步失败」。
            refreshWithRetry(maxAttempts = AUTO_SYNC_MAX_ATTEMPTS)
        }
    }

    /**
     * 手动刷新：单次请求。自动同步请用 [refreshWithRetry] 或 [initialize]。
     */
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
            // 协程取消时 applySnapshot 不会执行，须清掉 isRefreshing，否则首页 OR 聚合会一直「同步中」。
            val current = mutableState.value
            if (current.isRefreshing || current.isLoading) {
                mutableState.value = current.copy(isRefreshing = false, isLoading = false)
            }
        }
    }

    /**
     * 连续刷新最多 [maxAttempts] 次；任一次成功即停。
     * 用于登录后自动同步：中间失败不长期停留，最后一次失败才保留 failure 横幅。
     */
    suspend fun refreshWithRetry(
        maxAttempts: Int = AUTO_SYNC_MAX_ATTEMPTS,
        delayMillis: Long = AUTO_SYNC_RETRY_DELAY_MILLIS,
    ) {
        require(maxAttempts >= 1)
        repeat(maxAttempts) { index ->
            refresh()
            if (mutableState.value.failure == null) return
            if (index < maxAttempts - 1) delay(delayMillis)
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
