package team.bjtuss.bjtuselfservice.shared.feature.grade

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import team.bjtuss.bjtuselfservice.shared.PlatformFamily
import team.bjtuss.bjtuselfservice.shared.PlatformInfo
import team.bjtuss.bjtuselfservice.shared.WindowClass
import team.bjtuss.bjtuselfservice.shared.accessibleAlpha
import team.bjtuss.bjtuselfservice.shared.usesLegacySmartTransportFor
import team.bjtuss.bjtuselfservice.shared.auth.StudentProfile
import team.bjtuss.bjtuselfservice.shared.cache.AppPreferences
import team.bjtuss.bjtuselfservice.shared.data.grade.GradeSyncFailure
import team.bjtuss.bjtuselfservice.shared.data.home.HomeChangeFeedRepository
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
import team.bjtuss.bjtuselfservice.shared.feature.shell.LegacySmartTransportWarning
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

private enum class AppSection(val title: String) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthenticatedAppShell(
    profile: StudentProfile,
    platform: PlatformInfo,
    windowClass: WindowClass,
    gradeModel: GradeScreenModel,
    courseScheduleModel: CourseScheduleScreenModel,
    examScheduleModel: ExamScheduleScreenModel,
    homeworkModel: HomeworkScreenModel,
    coursewareModel: CoursewareScreenModel,
    otherFunctionModel: OtherFunctionScreenModel,
    classroomModel: ClassroomScreenModel,
    settingsModel: SettingsScreenModel,
    loginSyncPreferences: AppPreferences,
    mailboxModel: MailboxScreenModel,
    homeModel: HomeScreenModel,
    homeChangeFeed: HomeChangeFeedRepository,
    homeworkFileGateway: HomeworkFileGateway,
    coursewareDirectoryGateway: CoursewareDirectoryGateway,
    appCommandBus: AppCommandBus?,
    // 静默自动登录进行中：主界面已可见但会话尚未就绪，顶栏显示“登录中”，数据初始化延后到登录完成。
    entryLoggingIn: Boolean = false,
    onLogout: () -> Unit,
) {
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

    // 页面导航：NavHost 承载全部页面。一级页走标准 tab 模式（切走保存、切回恢复状态），
    // “更多”子页压栈 push：顶栏返回箭头或系统返回（Android 预测性返回）pop 回上一级。
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val section: AppSection = backStackEntry?.destination?.route
        ?.let { route -> AppSection.entries.firstOrNull { it.name == route } }
        ?: AppSection.HOME
    val moreSubPageNames = MoreGroupSections
        .filter { it != AppSection.MORE }
        .map { it.name }
        .toSet()
    val navigateToSection: (AppSection) -> Unit = { target ->
        navController.navigate(target.name) {
            if (target in MoreGroupSections && target != AppSection.MORE) {
                // 二级页：压栈，返回时 pop。
                launchSingleTop = true
            } else {
                // 一级页：标准底部导航模式，切换后保留各自页面状态。
                popUpTo(AppSection.HOME.name) { saveState = true }
                launchSingleTop = true
                restoreState = true
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
                courseScheduleModel.initialize(loginSyncPreferences.autoSyncSchedule)
                if (loginSyncPreferences.autoSyncSchedule && courseScheduleModel.state.value.failure != null) {
                    delay(LOGIN_SYNC_RETRY_DELAY_MILLIS)
                    courseScheduleModel.refresh()
                }
            }
        }
    }

    // 所有页面的 NavHost 目的地注册：compact/expanded 两套布局共用，
    // 差异只有 expanded 标志、明文通道开关和占位 modifier。
    fun NavGraphBuilder.sectionDestinations(
        expanded: Boolean,
        modifier: Modifier,
        usesLegacySmartTransport: Boolean,
    ) {
        composable(AppSection.HOME.name) {
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
                modifier = modifier,
            )
        }
        composable(AppSection.GRADES.name) {
            GradeWorkspace(
                state = gradeState,
                expanded = expanded,
                model = gradeModel,
                onRefresh = refresh,
                modifier = modifier,
            )
        }
        composable(AppSection.SCHEDULE.name) {
            CourseScheduleWorkspace(
                state = courseState,
                expanded = expanded,
                model = courseScheduleModel,
                onRefresh = refresh,
                modifier = modifier,
            )
        }
        composable(AppSection.EXAMS.name) {
            ExamScheduleWorkspace(
                state = examState,
                expanded = expanded,
                model = examScheduleModel,
                onRefresh = refresh,
                modifier = modifier,
            )
        }
        composable(AppSection.HOMEWORK.name) {
            HomeworkWorkspace(
                state = homeworkState,
                expanded = expanded,
                usesLegacySmartTransport = usesLegacySmartTransport,
                model = homeworkModel,
                fileGateway = homeworkFileGateway,
                onRefresh = refresh,
                modifier = modifier,
            )
        }
        composable(AppSection.COURSEWARE.name) {
            CoursewareWorkspace(
                state = coursewareState,
                expanded = expanded,
                usesLegacySmartTransport = usesLegacySmartTransport,
                model = coursewareModel,
                fileGateway = homeworkFileGateway,
                directoryGateway = coursewareDirectoryGateway,
                onRefresh = refresh,
                modifier = modifier,
            )
        }
        composable(AppSection.CALENDAR_DOWNLOAD.name) {
            CalendarDownloadWorkspace(
                model = otherFunctionModel,
                expanded = expanded,
                modifier = modifier,
            )
        }
        composable(AppSection.REPORT_CARD_DOWNLOAD.name) {
            ReportCardDownloadWorkspace(
                model = otherFunctionModel,
                expanded = expanded,
                modifier = modifier,
            )
        }
        composable(AppSection.CLASSROOMS.name) {
            ClassroomWorkspace(
                model = classroomModel,
                expanded = expanded,
                modifier = modifier,
            )
        }
        composable(AppSection.SETTINGS.name) {
            SettingsWorkspace(
                model = settingsModel,
                accountName = "${profile.name} · ${profile.studentId}",
                platform = platform,
                expanded = expanded,
                onLogout = onLogout,
                modifier = modifier,
            )
        }
        composable(AppSection.MAILBOX.name) {
            MailboxWorkspace(
                model = mailboxModel,
                platform = platform,
                expanded = expanded,
                modifier = modifier,
            )
        }
        composable(AppSection.MORE.name) {
            MoreWorkspace(
                onOpenSection = navigateToSection,
                modifier = modifier,
            )
        }
    }

    if (windowClass == WindowClass.Expanded) {
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
            // 宽屏侧栏布局页面并列，切换不播放 push/pop 动画。
            NavHost(
                navController = navController,
                startDestination = AppSection.HOME.name,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                enterTransition = { EnterTransition.None },
                exitTransition = { ExitTransition.None },
                popEnterTransition = { EnterTransition.None },
                popExitTransition = { ExitTransition.None },
            ) {
                sectionDestinations(
                    expanded = true,
                    modifier = Modifier.fillMaxSize(),
                    usesLegacySmartTransport = usesLegacySmartTransportFor(platform.family),
                )
            }
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            // “更多”子页是二级页面：顶栏显示返回箭头，同时隐藏底部导航，
            // 明确层级关系（对应 iOS 的 push 后 tab bar 收起）。
            val isMoreSubPage = section in MoreGroupSections && section != AppSection.MORE
            val isRefreshing = when (section) {
                AppSection.HOME -> homeState.isRefreshing || homeworkState.isRefreshing ||
                    examState.isRefreshing || courseState.isRefreshing
                AppSection.GRADES -> gradeState.isRefreshing
                AppSection.SCHEDULE -> courseState.isRefreshing
                AppSection.EXAMS -> examState.isRefreshing
                AppSection.HOMEWORK -> homeworkState.isRefreshing
                AppSection.COURSEWARE -> coursewareState.isRefreshing
                AppSection.CLASSROOMS -> classroomState.isLoading
                AppSection.MAILBOX -> mailboxState == MailboxUiState.Preparing
                AppSection.CALENDAR_DOWNLOAD -> false
                AppSection.REPORT_CARD_DOWNLOAD -> false
                AppSection.SETTINGS -> false
                AppSection.MORE -> false
            }
            CompactAppTopBar(
                title = section.title,
                isRefreshing = isRefreshing,
                platformFamily = platform.family,
                isLoggingIn = entryLoggingIn,
                // “更多”的子页左上角显示返回箭头，pop 回到上一级。
                onBack = if (isMoreSubPage) {
                    { navController.popBackStack() }
                } else {
                    null
                },
            )
            // 明文通道提示只在与作业/课件相关的页面出现，且每次登录会话内只需确认一次。
            if (
                usesLegacySmartTransportFor(platform.family) &&
                (section == AppSection.HOMEWORK || section == AppSection.COURSEWARE) &&
                !legacyWarningDismissed
            ) {
                LegacySmartTransportWarning(
                    onDismiss = { legacyWarningDismissed = true },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            // 邮箱页是全屏 WebView，下拉手势应留给网页；“设置/下载/更多”没有可刷新内容。
            val pullRefreshEnabled = section != AppSection.SETTINGS &&
                section != AppSection.MAILBOX &&
                section != AppSection.CALENDAR_DOWNLOAD &&
                section != AppSection.REPORT_CARD_DOWNLOAD &&
                section != AppSection.MORE
            // 页面过渡：进入二级页从右滑入，返回向右滑出（iOS push/pop 观感）；
            // Android 的系统返回由 Navigation 接管，自动获得预测性返回动画。
            val navHostContent: @Composable () -> Unit = {
                NavHost(
                    navController = navController,
                    startDestination = AppSection.HOME.name,
                    modifier = Modifier.fillMaxSize(),
                    enterTransition = {
                        val initialRoute = initialState.destination.route
                        val targetRoute = targetState.destination.route
                        val initialIsSub = initialRoute != null && moreSubPageNames.contains(initialRoute)
                        val targetIsSub = targetRoute != null && moreSubPageNames.contains(targetRoute)
                        when {
                            targetIsSub && !initialIsSub ->
                                slideInHorizontally(animationSpec = tween(300), initialOffsetX = { it }) +
                                    fadeIn(animationSpec = tween(300))
                            initialIsSub -> fadeIn(animationSpec = tween(200))
                            else -> fadeIn(animationSpec = tween(160))
                        }
                    },
                    exitTransition = {
                        val initialRoute = initialState.destination.route
                        val initialIsSub = initialRoute != null && moreSubPageNames.contains(initialRoute)
                        if (initialIsSub) {
                            fadeOut(animationSpec = tween(220))
                        } else {
                            fadeOut(animationSpec = tween(160))
                        }
                    },
                    popEnterTransition = { fadeIn(animationSpec = tween(200)) },
                    popExitTransition = {
                        val initialRoute = initialState.destination.route
                        val initialIsSub = initialRoute != null && moreSubPageNames.contains(initialRoute)
                        if (initialIsSub) {
                            slideOutHorizontally(animationSpec = tween(300), targetOffsetX = { it }) +
                                fadeOut(animationSpec = tween(300))
                        } else {
                            fadeOut(animationSpec = tween(160))
                        }
                    },
                ) {
                    sectionDestinations(
                        expanded = false,
                        modifier = Modifier.fillMaxSize(),
                        usesLegacySmartTransport = false,
                    )
                }
            }
            if (pullRefreshEnabled) {
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = refresh,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                ) {
                    navHostContent()
                }
            } else {
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    navHostContent()
                }
            }
            // 二级页隐藏底栏，表明处于更深层级；不额外保留底部安全区（实测各平台无遮挡）。
            if (!isMoreSubPage) {
                CompactBottomNavigation(
                    section = section,
                    onSectionSelected = navigateToSection,
                )
            }
        }
    }
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
                    "作业与课件平台只提供旧明文通道，已按你的授权连接；会话可能被同一网络中的第三方窃听或篡改。",
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
    platformFamily: PlatformFamily,
    isLoggingIn: Boolean = false,
    onBack: (() -> Unit)? = null,
) {
    // 顶栏与页面背景同色：iOS 的 SwiftUI 根视图在状态栏下方铺的就是 background，
    // 顶栏若用 surface 会在状态栏下方露出一条浅色带子，破坏沉浸感。
    // iOS 的 Compose 宿主本身已位于状态栏下方，再叠加 statusBarsPadding 会重复
    // 计算安全区，导致标题与状态栏间距异常（2026-08-04 真机反馈）。
    val statusBarInset = if (platformFamily == PlatformFamily.IOS) {
        Modifier
    } else {
        Modifier.statusBarsPadding()
    }
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
            // “登录中”优先于“同步中”：静默自动登录完成后才会开始数据同步。
            val statusText = when {
                isLoggingIn -> "登录中"
                isRefreshing -> "同步中"
                else -> null
            }
            if (statusText != null) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    statusText,
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
) {
    NavigationBar {
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

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun GradeWorkspace(
    state: GradeUiState,
    expanded: Boolean,
    model: GradeScreenModel,
    onRefresh: () -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier = if (expanded) {
            modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        } else {
            modifier.padding(horizontal = 16.dp).padding(top = 14.dp)
        },
        verticalArrangement = Arrangement.spacedBy(12.dp),
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
                GradeSummaryCard(state)
                GradeControls(state, model)
                if (state.selectionMode) {
                    GradeSelectionActions(state, model)
                }
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
}

@Composable
private fun GradeSummaryCard(state: GradeUiState) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = RoundedCornerShape(20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    when (val info = state.gradeInfo) {
                        GradeInfoResult.NoGrades -> if (state.selectionMode) {
                            "请选择用于计算的课程"
                        } else {
                            "暂无可计算成绩"
                        }
                        is GradeInfoResult.Calculated -> info.formattedMessage
                    },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    when {
                        state.selectionMode -> "已选 ${state.selectedGradeIds.size} 门 · 共 ${state.grades.size} 门"
                        state.selectedSemesters.isNotEmpty() -> "已筛选 ${state.selectedSemesters.size} 个学期 · 显示 ${state.visibleGrades.size} 门"
                        else -> "共 ${state.grades.size} 门课程"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.accessibleAlpha(0.78f),
                )
            }
            Surface(
                color = MaterialTheme.colorScheme.surface.accessibleAlpha(0.86f),
                shape = RoundedCornerShape(999.dp),
            ) {
                Text(
                    when {
                        state.isRefreshing -> "正在同步"
                        state.source == GradeContentSource.CACHE -> "本地缓存"
                        state.source == GradeContentSource.NETWORK -> "已同步"
                        else -> "尚未同步"
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GradeControls(state: GradeUiState, model: GradeScreenModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("学期", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            if (state.selectedSemesters.isNotEmpty()) {
                TextButton(onClick = model::clearSemesterFilter) { Text("显示全部") }
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.semesterOptions.forEach { semester ->
                FilterChip(
                    selected = semester in state.selectedSemesters,
                    onClick = { model.toggleSemester(semester) },
                    label = { Text(semester) },
                )
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilledTonalButton(onClick = model::cycleSortOrder) {
                Text(
                    when (state.sortOrder) {
                        GradeSortOrder.ORIGINAL -> "排序：原始"
                        GradeSortOrder.ASCENDING -> "排序：分数升序 ↑"
                        GradeSortOrder.DESCENDING -> "排序：分数降序 ↓"
                    },
                )
            }
            Button(onClick = model::toggleSelectionMode) {
                Text(if (state.selectionMode) "退出自选课程" else "自选课程计算")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GradeSelectionActions(state: GradeUiState, model: GradeScreenModel) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.accessibleAlpha(0.72f),
        shape = RoundedCornerShape(16.dp),
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = model::selectAllVisible) { Text("全选当前") }
            if (state.selectedSemesters.isNotEmpty()) {
                OutlinedButton(onClick = model::clearSelectedSemesters) { Text("清空所选学期") }
            }
            OutlinedButton(onClick = model::clearAllSelections) { Text("全部清空") }
            // 按课程性质批量选择/排除，三态 chip：全选（selected 样式）/ 部分选中
            // （tertiaryContainer + 已选/总数）/ 未选中（默认样式）。
            // 点击行为：全选状态 → 取消该性质全部；其余状态 → 全选该性质。
            // “其他类别”覆盖性质未知（UNKNOWN）的课程，避免已选未知课程没有入口取消。
            listOf(
                CourseType.REQUIRED to "必修",
                CourseType.LIMITED to "限选",
                CourseType.ELECTIVE to "任选",
                CourseType.PHYSICAL_EDUCATION to "体育",
                CourseType.UNKNOWN to "其他类别",
            ).forEach { (type, label) ->
                val count = state.courseTypeCounts[type] ?: 0
                if (count > 0) {
                    val selectionState = state.selectionStateForType(type)
                    val partial = selectionState == CourseTypeSelectionState.PARTIAL
                    FilterChip(
                        selected = selectionState == CourseTypeSelectionState.ALL,
                        onClick = {
                            if (selectionState == CourseTypeSelectionState.ALL) {
                                model.deselectByType(type)
                            } else {
                                model.selectAllByType(type)
                            }
                        },
                        colors = if (partial) {
                            FilterChipDefaults.filterChipColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                labelColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                        } else {
                            FilterChipDefaults.filterChipColors()
                        },
                        label = {
                            Text(
                                if (partial) {
                                    val selectedCount = state.grades.count { grade ->
                                        state.courseTypeOf(grade) == type &&
                                            grade.id in state.selectedGradeIds
                                    }
                                    "$label $selectedCount/$count"
                                } else {
                                    "$label $count"
                                },
                            )
                        },
                    )
                }
            }
        }
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
    courseType: CourseType,
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
                    // 性质未知的课程不显示标签，避免把映射缺失误读成任选。
                    if (courseType != CourseType.UNKNOWN) {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
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
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                when (failure) {
                    GradeSyncFailure.NETWORK -> if (hasContent) {
                        "同步失败，正在显示本地缓存。"
                    } else {
                        "无法连接教务系统，请检查网络后重试。"
                    }
                    GradeSyncFailure.SESSION_EXPIRED -> "教务会话已失效，请退出后重新登录。"
                    GradeSyncFailure.MALFORMED_RESPONSE -> "教务成绩页面结构已变化，暂时无法解析。"
                    GradeSyncFailure.CACHE -> "本地成绩缓存操作失败，当前选择可能未保存。"
                },
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (failure != GradeSyncFailure.CACHE) {
                TextButton(onClick = onRetry) { Text("重试") }
            }
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    }
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
