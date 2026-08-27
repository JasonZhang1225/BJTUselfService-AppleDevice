package team.bjtuss.bjtuselfservice.shared.feature.phyvlab

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import team.bjtuss.bjtuselfservice.shared.data.phyvlab.PhyVlabActivitiesResult
import team.bjtuss.bjtuselfservice.shared.data.phyvlab.PhyVlabAssignmentDetailResult
import team.bjtuss.bjtuselfservice.shared.data.phyvlab.PhyVlabRemoteFailure
import team.bjtuss.bjtuselfservice.shared.data.phyvlab.PhyVlabCoursesResult
import team.bjtuss.bjtuselfservice.shared.data.phyvlab.PhyVlabEventsResult
import team.bjtuss.bjtuselfservice.shared.data.phyvlab.PhyVlabRepository
import team.bjtuss.bjtuselfservice.shared.data.phyvlab.PhyVlabSessionProtocol
import team.bjtuss.bjtuselfservice.shared.data.phyvlab.PhyVlabSessionResult
import team.bjtuss.bjtuselfservice.shared.data.phyvlab.PhyVlabSubmissionResult
import team.bjtuss.bjtuselfservice.shared.data.phyvlab.PhyVlabSyncFailure
import team.bjtuss.bjtuselfservice.shared.data.phyvlab.phyVlabDebug
import team.bjtuss.bjtuselfservice.shared.domain.phyvlab.PhyVlabActivity
import team.bjtuss.bjtuselfservice.shared.domain.phyvlab.PhyVlabAssignmentDetail
import team.bjtuss.bjtuselfservice.shared.domain.phyvlab.PhyVlabCourse
import team.bjtuss.bjtuselfservice.shared.domain.phyvlab.PhyVlabEvent
import team.bjtuss.bjtuselfservice.shared.domain.homework.HomeworkFileContent
import kotlin.time.Clock

/** 物理在线是学校平台，页面日期和 Moodle 日历均以北京时间为准。 */
private val PHYVLAB_TIME_ZONE = TimeZone.of("Asia/Shanghai")

data class PhyVlabUiState(
    val courses: List<PhyVlabCourse> = emptyList(),
    val events: List<PhyVlabEvent> = emptyList(),
    val monthLabel: String = "",
    val activities: List<PhyVlabActivity> = emptyList(),
    val selectedCourse: PhyVlabCourse? = null,
    val selectedActivity: PhyVlabActivity? = null,
    val assignmentDetail: PhyVlabAssignmentDetail? = null,
    val isDetailLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    val detailFailure: PhyVlabSyncFailure? = null,
    val submissionFeedback: String? = null,
    val isLoading: Boolean = false,
    val failure: PhyVlabSyncFailure? = null,
    val failureDetail: String? = null,
    val casLoginRequired: Boolean = false,
) {
    val hasLoaded: Boolean
        get() = failure != null || courses.isNotEmpty() || selectedCourse != null || casLoginRequired
}

class PhyVlabScreenModel(
    private val repository: PhyVlabRepository,
    private val sessionProtocol: PhyVlabSessionProtocol,
    /**
     * 可选的 App 内 CAS 恢复入口。物理在线的 MoodleSession 过期时，
     * 不把用户直接送去系统浏览器；恢复失败才显示重新登录提示。
     */
    private val reauthenticate: (suspend () -> Boolean)? = null,
) {
    private val mutableState = MutableStateFlow(PhyVlabUiState())
    val state: StateFlow<PhyVlabUiState> = mutableState.asStateFlow()
    private val refreshMutex = Mutex()
    private var lastRequestedMonthSeconds: Long? = null
    private var networkAutoSyncStarted = false
    private var sessionReady = false
    private val activitiesByCourse = mutableMapOf<Int, List<PhyVlabActivity>>()
    private var deadlineEvents: List<PhyVlabEvent> = emptyList()

    /**
     * 登录完成后的自动同步与用户进入物理在线页共用同一个请求入口。
     * 关闭自动同步时不预取网络；用户打开物理在线页仍会按需刷新。
     */
    suspend fun initialize(refreshFromNetwork: Boolean = true) {
        if (refreshFromNetwork && !networkAutoSyncStarted) {
            networkAutoSyncStarted = true
            refresh()
        }
    }

    suspend fun refresh() {
        if (!refreshMutex.tryLock()) return
        try {
            phyVlabDebug("refresh start")
            sessionReady = false
            mutableState.value = mutableState.value.copy(isLoading = true, failure = null, casLoginRequired = false)
            when (val session = establishSessionWithRecovery()) {
                is PhyVlabSessionResult.Failed -> {
                    phyVlabDebug("session failed reason=${session.reason} detail=${session.detail ?: "none"}")
                    if (session.reason == PhyVlabRemoteFailure.SESSION_EXPIRED) {
                        // 即使恢复入口不可用/失败，也进入统一的“重新建立认证”状态，
                        // 不退回“没有课程→打开浏览器”的旧兜底路径。
                        mutableState.value = mutableState.value.copy(casLoginRequired = true)
                    }
                    publishFailure(session.reason.toUiFailure(), session.detail)
                    return
                }
                PhyVlabSessionResult.CasLoginRequired -> {
                    phyVlabDebug("session requires CAS login")
                    mutableState.value = mutableState.value.copy(casLoginRequired = true)
                    return
                }
                is PhyVlabSessionResult.Ready -> {
                    sessionReady = true
                    phyVlabDebug("session ready")
                }
            }
            // 不使用设备系统时区：在海外设备/模拟器上，学校平台的“本月”不能跨日漂移。
            val now = Clock.System.now().toLocalDateTime(PHYVLAB_TIME_ZONE)
            val monthStart = beijingMonthStartSeconds(year = now.year, month = now.month.ordinal + 1)
            val courses = when (val result = repository.fetchCourses()) {
                is PhyVlabCoursesResult.Success -> result.courses
                is PhyVlabCoursesResult.Failure -> {
                    publishFailure(result.reason)
                    return
                }
            }
            activitiesByCourse.clear()
            deadlineEvents = emptyList()
            mutableState.value = PhyVlabUiState(
                courses = courses,
                monthLabel = monthLabelFor(monthStart),
                selectedCourse = courses.firstOrNull(),
                isLoading = true,
            )
            lastRequestedMonthSeconds = monthStart

            // 课程页是服务端渲染的，作业的“到期日”比日历月视图更稳定；逐门课程读取后，
            // 立即把已完成的课程和截止安排推到 UI，不让一个慢页面把整个物理在线页卡成空白。
            courses.forEach { course ->
                when (val result = repository.fetchCourseActivities(course)) {
                    is PhyVlabActivitiesResult.Success -> {
                        activitiesByCourse[course.id] = result.activities
                        deadlineEvents = (deadlineEvents + result.activities.mapNotNull { it.toDeadlineEvent() })
                            .distinctBy(PhyVlabEvent::id)
                        val selectedActivities = activitiesByCourse[mutableState.value.selectedCourse?.id].orEmpty()
                        mutableState.value = mutableState.value.copy(
                            activities = selectedActivities,
                            events = eventsForMonth(deadlineEvents, monthStart),
                        )
                        phyVlabDebug(
                            "course activities courseId=${course.id} count=${result.activities.size} " +
                                "deadlines=${result.activities.count { it.dueTimestamp != null }}",
                        )
                    }
                    is PhyVlabActivitiesResult.Failure -> {
                        phyVlabDebug("course activities failed courseId=${course.id} reason=${result.reason}")
                    }
                }
            }
            phyVlabDebug(
                "refresh data courses=${courses.size} activities=${activitiesByCourse.values.sumOf { it.size }} " +
                    "events=${mutableState.value.events.size}",
            )
        } catch (error: CancellationException) {
            clearLoading()
            throw error
        } catch (_: Exception) {
            publishFailure(PhyVlabSyncFailure.NETWORK)
        } finally {
            clearLoading()
            refreshMutex.unlock()
        }
    }

    fun selectCourse(course: PhyVlabCourse) {
        mutableState.value = mutableState.value.copy(
            selectedCourse = course,
            activities = activitiesByCourse[course.id].orEmpty(),
            failure = null,
        )
    }

    fun showActivityDetails(activity: PhyVlabActivity) {
        mutableState.value = mutableState.value.copy(
            selectedActivity = activity,
            assignmentDetail = null,
            isDetailLoading = false,
            detailFailure = null,
            submissionFeedback = null,
        )
    }

    fun dismissActivityDetails() {
        mutableState.value = mutableState.value.copy(
            selectedActivity = null,
            assignmentDetail = null,
            isDetailLoading = false,
            isSubmitting = false,
            detailFailure = null,
            submissionFeedback = null,
        )
    }

    suspend fun loadSelectedActivityDetail(force: Boolean = false) {
        val activity = mutableState.value.selectedActivity ?: return
        if (!force && (mutableState.value.isDetailLoading || mutableState.value.assignmentDetail != null)) return
        mutableState.value = mutableState.value.copy(
            isDetailLoading = true,
            detailFailure = null,
        )
        try {
            if (!ensureSessionForOperation()) {
                mutableState.value = mutableState.value.copy(
                    isDetailLoading = false,
                    detailFailure = PhyVlabSyncFailure.SESSION_EXPIRED,
                )
                return
            }
            var result = repository.fetchAssignmentDetail(activity)
            if (result is PhyVlabAssignmentDetailResult.Failure &&
                result.reason == PhyVlabSyncFailure.SESSION_EXPIRED
            ) {
                // 读取是幂等操作：会话在打开详情期间过期时，恢复后安全重试一次。
                sessionReady = false
                if (ensureSessionForOperation()) {
                    result = repository.fetchAssignmentDetail(activity)
                }
            }
            when (result) {
                is PhyVlabAssignmentDetailResult.Success -> mutableState.value = mutableState.value.copy(
                    assignmentDetail = result.detail,
                    isDetailLoading = false,
                    detailFailure = null,
                )
                is PhyVlabAssignmentDetailResult.Failure -> mutableState.value = mutableState.value.copy(
                    isDetailLoading = false,
                    detailFailure = result.reason,
                )
            }
        } catch (error: CancellationException) {
            mutableState.value = mutableState.value.copy(isDetailLoading = false)
            throw error
        } catch (_: Exception) {
            mutableState.value = mutableState.value.copy(
                isDetailLoading = false,
                detailFailure = PhyVlabSyncFailure.NETWORK,
            )
        }
    }

    suspend fun submitSelectedActivity(files: List<HomeworkFileContent>) {
        val activity = mutableState.value.selectedActivity ?: return
        if (files.isEmpty()) return
        mutableState.value = mutableState.value.copy(isSubmitting = true, submissionFeedback = null)
        try {
            if (!ensureSessionForOperation()) {
                mutableState.value = mutableState.value.copy(
                    isSubmitting = false,
                    detailFailure = PhyVlabSyncFailure.SESSION_EXPIRED,
                    submissionFeedback = "物理在线会话已失效，请重新建立认证后再提交。",
                )
                return
            }
            // 提交是写操作：只在提交前恢复会话，不对一个不确定的 POST 结果自动重放，
            // 避免服务端已接收文件但响应丢失时产生重复提交。
            when (val result = repository.submitAssignment(activity, files)) {
                PhyVlabSubmissionResult.Success -> {
                    mutableState.value = mutableState.value.copy(
                        isSubmitting = false,
                        submissionFeedback = "已提交，正在刷新批改状态。",
                        assignmentDetail = null,
                    )
                    loadSelectedActivityDetail(force = true)
                }
                is PhyVlabSubmissionResult.Failure -> {
                    if (result.reason == PhyVlabSyncFailure.SESSION_EXPIRED) {
                        sessionReady = false
                    }
                    mutableState.value = mutableState.value.copy(
                        isSubmitting = false,
                        detailFailure = result.reason,
                        submissionFeedback = "提交未确认成功，请稍后重试。",
                    )
                }
            }
        } catch (error: CancellationException) {
            mutableState.value = mutableState.value.copy(isSubmitting = false)
            throw error
        } catch (_: Exception) {
            mutableState.value = mutableState.value.copy(
                isSubmitting = false,
                detailFailure = PhyVlabSyncFailure.NETWORK,
                submissionFeedback = "提交失败，请检查网络后重试。",
            )
        }
    }

    suspend fun loadSelectedActivities() {
        // 全量同步会逐门读取课程页；Compose 的选中课程副作用可能在第一门请求尚未完成时
        // 触发，这里直接等待全量同步的结果，避免同一课程产生重复网络请求。
        if (mutableState.value.isLoading) return
        val course = mutableState.value.selectedCourse ?: return
        activitiesByCourse[course.id]?.let { cached ->
            mutableState.value = mutableState.value.copy(activities = cached)
            return
        }
        refreshActivities(course)
    }

    suspend fun changeMonth(direction: Int) {
        val base = lastRequestedMonthSeconds ?: return
        val next = beijingMonthStartSecondsSafe(base, direction) ?: return
        lastRequestedMonthSeconds = next
        try {
            when (val result = repository.fetchEvents(next)) {
                is PhyVlabEventsResult.Success ->
                    mutableState.value = mutableState.value.copy(
                        events = mergeEvents(result.events, eventsForMonth(deadlineEvents, next)),
                        monthLabel = monthLabelFor(next),
                        failure = null,
                    )
                is PhyVlabEventsResult.Failure -> publishFailure(result.reason)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            publishFailure(PhyVlabSyncFailure.NETWORK)
        }
    }

    private suspend fun refreshActivities(course: PhyVlabCourse) {
        try {
            when (val result = repository.fetchCourseActivities(course)) {
                is PhyVlabActivitiesResult.Success -> {
                    activitiesByCourse[course.id] = result.activities
                    mutableState.value = mutableState.value.copy(
                        activities = result.activities,
                        events = mergeEvents(
                            mutableState.value.events,
                            result.activities.mapNotNull { it.toDeadlineEvent() },
                        ),
                        failure = null,
                    )
                }
                is PhyVlabActivitiesResult.Failure -> publishFailure(result.reason)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            publishFailure(PhyVlabSyncFailure.NETWORK)
        }
    }

    private fun publishFailure(reason: PhyVlabSyncFailure, detail: String? = null) {
        mutableState.value = mutableState.value.copy(failure = reason, failureDetail = detail)
    }

    private fun clearLoading() {
        if (mutableState.value.isLoading) {
            mutableState.value = mutableState.value.copy(isLoading = false)
        }
    }

    private suspend fun establishSessionWithRecovery(): PhyVlabSessionResult {
        val initial = sessionProtocol.establishSession()
        val needsRecovery = initial == PhyVlabSessionResult.CasLoginRequired ||
            (initial is PhyVlabSessionResult.Failed &&
                initial.reason == PhyVlabRemoteFailure.SESSION_EXPIRED)
        val recovery = reauthenticate ?: return initial
        if (!needsRecovery) return initial

        phyVlabDebug("session recovery start")
        val recovered = runCatching { recovery() }.getOrDefault(false)
        phyVlabDebug("session recovery result=$recovered")
        if (!recovered) return initial
        return sessionProtocol.establishSession()
    }

    private suspend fun ensureSessionForOperation(): Boolean {
        if (sessionReady) return true
        return when (establishSessionWithRecovery()) {
            is PhyVlabSessionResult.Ready -> {
                sessionReady = true
                true
            }
            PhyVlabSessionResult.CasLoginRequired,
            is PhyVlabSessionResult.Failed,
            -> {
                sessionReady = false
                false
            }
        }
    }
}
private fun PhyVlabActivity.toDeadlineEvent(): PhyVlabEvent? = dueTimestamp?.let { timestamp ->
    PhyVlabEvent(
        id = "activity-$courseId-$id-due",
        title = "$courseName · $title",
        dateText = dueText.orEmpty(),
        dayTimestamp = timestamp,
        eventUrl = activityUrl,
    )
}

private fun eventsForMonth(events: List<PhyVlabEvent>, monthStart: Long): List<PhyVlabEvent> {
    val nextMonth = beijingMonthStartSecondsSafe(monthStart, 1) ?: return events
    return events.filter { it.dayTimestamp in monthStart until nextMonth }
        .sortedWith(compareBy<PhyVlabEvent> { it.dayTimestamp }.thenBy { it.title })
}

private fun mergeEvents(
    primary: List<PhyVlabEvent>,
    secondary: List<PhyVlabEvent>,
): List<PhyVlabEvent> = (primary + secondary)
    .distinctBy { event -> event.eventUrl ?: "id:${event.id}" }
    .sortedWith(compareBy<PhyVlabEvent> { it.dayTimestamp }.thenBy { it.title })

private fun monthLabelFor(timestamp: Long): String = kotlin.time.Instant.fromEpochSeconds(timestamp)
    .toLocalDateTime(TimeZone.of("Asia/Shanghai"))
    .let { dateTime -> "${dateTime.year}年${dateTime.month.ordinal + 1}月" }

private fun beijingMonthStartSeconds(year: Int, month: Int): Long {
    val daysBeforeMonth = when (month) {
        1 -> 0
        2 -> 31
        3 -> 59
        4 -> 90
        5 -> 120
        6 -> 151
        7 -> 181
        8 -> 212
        9 -> 243
        10 -> 273
        11 -> 304
        else -> 334
    }
    val leapDay = if (month > 2 && year.isLeapYear()) 1 else 0
    val daysSinceEpoch = (year - 1970) * 365 +
        ((year - 1) / 4 - 1969 / 4) -
        ((year - 1) / 100 - 1969 / 100) +
        ((year - 1) / 400 - 1969 / 400) +
        daysBeforeMonth +
        leapDay
    return daysSinceEpoch * 86400L - 8 * 3600L
}

private fun beijingMonthStartSecondsSafe(current: Long, direction: Int): Long? {
    val date = kotlin.time.Instant.fromEpochSeconds(current)
        .toLocalDateTime(PHYVLAB_TIME_ZONE).date
    val target = LocalDate(date.year, date.month, 1)
        .plus(direction, DateTimeUnit.MONTH)
    return beijingMonthStartSeconds(target.year, target.month.ordinal + 1)
}

private fun Int.isLeapYear(): Boolean = this % 400 == 0 || (this % 4 == 0 && this % 100 != 0)

private fun PhyVlabRemoteFailure.toUiFailure(): PhyVlabSyncFailure = when (this) {
    PhyVlabRemoteFailure.NETWORK -> PhyVlabSyncFailure.NETWORK
    PhyVlabRemoteFailure.PARSE -> PhyVlabSyncFailure.PARSE
    PhyVlabRemoteFailure.SESSION_EXPIRED -> PhyVlabSyncFailure.SESSION_EXPIRED
}
