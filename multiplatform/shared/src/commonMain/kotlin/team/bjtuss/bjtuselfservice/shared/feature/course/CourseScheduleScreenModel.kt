package team.bjtuss.bjtuselfservice.shared.feature.course

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import team.bjtuss.bjtuselfservice.shared.data.course.CourseScheduleRefreshResult
import team.bjtuss.bjtuselfservice.shared.data.course.CourseScheduleRepository
import team.bjtuss.bjtuselfservice.shared.data.course.CourseScheduleSnapshot
import team.bjtuss.bjtuselfservice.shared.data.course.CourseScheduleSyncFailure
import team.bjtuss.bjtuselfservice.shared.data.classroomoccupancy.ClassroomOccupancyRepository
import team.bjtuss.bjtuselfservice.shared.domain.classroomoccupancy.OccupancyWeekDate
import team.bjtuss.bjtuselfservice.shared.domain.course.Course
import team.bjtuss.bjtuselfservice.shared.domain.course.coursesForWeek
import team.bjtuss.bjtuselfservice.shared.domain.change.DataChangeRecorder
import team.bjtuss.bjtuselfservice.shared.domain.change.recordSafely

/** 登录后自动同步：首轮 + 失败后再试 2 次（瞬时网络抖动常见）。 */
internal const val AUTO_SYNC_MAX_ATTEMPTS = 3
internal const val AUTO_SYNC_RETRY_DELAY_MILLIS = 700L
const val COURSE_MAX_WEEK = 30
internal const val COURSE_OVERVIEW_PAGE_COUNT = COURSE_MAX_WEEK + 1

/** 概览 page 0 固定对应“全部教学周”，page 1..30 与教学周编号相同。 */
internal fun overviewPageForWeek(week: Int): Int {
    require(week in 0..COURSE_MAX_WEEK)
    return week
}

internal fun weekForOverviewPage(page: Int): Int {
    require(page in 0 until COURSE_OVERVIEW_PAGE_COUNT)
    return page
}

internal data class CourseScheduleCalendarMapping(
    val semesterLabel: String,
    val weeks: List<OccupancyWeekDate>,
)

/**
 * 返回校历中覆盖 [date] 的教学周。校历是按学期编号的，不能拿全局接口返回的
 * “第 1 周”直接覆盖一个已经处于续编周的当前学期。
 */
internal fun calendarWeekForDate(
    mapping: CourseScheduleCalendarMapping?,
    date: LocalDate,
): Int? = mapping?.weeks?.firstOrNull { week ->
    val start = week.startDate ?: return@firstOrNull false
    date >= start && date <= start.plus(6, DateTimeUnit.DAY)
}?.week?.takeIf { it in 1..COURSE_MAX_WEEK }

/**
 * 本学期课表使用教务当前学期；选课课表使用紧随其后的学期。
 * 若服务器已经把“当前学期”预切到尚未开学的学期，选课课表就使用该学期本身。
 * 找不到精确下一学期时返回空映射，宁可禁用导出，也不能复用旧学期日期。
 */
internal fun resolveCourseScheduleCalendarMappings(
    selectedSemesterLabel: String?,
    weekDates: Map<String, List<OccupancyWeekDate>>,
    today: LocalDate,
): Map<CourseScheduleType, CourseScheduleCalendarMapping> {
    val available = weekDates.mapNotNull { (label, weeks) ->
        val dated = weeks.filter { it.startDate != null }.sortedBy { it.startDate }
        if (dated.isEmpty()) null else CourseScheduleCalendarMapping(label, dated)
    }.associateBy(CourseScheduleCalendarMapping::semesterLabel)
    if (available.isEmpty()) return emptyMap()
    val current = selectedSemesterLabel?.let(available::get)
        ?: available.values.firstOrNull { mapping ->
            mapping.weeks.any { week ->
                val start = week.startDate ?: return@any false
                today >= start && today <= start.plus(6, DateTimeUnit.DAY)
            }
        }
        ?: available.values
            .filter { mapping -> mapping.weeks.firstNotNullOf(OccupancyWeekDate::startDate) <= today }
            .maxByOrNull { mapping -> mapping.weeks.firstNotNullOf(OccupancyWeekDate::startDate) }
        ?: available.values.minBy { mapping -> mapping.weeks.firstNotNullOf(OccupancyWeekDate::startDate) }

    val currentStart = current.weeks.firstNotNullOf(OccupancyWeekDate::startDate)
    val selection = if (currentStart > today) {
        // 教务在开学前已把下拉切到新学期时，选课课表就是这个尚未开始的学期。
        current
    } else {
        nextSemesterLabel(current.semesterLabel)?.let(available::get)
    }
    return buildMap {
        put(CourseScheduleType.CURRENT, current)
        if (selection != null) put(CourseScheduleType.SELECTION, selection)
    }
}

private fun nextSemesterLabel(label: String): String? {
    val match = Regex("^(\\d{4})-(\\d{4})-([12])$").matchEntire(label) ?: return null
    val firstYear = match.groupValues[1].toIntOrNull() ?: return null
    val secondYear = match.groupValues[2].toIntOrNull() ?: return null
    return when (match.groupValues[3]) {
        "1" -> "$firstYear-$secondYear-2"
        "2" -> "$secondYear-${secondYear + 1}-1"
        else -> null
    }
}

enum class CourseScheduleType {
    CURRENT,
    SELECTION,
}

enum class CourseScheduleContentSource {
    CACHE,
    NETWORK,
}

enum class CourseCompactViewMode {
    DAY,
    WEEK,
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
    val compactViewMode: CourseCompactViewMode = CourseCompactViewMode.WEEK,
    val calendarSemesterLabel: String? = null,
    val academicWeeks: List<OccupancyWeekDate> = emptyList(),
    val isCalendarLoading: Boolean = false,
    val todayDate: LocalDate? = null,
    val selectedDate: LocalDate? = null,
    val dateOutsideTeachingWeeks: Boolean = false,
) {
    val scheduleCourses: List<Course>
        get() = courses.filter { course ->
            course.isCurrentSemester == (scheduleType == CourseScheduleType.SELECTION)
        }

    val visibleCourses: List<Course>
        get() = if (dateOutsideTeachingWeeks) emptyList() else coursesForWeek(scheduleCourses, selectedWeek)

    val selectedCourse: Course?
        get() = courses.firstOrNull { it.id == selectedCourseId }

    fun weekDate(week: Int = selectedWeek): OccupancyWeekDate? =
        academicWeeks.firstOrNull { it.week == week }

    fun dateFor(week: Int, dayIndex: Int): LocalDate? =
        weekDate(week)?.startDate?.plus(dayIndex, DateTimeUnit.DAY)

    /**
     * 副标题中的“当前周”提示：
     * - 本学期课表直接使用教务快照 currentWeek；
     * - 选课课表必须按自己的校历判断。学期未开始显示“学期尚未开始”，
     *   已开始则按校历计算当前周；快照 currentWeek 属于本学期，不能用于选课课表。
     */
    fun semesterStatusSubtitle(): String? {
        if (scheduleType == CourseScheduleType.CURRENT) {
            return if (currentWeek > 0) "当前第 $currentWeek 周" else null
        }
        val today = todayDate ?: return null
        val firstStart = academicWeeks.mapNotNull(OccupancyWeekDate::startDate).minOrNull()
            ?: return null
        if (today < firstStart) return "学期尚未开始"
        val currentSelectionWeek = academicWeeks.firstOrNull { week ->
            val start = week.startDate ?: return@firstOrNull false
            today >= start && today <= start.plus(6, DateTimeUnit.DAY)
        }?.week
        return currentSelectionWeek?.let { "当前第 $it 周" }
    }
}

class CourseScheduleScreenModel(
    private val repository: CourseScheduleRepository,
    private val changeRecorder: DataChangeRecorder<Course>? = null,
    private val calendarRepository: ClassroomOccupancyRepository? = null,
    private val todayProvider: () -> LocalDate = {
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    },
) {
    private val mutableState = MutableStateFlow(CourseScheduleUiState(todayDate = todayProvider()))
    val state: StateFlow<CourseScheduleUiState> = mutableState.asStateFlow()

    /** 本地缓存是否已灌入 UI。可在登录完成前执行。 */
    private var cacheLoaded = false
    /**
     * 网络自动同步是否已启动。必须在登录成功后由 shell 以 refreshFromNetwork=true 触发，
     * 避免静默登录未完成时白白失败多次。
     */
    private var networkAutoSyncStarted = false
    private var refreshInFlight = false
    private val calendarMutex = Mutex()
    private var calendarLoaded = false
    private var calendarMappings: Map<CourseScheduleType, CourseScheduleCalendarMapping> = emptyMap()

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
        val calendar = calendarMappings[type]
        mutableState.value = current.copy(
            scheduleType = type,
            selectedWeek = targetWeek,
            // 进入本学期课表即恢复跟随；只有后续明确选周/选日期才冻结。
            followCurrentWeek = type == CourseScheduleType.CURRENT,
            selectedCourseId = null,
            calendarSemesterLabel = calendar?.semesterLabel,
            academicWeeks = calendar?.weeks.orEmpty(),
            selectedDate = calendar?.weeks
                ?.firstOrNull { it.week == targetWeek }
                ?.startDate
                ?.plus(current.selectedDay, DateTimeUnit.DAY),
            dateOutsideTeachingWeeks = false,
        )
    }

    fun selectWeek(week: Int) {
        if (week !in 0..COURSE_MAX_WEEK) return
        val current = mutableState.value
        mutableState.value = current.copy(
            selectedWeek = week,
            followCurrentWeek = false,
            selectedCourseId = null,
            selectedDate = current.dateFor(week, current.selectedDay),
            dateOutsideTeachingWeeks = false,
        )
    }

    /** 桌面按钮/触摸板必须基于最新状态原子递进，不能使用 Modifier 首次组合时捕获的旧周数。 */
    fun moveWeekBy(offset: Int) {
        if (offset !in setOf(-1, 1)) return
        val target = (mutableState.value.selectedWeek + offset).coerceIn(0, COURSE_MAX_WEEK)
        selectWeek(target)
    }

    fun selectDay(day: Int) {
        if (day !in 0..6) return
        val current = mutableState.value
        mutableState.value = current.copy(
            selectedDay = day,
            selectedCourseId = null,
            selectedDate = current.dateFor(current.selectedWeek, day),
            dateOutsideTeachingWeeks = false,
        )
    }

    fun selectCompactViewMode(mode: CourseCompactViewMode) {
        mutableState.value = mutableState.value.copy(compactViewMode = mode, selectedCourseId = null)
    }

    /**
     * 日期选择会在本学期与选课学期两套校历中查找；命中另一学期时原子切换课表类型、
     * 学期和周次，防止先切类型再切周造成中间帧错位，也防止后到同步覆盖用户选择。
     */
    fun selectDate(date: LocalDate) {
        val current = mutableState.value
        val destination = sequenceOf(
            current.scheduleType,
            CourseScheduleType.entries.first { it != current.scheduleType },
        ).mapNotNull { type ->
            val mapping = calendarMappings[type] ?: return@mapNotNull null
            val week = mapping.weeks.firstOrNull { item ->
                val start = item.startDate ?: return@firstOrNull false
                date >= start && date <= start.plus(6, DateTimeUnit.DAY)
            } ?: return@mapNotNull null
            Triple(type, mapping, week)
        }.firstOrNull()
        mutableState.value = if (destination == null) {
            current.copy(
                selectedWeek = 0,
                selectedDay = date.dayOfWeek.isoDayNumber - 1,
                selectedDate = date,
                dateOutsideTeachingWeeks = true,
                followCurrentWeek = false,
                selectedCourseId = null,
            )
        } else {
            val (scheduleType, mapping, week) = destination
            current.copy(
                scheduleType = scheduleType,
                selectedWeek = week.week,
                selectedDay = date.dayOfWeek.isoDayNumber - 1,
                selectedDate = date,
                dateOutsideTeachingWeeks = false,
                followCurrentWeek = false,
                selectedCourseId = null,
                calendarSemesterLabel = mapping.semesterLabel,
                academicWeeks = mapping.weeks,
            )
        }
    }

    /** 登录完成后加载一次 M11 校历映射；失败只关闭加载态，不阻断已有课表。 */
    suspend fun ensureCalendarLoaded() {
        val calendarRepository = calendarRepository ?: return
        if (calendarLoaded) return
        calendarMutex.withLock {
            if (calendarLoaded) return
            mutableState.value = mutableState.value.copy(isCalendarLoading = true)
            try {
                val semesters = calendarRepository.fetchSemesters()
                val weekDates = calendarRepository.fetchWeekDates()
                val today = todayProvider()
                val current = mutableState.value
                calendarMappings = resolveCourseScheduleCalendarMappings(
                    selectedSemesterLabel = semesters.selected?.label,
                    weekDates = weekDates,
                    today = today,
                )
                val selectedCalendar = calendarMappings[current.scheduleType]
                val weeks = selectedCalendar?.weeks.orEmpty()
                // 1.7.3B 的 getTimeList/room_view 仍是首选远端来源；但在学期切换边界，
                // 两个接口可能都返回下一学期的第 1 周。当前学期校历带有真实日期，
                // 若今天落在其中，以它校正当前周，避免首页和课表回到第 1 周。
                val calendarCurrentWeek = calendarWeekForDate(
                    mapping = calendarMappings[CourseScheduleType.CURRENT],
                    date = today,
                )
                val followCalendarCurrentWeek = current.scheduleType == CourseScheduleType.CURRENT &&
                    current.followCurrentWeek && calendarCurrentWeek != null
                val effectiveCurrentWeek = calendarCurrentWeek ?: current.currentWeek
                val effectiveSelectedWeek = if (followCalendarCurrentWeek) {
                    calendarCurrentWeek
                } else {
                    current.selectedWeek
                }
                if (calendarCurrentWeek != null && calendarCurrentWeek != current.currentWeek) {
                    repository.reconcileCurrentWeek(calendarCurrentWeek)
                }
                val selectedDate = current.selectedDate
                    ?.takeIf { date -> weeks.any { week ->
                        val start = week.startDate ?: return@any false
                        date >= start && date <= start.plus(6, DateTimeUnit.DAY)
                    } }
                    ?: weeks.firstOrNull { it.week == effectiveSelectedWeek }?.startDate
                        ?.plus(current.selectedDay, DateTimeUnit.DAY)
                mutableState.value = current.copy(
                    calendarSemesterLabel = selectedCalendar?.semesterLabel,
                    academicWeeks = weeks,
                    currentWeek = effectiveCurrentWeek,
                    selectedWeek = effectiveSelectedWeek,
                    isCalendarLoading = false,
                    todayDate = today,
                    selectedDate = selectedDate,
                )
                calendarLoaded = calendarMappings.isNotEmpty()
            } catch (error: kotlinx.coroutines.CancellationException) {
                mutableState.value = mutableState.value.copy(isCalendarLoading = false)
                throw error
            } catch (_: Exception) {
                mutableState.value = mutableState.value.copy(isCalendarLoading = false)
            }
        }
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
        val today = todayProvider()
        val calendarCurrentWeek = calendarWeekForDate(
            mapping = calendarMappings[CourseScheduleType.CURRENT],
            date = today,
        )
        val effectiveCurrentWeek = calendarCurrentWeek ?: snapshot.currentWeek
        val shouldApplyCurrentWeek = current.scheduleType == CourseScheduleType.CURRENT &&
            current.followCurrentWeek && effectiveCurrentWeek in 1..COURSE_MAX_WEEK
        val selectedWeek = if (shouldApplyCurrentWeek) effectiveCurrentWeek else current.selectedWeek
        if (calendarCurrentWeek != null && calendarCurrentWeek != snapshot.currentWeek) {
            repository.reconcileCurrentWeek(calendarCurrentWeek)
        }
        val visibleIds = snapshot.courses.mapTo(mutableSetOf(), Course::id)
        mutableState.value = current.copy(
            courses = snapshot.courses,
            currentWeek = effectiveCurrentWeek,
            selectedWeek = selectedWeek,
            // 缓存/网络快照都属于自动结果，不能把“跟随当前周”误关掉；
            // selectWeek/selectDate 才代表用户明确选择并会把它置 false。
            followCurrentWeek = current.followCurrentWeek,
            selectedCourseId = current.selectedCourseId?.takeIf(visibleIds::contains),
            isLoading = false,
            isRefreshing = false,
            source = source,
            failure = failure,
            todayDate = today,
            selectedDate = if (shouldApplyCurrentWeek) {
                current.dateFor(effectiveCurrentWeek, current.selectedDay)
            } else {
                current.selectedDate
            },
        )
    }
}
