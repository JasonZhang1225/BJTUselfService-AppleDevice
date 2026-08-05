package team.bjtuss.bjtuselfservice.shared.feature.grade

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.NavigationEvent
import team.bjtuss.bjtuselfservice.shared.LocalReduceMotion
import team.bjtuss.bjtuselfservice.shared.PlatformFamily
import team.bjtuss.bjtuselfservice.shared.PlatformInfo
import team.bjtuss.bjtuselfservice.shared.WindowClass
import team.bjtuss.bjtuselfservice.shared.accessibleAlpha
import team.bjtuss.bjtuselfservice.shared.feature.shell.AppErrorBanner
import team.bjtuss.bjtuselfservice.shared.usesLegacySmartTransportFor
import team.bjtuss.bjtuselfservice.shared.auth.StudentProfile
import team.bjtuss.bjtuselfservice.shared.cache.AppPreferences
import team.bjtuss.bjtuselfservice.shared.data.grade.GradeSyncFailure
import team.bjtuss.bjtuselfservice.shared.data.home.HomeChangeFeedRepository
import team.bjtuss.bjtuselfservice.shared.feature.course.CourseScheduleContentSource
import team.bjtuss.bjtuselfservice.shared.feature.course.CourseScheduleScreenModel
import team.bjtuss.bjtuselfservice.shared.feature.course.CourseScheduleWorkspace
import team.bjtuss.bjtuselfservice.shared.feature.exam.ExamScheduleScreenModel
import team.bjtuss.bjtuselfservice.shared.feature.exam.ExamScheduleWorkspace
import team.bjtuss.bjtuselfservice.shared.feature.homework.HomeworkScreenModel
import team.bjtuss.bjtuselfservice.shared.feature.homework.HomeworkWorkspace
import team.bjtuss.bjtuselfservice.shared.feature.courseware.CoursewareScreenModel
import team.bjtuss.bjtuselfservice.shared.feature.courseware.CoursewareWorkspace
import team.bjtuss.bjtuselfservice.shared.feature.otherfunction.OtherFunctionScreenModel
import team.bjtuss.bjtuselfservice.shared.feature.otherfunction.CalendarDownloadWorkspace
import team.bjtuss.bjtuselfservice.shared.feature.otherfunction.ReportCardDownloadWorkspace
import team.bjtuss.bjtuselfservice.shared.feature.classroom.ClassroomScreenModel
import team.bjtuss.bjtuselfservice.shared.feature.classroom.ClassroomBuildingWorkspace
import team.bjtuss.bjtuselfservice.shared.feature.classroom.ClassroomWorkspace
import team.bjtuss.bjtuselfservice.shared.feature.settings.SettingsScreenModel
import team.bjtuss.bjtuselfservice.shared.feature.settings.SettingsWorkspace
import team.bjtuss.bjtuselfservice.shared.feature.mailbox.MailboxScreenModel
import team.bjtuss.bjtuselfservice.shared.feature.mailbox.MailboxUiState
import team.bjtuss.bjtuselfservice.shared.feature.mailbox.MailboxWorkspace
import team.bjtuss.bjtuselfservice.shared.feature.home.HomeScreenModel
import team.bjtuss.bjtuselfservice.shared.feature.home.HomeWorkspace
import team.bjtuss.bjtuselfservice.shared.feature.shell.AppCommand
import team.bjtuss.bjtuselfservice.shared.feature.shell.AppCommandBus
import team.bjtuss.bjtuselfservice.shared.files.HomeworkFileGateway
import team.bjtuss.bjtuselfservice.shared.files.CoursewareDirectoryGateway
import team.bjtuss.bjtuselfservice.shared.domain.grade.CourseType
import team.bjtuss.bjtuselfservice.shared.domain.grade.Grade
import team.bjtuss.bjtuselfservice.shared.domain.grade.GradeInfoResult
import team.bjtuss.bjtuselfservice.shared.domain.grade.GradeSortOrder
import team.bjtuss.bjtuselfservice.shared.domain.grade.displayCourseName
import team.bjtuss.bjtuselfservice.shared.domain.grade.displayName
import team.bjtuss.bjtuselfservice.shared.domain.grade.scoreForSorting
import team.bjtuss.bjtuselfservice.shared.domain.home.HomeChangeDomain

private sealed interface AppRoute : NavKey

private enum class AppSection(val title: String) : AppRoute {
    HOME("首页"),
    GRADES("成绩"),
    SCHEDULE("课程表"),
    EXAMS("考试安排"),
    HOMEWORK("作业"),
    COURSEWARE("课件"),
    CLASSROOMS("教室"),
    MAILBOX("邮箱"),
    CALENDAR_DOWNLOAD("校历下载"),
    REPORT_CARD_DOWNLOAD("成绩单下载"),
    SETTINGS("设置"),
    MORE("更多"),
}

/** 紧凑布局底部导航直接暴露的一级入口；其余入口收进“更多”页。 */
private val BottomNavSections = listOf(
    AppSection.HOME,
    AppSection.SCHEDULE,
    AppSection.GRADES,
    AppSection.HOMEWORK,
    AppSection.MORE,
)

/** 在底部导航中归属“更多”高亮的入口。 */
private val MoreGroupSections = setOf(
    AppSection.EXAMS,
    AppSection.COURSEWARE,
    AppSection.CLASSROOMS,
    AppSection.MAILBOX,
    AppSection.CALENDAR_DOWNLOAD,
    AppSection.REPORT_CARD_DOWNLOAD,
    AppSection.SETTINGS,
    AppSection.MORE,
)

private const val LOGIN_SYNC_RETRY_DELAY_MILLIS = 700L
/** 教室详情的第三级路由：独立于一级/二级 section。 */
private data object ClassroomDetailRoute : AppRoute
const val CLASSROOM_DETAIL_ROUTE_ID = "CLASSROOM_DETAIL"

/** Google predictive-back full-screen surface 的 SystemUI 插值。 */
private val androidPredictiveEasing = CubicBezierEasing(0.1f, 0.1f, 0f, 1f)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthenticatedAppShell(
    session: team.bjtuss.bjtuselfservice.shared.AuthenticatedSession,
    platform: PlatformInfo,
    windowClass: WindowClass,
    appCommandBus: AppCommandBus? = null,
    nativeNavigationEnabled: Boolean = false,
    onOpenNativeRoute: (String) -> Unit = {},
    forcedRouteId: String? = null,
    onCloseNativeRoute: () -> Unit = {},
    homeworkFileGatewayOverride: HomeworkFileGateway? = null,
    coursewareDirectoryGatewayOverride: CoursewareDirectoryGateway? = null,
) {
    val profile = session.profile
    val entryLoggingIn = session.entryLoggingIn
    val gradeModel = session.gradeModel
    val courseScheduleModel = session.courseScheduleModel
    val examScheduleModel = session.examScheduleModel
    val homeworkModel = session.homeworkModel
    val coursewareModel = session.coursewareModel
    val otherFunctionModel = session.otherFunctionModel
    val classroomModel = session.classroomModel
    val settingsModel = session.settingsModel
    val loginSyncPreferences = session.loginSyncPreferences
    val mailboxModel = session.mailboxModel
    val homeModel = session.homeModel
    val homeChangeFeed = session.homeChangeFeed
    val homeworkFileGateway = homeworkFileGatewayOverride ?: session.homeworkFileGateway
    val coursewareDirectoryGateway = coursewareDirectoryGatewayOverride ?: session.coursewareDirectoryGateway
    val onLogout = session.onLogout
    val gradeState by gradeModel.state.collectAsState()
    val courseState by courseScheduleModel.state.collectAsState()
    val examState by examScheduleModel.state.collectAsState()
    val homeworkState by homeworkModel.state.collectAsState()
    val coursewareState by coursewareModel.state.collectAsState()
    val classroomState by classroomModel.state.collectAsState()
    val mailboxState by mailboxModel.state.collectAsState()
    val homeState by homeModel.state.collectAsState()
    val homeChanges by homeChangeFeed.records.collectAsState()
    var legacyWarningDismissed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    // 紧凑端底栏属于一级 destination，自身随场景一起切换；NavDisplay 始终保持全屏尺寸，
    // 避免 push/pop 时因底栏显隐改变内容高度。NavigationBar 本体高 80dp，并追加系统 inset。
    val compactBottomBarOverlayPadding = if (windowClass != WindowClass.Expanded) {
        80.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    } else {
        0.dp
    }

    // Navigation 3：应用直接拥有返回栈。一级 tab 总是以 HOME 为根，二/三级页继续压栈；
    // NavDisplay 负责 Android predictive back 与 iOS start-edge back 的连续手势进度。
    val initialRoute = remember(forcedRouteId) {
        forcedRouteId?.toAppRoute() ?: AppSection.HOME
    }
    val backStack = remember(forcedRouteId) { mutableStateListOf<AppRoute>(initialRoute) }
    val currentRoute = backStack.last()
    val section: AppSection = when (currentRoute) {
        ClassroomDetailRoute -> AppSection.CLASSROOMS
        is AppSection -> currentRoute
    }
    val popBackStack: () -> Unit = if (forcedRouteId != null) {
        onCloseNativeRoute
    } else {
        { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) }
    }
    val navigateToSection: (AppSection) -> Unit = { target ->
        if (backStack.lastOrNull() != target) {
            if (
                nativeNavigationEnabled &&
                target in MoreGroupSections &&
                target != AppSection.MORE
            ) {
                onOpenNativeRoute(target.name)
            } else if (target in MoreGroupSections && target != AppSection.MORE) {
                // 二级页：保留当前来源并 push，返回可准确预览来源页。
                backStack.add(target)
            } else {
                // 一级页：复刻旧 popUpTo(HOME) 语义；从非首页返回时先回首页。
                backStack.clear()
                backStack.add(AppSection.HOME)
                if (target != AppSection.HOME) backStack.add(target)
            }
        }
    }
    val refresh: () -> Unit = {
        scope.launch {
            // 静默自动登录期间会话未就绪，忽略刷新；登录完成后各模块会按自动同步设置初始化。
            if (entryLoggingIn) return@launch
            when (section) {
                AppSection.HOME -> coroutineScope {
                    launch { homeModel.refresh() }
                    launch { homeworkModel.refresh() }
                    launch { examScheduleModel.refresh() }
                    launch { courseScheduleModel.refresh() }
                }
                AppSection.GRADES -> gradeModel.refresh()
                AppSection.SCHEDULE -> courseScheduleModel.refresh()
                AppSection.EXAMS -> examScheduleModel.refresh()
                AppSection.HOMEWORK -> homeworkModel.refresh()
                AppSection.COURSEWARE -> coursewareModel.refresh()
                AppSection.CLASSROOMS -> classroomModel.refresh()
                AppSection.MAILBOX -> mailboxModel.refresh()
                AppSection.CALENDAR_DOWNLOAD -> Unit
                AppSection.REPORT_CARD_DOWNLOAD -> Unit
                AppSection.SETTINGS -> Unit
                AppSection.MORE -> Unit
            }
        }
    }

    LaunchedEffect(appCommandBus) {
        appCommandBus?.commands?.collect { command ->
            when (command) {
                AppCommand.NAVIGATE_HOME -> navigateToSection(AppSection.HOME)
                AppCommand.NAVIGATE_GRADES -> navigateToSection(AppSection.GRADES)
                AppCommand.NAVIGATE_SCHEDULE -> navigateToSection(AppSection.SCHEDULE)
                AppCommand.NAVIGATE_EXAMS -> navigateToSection(AppSection.EXAMS)
                AppCommand.NAVIGATE_HOMEWORK -> navigateToSection(AppSection.HOMEWORK)
                AppCommand.NAVIGATE_COURSEWARE -> navigateToSection(AppSection.COURSEWARE)
                AppCommand.NAVIGATE_CLASSROOMS -> navigateToSection(AppSection.CLASSROOMS)
                AppCommand.NAVIGATE_MAILBOX -> navigateToSection(AppSection.MAILBOX)
                AppCommand.NAVIGATE_SETTINGS -> navigateToSection(AppSection.SETTINGS)
                AppCommand.REFRESH_CURRENT -> refresh()
            }
        }
    }

    LaunchedEffect(gradeModel, entryLoggingIn) {
        if (entryLoggingIn) return@LaunchedEffect
        gradeModel.initialize(loginSyncPreferences.autoSyncGrades)
        if (loginSyncPreferences.autoSyncGrades && gradeModel.state.value.failure != null) {
            delay(LOGIN_SYNC_RETRY_DELAY_MILLIS)
            gradeModel.refresh()
        }
    }
    LaunchedEffect(homeworkModel, examScheduleModel, courseScheduleModel, entryLoggingIn) {
        if (entryLoggingIn) return@LaunchedEffect
        coroutineScope {
            launch {
                homeworkModel.initialize(loginSyncPreferences.autoSyncHomework)
                if (loginSyncPreferences.autoSyncHomework && homeworkModel.state.value.failure != null) {
                    delay(LOGIN_SYNC_RETRY_DELAY_MILLIS)
                    homeworkModel.refresh()
                }
            }
            launch {
                examScheduleModel.initialize(loginSyncPreferences.autoSyncExams)
                if (loginSyncPreferences.autoSyncExams && examScheduleModel.state.value.failure != null) {
                    delay(LOGIN_SYNC_RETRY_DELAY_MILLIS)
                    examScheduleModel.refresh()
                }
            }
            launch {
                // 课表自动同步的重试在 ScreenModel.initialize 内完成（最多 3 次）。
                courseScheduleModel.initialize(loginSyncPreferences.autoSyncSchedule)
            }
        }
    }

    // 页面骨架（顶栏 + 下拉刷新）包在各目的地内部而非 NavHost 外层：
    // 1) 外层容器若随当前页面切换（如“更多”页不可刷新、考试安排页可刷新），NavHost 会在
    //    转场瞬间移出/移入 composition 被销毁重建，表现为点击进出页面没有任何转场动画；
    // 2) 顶栏随页面一起参与转场才是原生观感——整个屏幕一起动，而非标题栏先跳变；
    // 3) 必须铺不透明背景：页面透明时转场中上下两层互相穿透（iOS 上会露出灰色窗口底色），
    //    看起来像层级重叠遮挡。
    @Composable
    fun DestinationPage(
        title: String,
        expanded: Boolean,
        refreshable: Boolean,
        isRefreshing: Boolean,
        showBack: Boolean,
        modifier: Modifier,
        /** 空闲时右上角状态文案（如课表「已同步/未同步」）；登录中/同步中优先覆盖。 */
        idleStatusText: String? = null,
        content: @Composable () -> Unit,
    ) {
        val showsCompactBottomBar = !expanded && !showBack && compactBottomBarOverlayPadding > 0.dp
        Box(modifier = modifier.background(MaterialTheme.colorScheme.background)) {
            Column(
                modifier = Modifier.fillMaxSize().padding(
                    bottom = if (showsCompactBottomBar) compactBottomBarOverlayPadding else 0.dp,
                ),
            ) {
                if (!expanded) {
                    CompactAppTopBar(
                        title = title,
                        isRefreshing = isRefreshing,
                        isLoggingIn = entryLoggingIn,
                        idleStatusText = idleStatusText,
                        onBack = if (showBack) {
                            popBackStack
                        } else {
                            null
                        },
                    )
                }
                val contentModifier = Modifier.weight(1f).fillMaxWidth()
                if (!expanded && refreshable) {
                    PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = refresh, modifier = contentModifier) {
                        content()
                    }
                } else {
                    // 宽屏布局本就没有下拉刷新；邮箱/设置/下载/更多页也不可刷新。
                    Box(modifier = contentModifier) { content() }
                }
            }
            if (showsCompactBottomBar) {
                // 底栏随一级场景一起运动；二/三级场景在其上方，返回预览不会提前跳出底栏。
                CompactBottomNavigation(
                    section = section,
                    onSectionSelected = navigateToSection,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }

    // Navigation 3 的目的地渲染器：compact/expanded 两套布局共用，
    // entryProvider 只负责把类型安全 route 交给这里，页面业务保持不变。
    @Composable
    fun SectionDestination(
        route: AppRoute,
        expanded: Boolean,
        modifier: Modifier,
        usesLegacySmartTransport: Boolean,
    ) {
        when (route) {
            AppSection.HOME -> DestinationPage(
                title = AppSection.HOME.title,
                expanded = expanded,
                refreshable = true,
                isRefreshing = homeState.isRefreshing || homeworkState.isRefreshing ||
                    examState.isRefreshing || courseState.isRefreshing,
                showBack = false,
                modifier = modifier,
            ) {
                HomeWorkspace(
                    model = homeModel,
                    platform = platform,
                    expanded = expanded,
                    holdNetwork = entryLoggingIn,
                    homework = homeworkState.homework,
                    exams = examState.exams,
                    currentWeek = courseState.currentWeek,
                    now = homeworkState.now,
                    timeZone = homeworkState.timeZone,
                    isAgendaLoading = homeworkState.isLoading || examState.isLoading || courseState.isLoading,
                    isRefreshing = homeState.isRefreshing || homeworkState.isRefreshing ||
                        examState.isRefreshing || courseState.isRefreshing,
                    onRefresh = refresh,
                    onOpenMailbox = { navigateToSection(AppSection.MAILBOX) },
                    onOpenHomework = { navigateToSection(AppSection.HOMEWORK) },
                    onOpenExams = { navigateToSection(AppSection.EXAMS) },
                    changes = homeChanges,
                    onClearAllChanges = { scope.launch { homeChangeFeed.clear() } },
                    onClearChangeDomain = { domain -> scope.launch { homeChangeFeed.clear(domain) } },
                    onOpenChangeDomain = { domain -> navigateToSection(domain.toAppSection()) },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            AppSection.GRADES -> DestinationPage(
                title = AppSection.GRADES.title,
                expanded = expanded,
                refreshable = true,
                isRefreshing = gradeState.isRefreshing,
                showBack = false,
                modifier = modifier,
                // 与课表一致：同步态在顶栏右上，banner 内只放成绩摘要与筛选入口。
                idleStatusText = when {
                    gradeState.failure != null -> "同步失败"
                    gradeState.source == GradeContentSource.NETWORK -> "已同步"
                    gradeState.source == GradeContentSource.CACHE -> "已同步"
                    else -> "未同步"
                },
            ) {
                GradeWorkspace(
                    state = gradeState,
                    expanded = expanded,
                    model = gradeModel,
                    onRefresh = refresh,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            AppSection.SCHEDULE -> DestinationPage(
                title = AppSection.SCHEDULE.title,
                expanded = expanded,
                refreshable = true,
                isRefreshing = courseState.isRefreshing,
                showBack = false,
                modifier = modifier,
                // 同步状态放在顶栏右上；有失败横幅时不要仍显示「已同步」。
                idleStatusText = when {
                    courseState.failure != null -> "同步失败"
                    courseState.source == CourseScheduleContentSource.NETWORK -> "已同步"
                    courseState.source == CourseScheduleContentSource.CACHE -> "已同步"
                    else -> "未同步"
                },
            ) {
                CourseScheduleWorkspace(
                    state = courseState,
                    expanded = expanded,
                    model = courseScheduleModel,
                    onRefresh = refresh,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            AppSection.EXAMS -> DestinationPage(
                title = AppSection.EXAMS.title,
                expanded = expanded,
                refreshable = true,
                isRefreshing = examState.isRefreshing,
                showBack = true,
                modifier = modifier,
            ) {
                ExamScheduleWorkspace(
                    state = examState,
                    expanded = expanded,
                    model = examScheduleModel,
                    onRefresh = refresh,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            AppSection.HOMEWORK -> DestinationPage(
                title = AppSection.HOMEWORK.title,
                expanded = expanded,
                refreshable = true,
                isRefreshing = homeworkState.isRefreshing,
                showBack = false,
                modifier = modifier,
            ) {
                HomeworkWorkspace(
                    state = homeworkState,
                    expanded = expanded,
                    usesLegacySmartTransport = usesLegacySmartTransport,
                    legacyWarningVisible = usesLegacySmartTransportFor(platform.family) && !legacyWarningDismissed,
                    onDismissLegacyWarning = { legacyWarningDismissed = true },
                    model = homeworkModel,
                    fileGateway = homeworkFileGateway,
                    onRefresh = refresh,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            AppSection.COURSEWARE -> DestinationPage(
                title = AppSection.COURSEWARE.title,
                expanded = expanded,
                refreshable = true,
                isRefreshing = coursewareState.isRefreshing,
                showBack = true,
                modifier = modifier,
            ) {
                CoursewareWorkspace(
                    state = coursewareState,
                    expanded = expanded,
                    usesLegacySmartTransport = usesLegacySmartTransport,
                    legacyWarningVisible = usesLegacySmartTransportFor(platform.family) && !legacyWarningDismissed,
                    onDismissLegacyWarning = { legacyWarningDismissed = true },
                    model = coursewareModel,
                    fileGateway = homeworkFileGateway,
                    directoryGateway = coursewareDirectoryGateway,
                    onRefresh = refresh,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            AppSection.CALENDAR_DOWNLOAD -> DestinationPage(
                title = AppSection.CALENDAR_DOWNLOAD.title,
                expanded = expanded,
                refreshable = false,
                isRefreshing = false,
                showBack = true,
                modifier = modifier,
            ) {
                CalendarDownloadWorkspace(
                    model = otherFunctionModel,
                    expanded = expanded,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            AppSection.REPORT_CARD_DOWNLOAD -> DestinationPage(
                title = AppSection.REPORT_CARD_DOWNLOAD.title,
                expanded = expanded,
                refreshable = false,
                isRefreshing = false,
                showBack = true,
                modifier = modifier,
            ) {
                ReportCardDownloadWorkspace(
                    model = otherFunctionModel,
                    expanded = expanded,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            AppSection.CLASSROOMS -> DestinationPage(
                title = AppSection.CLASSROOMS.title,
                expanded = expanded,
                refreshable = true,
                isRefreshing = classroomState.isLoading,
                showBack = true,
                modifier = modifier,
            ) {
                ClassroomWorkspace(
                    model = classroomModel,
                    expanded = expanded,
                    onOpenBuilding = {
                        if (nativeNavigationEnabled) {
                            onOpenNativeRoute(CLASSROOM_DETAIL_ROUTE_ID)
                        } else if (backStack.lastOrNull() != ClassroomDetailRoute) {
                            backStack.add(ClassroomDetailRoute)
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            ClassroomDetailRoute -> DestinationPage(
                title = AppSection.CLASSROOMS.title,
                expanded = expanded,
                refreshable = true,
                isRefreshing = classroomState.isLoading,
                showBack = true,
                modifier = modifier,
            ) {
                ClassroomBuildingWorkspace(
                    model = classroomModel,
                    onBack = popBackStack,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            AppSection.SETTINGS -> DestinationPage(
                title = AppSection.SETTINGS.title,
                expanded = expanded,
                refreshable = false,
                isRefreshing = false,
                showBack = true,
                modifier = modifier,
            ) {
                SettingsWorkspace(
                    model = settingsModel,
                    accountName = "${profile.name} · ${profile.studentId}",
                    platform = platform,
                    expanded = expanded,
                    onLogout = onLogout,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            AppSection.MAILBOX -> DestinationPage(
                title = AppSection.MAILBOX.title,
                expanded = expanded,
                refreshable = false,
                isRefreshing = mailboxState == MailboxUiState.Preparing,
                showBack = true,
                modifier = modifier,
            ) {
                MailboxWorkspace(
                    model = mailboxModel,
                    platform = platform,
                    expanded = expanded,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            AppSection.MORE -> DestinationPage(
                title = AppSection.MORE.title,
                expanded = expanded,
                refreshable = false,
                isRefreshing = false,
                showBack = false,
                modifier = modifier,
            ) {
                MoreWorkspace(
                    onOpenSection = navigateToSection,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    if (forcedRouteId != null) {
        SectionDestination(
            route = currentRoute,
            expanded = false,
            modifier = Modifier.fillMaxSize(),
            usesLegacySmartTransport = usesLegacySmartTransportFor(platform.family),
        )
    } else if (windowClass == WindowClass.Expanded) {
        Row(
            modifier = Modifier.fillMaxSize().padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            AppSidebar(
                profile = profile,
                platform = platform,
                section = section,
                onSectionSelected = navigateToSection,
                onLogout = onLogout,
                modifier = Modifier.width(236.dp).fillMaxHeight(),
            )
            // 宽屏侧栏布局按 macOS/iPad 的并列工作区处理，不播放手机式 push/pop。
            NavDisplay(
                backStack = backStack,
                onBack = popBackStack,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                transitionSpec = { EnterTransition.None togetherWith ExitTransition.None },
                popTransitionSpec = { EnterTransition.None togetherWith ExitTransition.None },
                predictivePopTransitionSpec = { _: Int ->
                    EnterTransition.None togetherWith ExitTransition.None
                },
                entryProvider = entryProvider {
                    entry<AppSection> { route ->
                        SectionDestination(
                            route = route,
                            expanded = true,
                            modifier = Modifier.fillMaxSize(),
                            usesLegacySmartTransport = usesLegacySmartTransportFor(platform.family),
                        )
                    }
                    entry<ClassroomDetailRoute> {
                        SectionDestination(
                            route = ClassroomDetailRoute,
                            expanded = true,
                            modifier = Modifier.fillMaxSize(),
                            usesLegacySmartTransport = usesLegacySmartTransportFor(platform.family),
                        )
                    }
                },
            )
        }
    } else {
        val reduceMotion = LocalReduceMotion.current
        val direction = if (LocalLayoutDirection.current == LayoutDirection.Ltr) 1 else -1
        val appleSpatialSpec = spring<androidx.compose.ui.unit.IntOffset>(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = 320f,
        )
        val appleInteractiveSpec = tween<androidx.compose.ui.unit.IntOffset>(
            durationMillis = 350,
            easing = LinearEasing,
        )
        val androidOffsetSpec = tween<androidx.compose.ui.unit.IntOffset>(
            durationMillis = 350,
            easing = androidPredictiveEasing,
        )
        val androidScaleSpec = tween<Float>(
            durationMillis = 350,
            easing = androidPredictiveEasing,
        )

        // Android 使用 Navigation 3 的 seekable 场景内核，并按 Google full-screen surface
        // predictive-back 规范让前景 100%→90%、后景 110%→100%，同时保留小幅横向预览。
        // iOS 使用可被 NavDisplay start-edge 手势 seek 的 UINavigationController 空间路径；
        // macOS 的紧凑窗口仍遵守桌面习惯，不播放手机式整页滑动。
        NavDisplay(
            backStack = backStack,
            onBack = popBackStack,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                when {
                    nativeNavigationEnabled ->
                        EnterTransition.None togetherWith ExitTransition.None
                    reduceMotion ->
                        fadeIn(tween(150)) togetherWith fadeOut(tween(150))
                    platform.family == PlatformFamily.Android ->
                        (slideInHorizontally(
                            initialOffsetX = { direction * it / 12 },
                            animationSpec = androidOffsetSpec,
                        ) + scaleIn(
                            initialScale = 0.96f,
                            animationSpec = androidScaleSpec,
                        ) + fadeIn(tween(220))) togetherWith
                            (slideOutHorizontally(
                                targetOffsetX = { -direction * it / 20 },
                                animationSpec = androidOffsetSpec,
                            ) + scaleOut(
                                targetScale = 0.90f,
                                animationSpec = androidScaleSpec,
                            ) + fadeOut(tween(280)))
                    platform.family == PlatformFamily.IOS ->
                        slideInHorizontally(
                            initialOffsetX = { direction * it },
                            animationSpec = appleSpatialSpec,
                        ) togetherWith slideOutHorizontally(
                            targetOffsetX = { -direction * it / 3 },
                            animationSpec = appleSpatialSpec,
                        )
                    else -> EnterTransition.None togetherWith ExitTransition.None
                }
            },
            popTransitionSpec = {
                when {
                    nativeNavigationEnabled ->
                        EnterTransition.None togetherWith ExitTransition.None
                    reduceMotion ->
                        fadeIn(tween(150)) togetherWith fadeOut(tween(150))
                    platform.family == PlatformFamily.Android ->
                        (slideInHorizontally(
                            initialOffsetX = { -direction * it / 20 },
                            animationSpec = androidOffsetSpec,
                        ) + scaleIn(
                            initialScale = 1.10f,
                            animationSpec = androidScaleSpec,
                        ) + fadeIn(tween(280))) togetherWith
                            (slideOutHorizontally(
                                targetOffsetX = { direction * it / 20 },
                                animationSpec = androidOffsetSpec,
                            ) + scaleOut(
                                targetScale = 0.90f,
                                animationSpec = androidScaleSpec,
                            ) + fadeOut(tween(220)))
                    platform.family == PlatformFamily.IOS ->
                        slideInHorizontally(
                            initialOffsetX = { -direction * it / 3 },
                            animationSpec = appleSpatialSpec,
                        ) togetherWith slideOutHorizontally(
                            targetOffsetX = { direction * it },
                            animationSpec = appleSpatialSpec,
                        )
                    else -> EnterTransition.None togetherWith ExitTransition.None
                }
            },
            predictivePopTransitionSpec = { swipeEdge: Int ->
                val gestureDirection = if (swipeEdge == NavigationEvent.EDGE_RIGHT) -1 else 1
                when {
                    nativeNavigationEnabled ->
                        EnterTransition.None togetherWith ExitTransition.None
                    reduceMotion ->
                        fadeIn(tween(150)) togetherWith fadeOut(tween(150))
                    platform.family == PlatformFamily.Android ->
                        (slideInHorizontally(
                            initialOffsetX = { -gestureDirection * it / 20 },
                            animationSpec = androidOffsetSpec,
                        ) + scaleIn(
                            initialScale = 1.10f,
                            animationSpec = androidScaleSpec,
                        ) + fadeIn(tween(280))) togetherWith
                            (slideOutHorizontally(
                                targetOffsetX = { gestureDirection * it / 20 },
                                animationSpec = androidOffsetSpec,
                            ) + scaleOut(
                                targetScale = 0.90f,
                                animationSpec = androidScaleSpec,
                            ) + fadeOut(tween(220)))
                    platform.family == PlatformFamily.IOS ->
                        slideInHorizontally(
                            initialOffsetX = { -gestureDirection * it / 3 },
                            animationSpec = appleInteractiveSpec,
                        ) togetherWith slideOutHorizontally(
                            targetOffsetX = { gestureDirection * it },
                            animationSpec = appleInteractiveSpec,
                        )
                    else -> EnterTransition.None togetherWith ExitTransition.None
                }
            },
            entryProvider = entryProvider {
                entry<AppSection> { route ->
                    SectionDestination(
                        route = route,
                        expanded = false,
                        modifier = Modifier.fillMaxSize(),
                        usesLegacySmartTransport = false,
                    )
                }
                entry<ClassroomDetailRoute> {
                    SectionDestination(
                        route = ClassroomDetailRoute,
                        expanded = false,
                        modifier = Modifier.fillMaxSize(),
                        usesLegacySmartTransport = false,
                    )
                }
            },
        )
    }
}

private fun String.toAppRoute(): AppRoute? =
    if (this == CLASSROOM_DETAIL_ROUTE_ID) {
        ClassroomDetailRoute
    } else {
        AppSection.entries.firstOrNull { it.name == this }
    }

private fun HomeChangeDomain.toAppSection(): AppSection = when (this) {
    HomeChangeDomain.GRADES -> AppSection.GRADES
    HomeChangeDomain.COURSES -> AppSection.SCHEDULE
    HomeChangeDomain.EXAMS -> AppSection.EXAMS
    HomeChangeDomain.HOMEWORK -> AppSection.HOMEWORK
}

@Composable
private fun AppSidebar(
    profile: StudentProfile,
    platform: PlatformInfo,
    section: AppSection,
    onSectionSelected: (AppSection) -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant.accessibleAlpha(0.62f),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("交大自由行", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                platform.displayName,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Surface(
                color = MaterialTheme.colorScheme.surface.accessibleAlpha(0.82f),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                    Text(
                        profile.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        profile.department,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AppSection.entries.filter { it != AppSection.MORE }.forEach { item ->
                    AppSidebarItem(
                        title = item.title,
                        selected = section == item,
                        onClick = { onSectionSelected(item) },
                    )
                }
                Text(
                    "作业、课件和教室功能使用 HTTP 明文传输，请勿在不可信网络中使用。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) {
                Text("退出并清除登录信息")
            }
        }
    }
}

@Composable
private fun AppSidebarItem(title: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface.accessibleAlpha(0.82f)
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        shape = RoundedCornerShape(14.dp),
    ) {
        Text(
            title,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        )
    }
}

@Composable
private fun CompactAppTopBar(
    title: String,
    isRefreshing: Boolean,
    isLoggingIn: Boolean = false,
    /** 非登录/非刷新时右上角文案（课表等页的「已同步/未同步」）；其它页保持 null。 */
    idleStatusText: String? = null,
    onBack: (() -> Unit)? = null,
) {
    // 顶栏与页面背景同色：iOS 的 SwiftUI 根视图在状态栏下方铺的就是 background，
    // 顶栏若用 surface 会在状态栏下方露出一条浅色带子，破坏沉浸感。
    // iOS 的 Compose 宿主已改为全屏布局（原生 push 转场需要覆盖状态栏区域），
    // WindowInsets.statusBars 在 iOS 上恢复为真实值，顶栏统一应用状态栏内边距。
    val statusBarInset = Modifier.statusBarsPadding()
    Surface(color = MaterialTheme.colorScheme.background) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .then(statusBarInset)
                .padding(
                    start = if (onBack != null) 10.dp else 20.dp,
                    end = 20.dp,
                    top = 14.dp,
                    bottom = 14.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // “更多”子页提供返回上一级的箭头；一级页面没有返回。
            if (onBack != null) {
                Surface(
                    onClick = onBack,
                    color = Color.Transparent,
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                            .semantics { contentDescription = "返回" },
                    ) {
                        BackChevron()
                    }
                }
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            // “登录中”优先于“同步中”，再回落到页面提供的空闲状态（如课表已同步）。
            val busyText = when {
                isLoggingIn -> "登录中"
                isRefreshing -> "同步中"
                else -> null
            }
            if (busyText != null) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    busyText,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (idleStatusText != null) {
                Text(
                    idleStatusText,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 返回箭头：与 MoreEntryChevron 同风格的 Canvas 左尖括号，不引入图标库。 */
@Composable
private fun BackChevron() {
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier = Modifier.size(10.dp, 16.dp)) {
        val strokeWidth = 2.dp.toPx()
        val mid = size.height / 2
        drawLine(
            color,
            Offset(size.width - 2.dp.toPx(), 2.dp.toPx()),
            Offset(2.dp.toPx(), mid),
            strokeWidth,
            cap = StrokeCap.Round,
        )
        drawLine(
            color,
            Offset(2.dp.toPx(), mid),
            Offset(size.width - 2.dp.toPx(), size.height - 2.dp.toPx()),
            strokeWidth,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun CompactBottomNavigation(
    section: AppSection,
    onSectionSelected: (AppSection) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(modifier = modifier) {
        BottomNavSections.forEach { item ->
            NavigationBarItem(
                selected = if (item == AppSection.MORE) section in MoreGroupSections else section == item,
                onClick = { onSectionSelected(item) },
                icon = { CompactTabIcon(item) },
                label = { Text(item.title, style = MaterialTheme.typography.labelSmall) },
            )
        }
    }
}

/** 底部导航图标：与登录页密码眼睛一致用 Canvas 绘制，不引入图标库依赖。 */
@Composable
private fun CompactTabIcon(section: AppSection) {
    val color = LocalContentColor.current
    Canvas(modifier = Modifier.size(24.dp)) {
        val strokeWidth = 1.8.dp.toPx()
        when (section) {
            AppSection.HOME -> {
                // 2×2 圆角方块
                val cell = 7.dp.toPx()
                val gap = 3.dp.toPx()
                val start = (size.width - cell * 2 - gap) / 2
                val radius = CornerRadius(2.dp.toPx())
                for (row in 0..1) {
                    for (col in 0..1) {
                        drawRoundRect(
                            color = color,
                            topLeft = Offset(start + col * (cell + gap), start + row * (cell + gap)),
                            size = Size(cell, cell),
                            cornerRadius = radius,
                        )
                    }
                }
            }
            AppSection.SCHEDULE -> {
                // 日历：圆角外框 + 顶部分隔线 + 两个挂环
                val left = 4.dp.toPx()
                val top = 5.dp.toPx()
                val right = size.width - left
                val bottom = size.height - 4.dp.toPx()
                drawRoundRect(
                    color = color,
                    topLeft = Offset(left, top),
                    size = Size(right - left, bottom - top),
                    cornerRadius = CornerRadius(3.dp.toPx()),
                    style = Stroke(width = strokeWidth),
                )
                val divider = top + 4.dp.toPx()
                drawLine(color, Offset(left, divider), Offset(right, divider), strokeWidth)
                drawLine(
                    color,
                    Offset(left + 4.5.dp.toPx(), top - 2.dp.toPx()),
                    Offset(left + 4.5.dp.toPx(), top + 2.dp.toPx()),
                    strokeWidth,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color,
                    Offset(right - 4.5.dp.toPx(), top - 2.dp.toPx()),
                    Offset(right - 4.5.dp.toPx(), top + 2.dp.toPx()),
                    strokeWidth,
                    cap = StrokeCap.Round,
                )
            }
            AppSection.GRADES -> {
                // 三根高度递增的柱条
                val barWidth = 3.4.dp.toPx()
                val baseY = size.height - 4.dp.toPx()
                val xs = listOf(5.dp.toPx(), 10.3.dp.toPx(), 15.6.dp.toPx())
                val heights = listOf(6.dp.toPx(), 10.dp.toPx(), 14.dp.toPx())
                xs.zip(heights).forEach { (x, h) ->
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(x, baseY - h),
                        size = Size(barWidth, h),
                        cornerRadius = CornerRadius(1.2.dp.toPx()),
                    )
                }
            }
            AppSection.HOMEWORK -> {
                // 便签框 + 对勾
                val left = 5.dp.toPx()
                val top = 4.dp.toPx()
                val right = size.width - 5.dp.toPx()
                val bottom = size.height - 4.dp.toPx()
                drawRoundRect(
                    color = color,
                    topLeft = Offset(left, top),
                    size = Size(right - left, bottom - top),
                    cornerRadius = CornerRadius(3.dp.toPx()),
                    style = Stroke(width = strokeWidth),
                )
                drawLine(
                    color,
                    Offset(left + 3.dp.toPx(), top + 7.5.dp.toPx()),
                    Offset(left + 6.dp.toPx(), top + 10.5.dp.toPx()),
                    strokeWidth,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color,
                    Offset(left + 6.dp.toPx(), top + 10.5.dp.toPx()),
                    Offset(right - 3.dp.toPx(), top + 5.dp.toPx()),
                    strokeWidth,
                    cap = StrokeCap.Round,
                )
            }
            else -> {
                // 更多：三个圆点
                val radius = 1.7.dp.toPx()
                val cy = size.height / 2
                drawCircle(color, radius, Offset(5.5.dp.toPx(), cy))
                drawCircle(color, radius, Offset(size.width / 2, cy))
                drawCircle(color, radius, Offset(size.width - 5.5.dp.toPx(), cy))
            }
        }
    }
}

@Composable
private fun MoreWorkspace(
    onOpenSection: (AppSection) -> Unit,
    modifier: Modifier,
) {
    val entries = listOf(
        AppSection.EXAMS,
        AppSection.COURSEWARE,
        AppSection.CLASSROOMS,
        AppSection.MAILBOX,
        AppSection.CALENDAR_DOWNLOAD,
        AppSection.REPORT_CARD_DOWNLOAD,
        AppSection.SETTINGS,
    )
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        entries.forEach { item ->
            Surface(
                onClick = { onOpenSection(item) },
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        item.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                    )
                    MoreEntryChevron()
                }
            }
        }
    }
}

@Composable
private fun MoreEntryChevron() {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier = Modifier.size(10.dp, 16.dp)) {
        val strokeWidth = 2.dp.toPx()
        val mid = size.height / 2
        drawLine(
            color,
            Offset(2.dp.toPx(), 2.dp.toPx()),
            Offset(size.width - 2.dp.toPx(), mid),
            strokeWidth,
            cap = StrokeCap.Round,
        )
        drawLine(
            color,
            Offset(size.width - 2.dp.toPx(), mid),
            Offset(2.dp.toPx(), size.height - 2.dp.toPx()),
            strokeWidth,
            cap = StrokeCap.Round,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GradeWorkspace(
    state: GradeUiState,
    expanded: Boolean,
    model: GradeScreenModel,
    onRefresh: () -> Unit,
    modifier: Modifier,
) {
    var showFilterSheet by remember { mutableStateOf(false) }

    Column(
        modifier = if (expanded) {
            modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        } else {
            // 与课表/作业紧凑顶距对齐，避免 banner 视觉偏大。
            modifier.padding(horizontal = 16.dp).padding(top = 8.dp)
        },
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (expanded) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "成绩",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        "按学期查看、排序或自选课程计算加权平均分",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FilledTonalButton(onClick = onRefresh, enabled = !state.isRefreshing) {
                    Text(if (state.isRefreshing) "正在同步" else "同步成绩")
                }
            }
        }

        if (state.isRefreshing) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        state.failure?.let { failure ->
            GradeFailureBanner(
                failure = failure,
                hasContent = state.grades.isNotEmpty(),
                onRetry = onRefresh,
                onDismiss = model::dismissFailure,
            )
        }

        when {
            state.isLoading && state.grades.isEmpty() -> GradeLoadingState()
            state.grades.isEmpty() -> GradeEmptyState(onRefresh)
            else -> {
                GradeSummaryCard(
                    state = state,
                    onOpenFilter = { showFilterSheet = true },
                )
                if (expanded) {
                    Row(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        GradeList(
                            state = state,
                            model = model,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                        GradeDetailPanel(
                            grade = state.selectedGrade,
                            modifier = Modifier.widthIn(min = 290.dp, max = 390.dp).fillMaxHeight(),
                        )
                    }
                } else {
                    GradeList(
                        state = state,
                        model = model,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )
                    state.selectedGrade?.let { grade ->
                        ModalBottomSheet(onDismissRequest = model::dismissGradeDetails) {
                            GradeDetailContent(
                                grade = grade,
                                modifier = Modifier.fillMaxWidth()
                                    .verticalScroll(rememberScrollState())
                                    .padding(horizontal = 24.dp, vertical = 8.dp),
                            )
                            Spacer(Modifier.height(24.dp))
                        }
                    }
                }
            }
        }
    }

    if (showFilterSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showFilterSheet = false },
            sheetState = sheetState,
        ) {
            GradeFilterSheet(state = state, model = model)
        }
    }
}

@Composable
private fun GradeSummaryCard(
    state: GradeUiState,
    onOpenFilter: () -> Unit,
) {
    val title = when (val info = state.gradeInfo) {
        GradeInfoResult.NoGrades -> if (state.selectionMode) {
            "请选择用于计算的课程"
        } else {
            "暂无可计算成绩"
        }
        is GradeInfoResult.Calculated -> info.formattedMessage
    }
    val semesterFiltered =
        state.semesterFilterForQuery.isNotEmpty() || state.excludedCourseTypes.isNotEmpty()
    val subtitle = when {
        state.selectionMode -> "自选 ${state.selectedGradeIds.size} 门 · 显示 ${state.visibleGrades.size} 门"
        semesterFiltered -> "筛选后 ${state.visibleGrades.size} 门 · 共 ${state.grades.size} 门"
        else -> "共 ${state.grades.size} 门课程"
    }

    // 尺寸与课表 CourseSummary 对齐：18dp 圆角、14/12 内边距、右侧 pill 操作。
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onOpenFilter)
                    .padding(vertical = 2.dp, horizontal = 4.dp),
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.accessibleAlpha(0.78f),
                )
            }
            Surface(
                onClick = onOpenFilter,
                color = MaterialTheme.colorScheme.surface.accessibleAlpha(0.86f),
                contentColor = MaterialTheme.colorScheme.onSurface,
                shape = RoundedCornerShape(999.dp),
            ) {
                Text(
                    "筛选",
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GradeFilterSheet(
    state: GradeUiState,
    model: GradeScreenModel,
) {
    val semesterOptions = state.semesterOptions
    val allSemestersSelected =
        semesterOptions.isNotEmpty() && state.selectedSemesters.containsAll(semesterOptions)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text("筛选与计算", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

        // —— 学期：小胶囊，默认全选 ——
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "学期",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (!allSemestersSelected && semesterOptions.isNotEmpty()) {
                    TextButton(onClick = model::clearSemesterFilter) { Text("全选") }
                }
            }
            if (semesterOptions.isEmpty()) {
                Text(
                    "暂无学期数据",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    semesterOptions.forEach { semester ->
                        FilterChip(
                            selected = semester in state.selectedSemesters,
                            onClick = { model.toggleSemester(semester) },
                            label = { Text(semester) },
                        )
                    }
                }
            }
        }

        // —— 排序 ——
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "排序",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    GradeSortOrder.ORIGINAL to "默认顺序",
                    GradeSortOrder.ASCENDING to "分数升序",
                    GradeSortOrder.DESCENDING to "分数降序",
                ).forEach { (order, label) ->
                    FilterChip(
                        selected = state.sortOrder == order,
                        onClick = { model.setSortOrder(order) },
                        label = { Text(label) },
                    )
                }
            }
        }

        // —— 课程性质：彩色小胶囊，默认全选；不依赖自选开关 ——
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "课程性质",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            if (state.courseTypesByCode == null) {
                Text(
                    "课程性质未同步，下拉刷新后可用。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(
                        CourseType.REQUIRED to "必修",
                        CourseType.LIMITED to "限选",
                        CourseType.ELECTIVE to "任选",
                        CourseType.PHYSICAL_EDUCATION to "体育",
                        CourseType.UNKNOWN to "其他类别",
                    ).forEach { (type, label) ->
                        val count = state.courseTypeCounts[type] ?: 0
                        if (count <= 0) return@forEach
                        val included = type !in state.excludedCourseTypes
                        val colors = courseTypeColors(type)
                        // 选中：满色底 + 加粗；未选中：接近透明底 + 弱字重 + 虚化描边，对比拉大。
                        FilterChip(
                            selected = included,
                            onClick = { model.toggleCourseTypeIncluded(type) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = colors.container,
                                selectedLabelColor = colors.onContainer,
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                                    .accessibleAlpha(0.35f),
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    .accessibleAlpha(0.48f),
                            ),
                            border = BorderStroke(
                                width = if (included) 1.5.dp else 1.dp,
                                color = if (included) {
                                    colors.onContainer.copy(alpha = 0.42f)
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant.accessibleAlpha(0.55f)
                                },
                            ),
                            label = {
                                Text(
                                    "$label $count",
                                    fontWeight = if (included) FontWeight.SemiBold else FontWeight.Normal,
                                )
                            },
                        )
                    }
                }
            }
        }

        // —— 自选模式：仅控制列表逐门勾选框 ——
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "自选课程计算",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.accessibleAlpha(0.55f),
                shape = RoundedCornerShape(14.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (state.selectionMode) "已开启自选" else "按筛选结果计算加权",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            "开启后列表出现勾选框，可任意点选课程；关闭时用上方学期与性质筛选。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = state.selectionMode,
                        onCheckedChange = model::setSelectionMode,
                    )
                }
            }

            if (state.selectionMode) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(onClick = model::selectAllVisible) { Text("全选当前") }
                    OutlinedButton(onClick = model::clearAllSelections) { Text("全部清空") }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun GradeList(
    state: GradeUiState,
    model: GradeScreenModel,
    modifier: Modifier,
) {
    if (state.visibleGrades.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                "当前筛选条件下没有成绩",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 20.dp),
    ) {
        items(state.visibleGrades, key = Grade::id) { grade ->
            GradeRow(
                grade = grade,
                courseType = state.courseTypeOf(grade),
                selectionMode = state.selectionMode,
                selectedForCalculation = grade.id in state.selectedGradeIds,
                selectedForDetails = grade.id == state.selectedGradeId,
                onOpen = { model.showGradeDetails(grade.id) },
                onSelectionChange = { selected -> model.setGradeSelected(grade.id, selected) },
            )
        }
    }
}

@Composable
private fun GradeRow(
    grade: Grade,
    courseType: CourseType?,
    selectionMode: Boolean,
    selectedForCalculation: Boolean,
    selectedForDetails: Boolean,
    onOpen: () -> Unit,
    onSelectionChange: (Boolean) -> Unit,
) {
    ElevatedCard(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (selectedForDetails) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        grade.displayCourseName(),
                        modifier = Modifier.weight(1f, fill = false),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    // 性质未同步（null）或未知的课程不显示标签，避免把映射缺失误读成任选。
                    if (courseType != null && courseType != CourseType.UNKNOWN) {
                        val colors = courseTypeColors(courseType)
                        Surface(
                            color = colors.container,
                            contentColor = colors.onContainer,
                            shape = RoundedCornerShape(6.dp),
                        ) {
                            Text(
                                courseType.displayName(),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
                Text(
                    "${grade.semester} · ${grade.courseTeacher.ifBlank { "教师信息未提供" }}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "学分 ${grade.courseCredits}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                grade.courseScore,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = gradeScoreColor(grade.courseScore),
            )
            if (selectionMode) {
                Checkbox(
                    checked = selectedForCalculation,
                    onCheckedChange = onSelectionChange,
                    modifier = Modifier.size(48.dp).semantics {
                        contentDescription = "选择${grade.displayCourseName()}用于计算"
                    },
                )
            }
        }
    }
}

@Composable
private fun GradeDetailPanel(grade: Grade?, modifier: Modifier) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant.accessibleAlpha(0.54f),
        shape = RoundedCornerShape(22.dp),
    ) {
        if (grade == null) {
            Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(
                    "选择一门课程查看详情",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            GradeDetailContent(
                grade = grade,
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp),
            )
        }
    }
}

@Composable
private fun GradeDetailContent(grade: Grade, modifier: Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            "成绩详情",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            grade.displayCourseName(),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        DetailLine("学期", grade.semester)
        DetailLine("教师", grade.courseTeacher.ifBlank { "未提供" })
        DetailLine("学分", grade.courseCredits)
        DetailLine("成绩", grade.courseScore)
        if (grade.detail.isNotBlank()) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(14.dp),
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                    Text("组成与说明", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text(grade.detail, style = MaterialTheme.typography.bodyMedium)
                }
            }
        } else {
            Text(
                "教务系统未提供更多组成信息。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            label,
            modifier = Modifier.width(48.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun GradeFailureBanner(
    failure: GradeSyncFailure,
    hasContent: Boolean,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    AppErrorBanner(
        message = when (failure) {
            GradeSyncFailure.NETWORK -> if (hasContent) {
                "同步失败，正在显示本地缓存。"
            } else {
                "无法连接教务系统，请检查网络后重试。"
            }
            GradeSyncFailure.SESSION_EXPIRED -> "教务会话已失效，请退出后重新登录。"
            GradeSyncFailure.MALFORMED_RESPONSE -> "教务成绩页面结构已变化，暂时无法解析。"
            GradeSyncFailure.CACHE -> "本地成绩缓存操作失败，当前选择可能未保存。"
        },
        onRetry = if (failure != GradeSyncFailure.CACHE) onRetry else null,
        onDismiss = onDismiss,
    )
}

@Composable
private fun GradeLoadingState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator()
            Text("正在读取本地成绩并连接教务系统…")
        }
    }
}

@Composable
private fun GradeEmptyState(onRefresh: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("暂无成绩", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                "本地没有缓存，教务系统也没有返回可显示的成绩。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onRefresh) { Text("重新同步") }
        }
    }
}

@Composable
private fun gradeScoreColor(score: String): Color = when (val numeric = scoreForSorting(score)) {
    in 60..100 -> MaterialTheme.colorScheme.primary
    in 0..59 -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}
