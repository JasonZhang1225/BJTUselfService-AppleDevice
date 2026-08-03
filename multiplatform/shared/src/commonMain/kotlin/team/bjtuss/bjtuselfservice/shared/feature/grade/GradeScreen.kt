package team.bjtuss.bjtuselfservice.shared.feature.grade

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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import team.bjtuss.bjtuselfservice.shared.PlatformInfo
import team.bjtuss.bjtuselfservice.shared.PlatformFamily
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
import team.bjtuss.bjtuselfservice.shared.feature.otherfunction.OtherFunctionWorkspace
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
import team.bjtuss.bjtuselfservice.shared.domain.grade.Grade
import team.bjtuss.bjtuselfservice.shared.domain.grade.GradeInfoResult
import team.bjtuss.bjtuselfservice.shared.domain.grade.GradeSortOrder
import team.bjtuss.bjtuselfservice.shared.domain.grade.displayCourseName
import team.bjtuss.bjtuselfservice.shared.domain.grade.scoreForSorting
import team.bjtuss.bjtuselfservice.shared.domain.home.HomeChangeDomain

private enum class AppSection(val title: String) {
    HOME("首页"),
    GRADES("成绩"),
    SCHEDULE("课程表"),
    EXAMS("考试安排"),
    HOMEWORK("作业"),
    COURSEWARE("课件"),
    OTHERS("其他功能"),
    CLASSROOMS("教室"),
    MAILBOX("邮箱"),
    SETTINGS("设置"),
}

private const val LOGIN_SYNC_RETRY_DELAY_MILLIS = 700L

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
    var section by remember { mutableStateOf(AppSection.HOME) }
    val scope = rememberCoroutineScope()
    val refresh: () -> Unit = {
        scope.launch {
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
                AppSection.OTHERS -> Unit
                AppSection.CLASSROOMS -> classroomModel.refresh()
                AppSection.MAILBOX -> mailboxModel.refresh()
                AppSection.SETTINGS -> Unit
            }
        }
    }

    LaunchedEffect(appCommandBus) {
        appCommandBus?.commands?.collect { command ->
            when (command) {
                AppCommand.NAVIGATE_HOME -> section = AppSection.HOME
                AppCommand.NAVIGATE_GRADES -> section = AppSection.GRADES
                AppCommand.NAVIGATE_SCHEDULE -> section = AppSection.SCHEDULE
                AppCommand.NAVIGATE_EXAMS -> section = AppSection.EXAMS
                AppCommand.NAVIGATE_HOMEWORK -> section = AppSection.HOMEWORK
                AppCommand.NAVIGATE_COURSEWARE -> section = AppSection.COURSEWARE
                AppCommand.NAVIGATE_OTHERS -> section = AppSection.OTHERS
                AppCommand.NAVIGATE_CLASSROOMS -> section = AppSection.CLASSROOMS
                AppCommand.NAVIGATE_MAILBOX -> section = AppSection.MAILBOX
                AppCommand.NAVIGATE_SETTINGS -> section = AppSection.SETTINGS
                AppCommand.REFRESH_CURRENT -> refresh()
            }
        }
    }

    LaunchedEffect(gradeModel) {
        gradeModel.initialize(loginSyncPreferences.autoSyncGrades)
        if (loginSyncPreferences.autoSyncGrades && gradeModel.state.value.failure != null) {
            delay(LOGIN_SYNC_RETRY_DELAY_MILLIS)
            gradeModel.refresh()
        }
    }
    LaunchedEffect(homeworkModel, examScheduleModel, courseScheduleModel) {
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

    if (windowClass == WindowClass.Expanded) {
        Row(
            modifier = Modifier.fillMaxSize().padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            AppSidebar(
                profile = profile,
                platform = platform,
                section = section,
                onSectionSelected = { section = it },
                onLogout = onLogout,
                modifier = Modifier.width(236.dp).fillMaxHeight(),
            )
            when (section) {
                AppSection.HOME -> HomeWorkspace(
                    model = homeModel,
                    platform = platform,
                    expanded = true,
                    homework = homeworkState.homework,
                    exams = examState.exams,
                    currentWeek = courseState.currentWeek,
                    now = homeworkState.now,
                    timeZone = homeworkState.timeZone,
                    isAgendaLoading = homeworkState.isLoading || examState.isLoading || courseState.isLoading,
                    isRefreshing = homeState.isRefreshing || homeworkState.isRefreshing ||
                        examState.isRefreshing || courseState.isRefreshing,
                    onRefresh = refresh,
                    onOpenMailbox = { section = AppSection.MAILBOX },
                    onOpenHomework = { section = AppSection.HOMEWORK },
                    onOpenExams = { section = AppSection.EXAMS },
                    changes = homeChanges,
                    onClearAllChanges = { scope.launch { homeChangeFeed.clear() } },
                    onClearChangeDomain = { domain -> scope.launch { homeChangeFeed.clear(domain) } },
                    onOpenChangeDomain = { domain -> section = domain.toAppSection() },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                AppSection.GRADES -> GradeWorkspace(
                    state = gradeState,
                    expanded = true,
                    model = gradeModel,
                    onRefresh = refresh,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                AppSection.SCHEDULE -> CourseScheduleWorkspace(
                    state = courseState,
                    expanded = true,
                    model = courseScheduleModel,
                    onRefresh = refresh,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                AppSection.EXAMS -> ExamScheduleWorkspace(
                    state = examState,
                    expanded = true,
                    model = examScheduleModel,
                    onRefresh = refresh,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                AppSection.HOMEWORK -> HomeworkWorkspace(
                    state = homeworkState,
                    expanded = true,
                    usesLegacySmartTransport = usesLegacySmartTransportFor(platform.family),
                    model = homeworkModel,
                    fileGateway = homeworkFileGateway,
                    onRefresh = refresh,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                AppSection.COURSEWARE -> CoursewareWorkspace(
                    state = coursewareState,
                    expanded = true,
                    usesLegacySmartTransport = usesLegacySmartTransportFor(platform.family),
                    model = coursewareModel,
                    fileGateway = homeworkFileGateway,
                    directoryGateway = coursewareDirectoryGateway,
                    onRefresh = refresh,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                AppSection.OTHERS -> OtherFunctionWorkspace(
                    model = otherFunctionModel,
                    expanded = true,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                AppSection.CLASSROOMS -> ClassroomWorkspace(
                    model = classroomModel,
                    expanded = true,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                AppSection.SETTINGS -> SettingsWorkspace(
                    model = settingsModel,
                    accountName = "${profile.name} · ${profile.studentId}",
                    platform = platform,
                    expanded = true,
                    onLogout = onLogout,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                AppSection.MAILBOX -> MailboxWorkspace(
                    model = mailboxModel,
                    platform = platform,
                    expanded = true,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            CompactAppTopBar(
                title = section.title,
                platform = platform,
                isRefreshing = when (section) {
                    AppSection.HOME -> homeState.isRefreshing || homeworkState.isRefreshing ||
                        examState.isRefreshing || courseState.isRefreshing
                    AppSection.GRADES -> gradeState.isRefreshing
                    AppSection.SCHEDULE -> courseState.isRefreshing
                    AppSection.EXAMS -> examState.isRefreshing
                    AppSection.HOMEWORK -> homeworkState.isRefreshing
                    AppSection.COURSEWARE -> coursewareState.isRefreshing
                    AppSection.OTHERS -> false
                    AppSection.CLASSROOMS -> classroomState.isLoading
                    AppSection.MAILBOX -> mailboxState == MailboxUiState.Preparing
                    AppSection.SETTINGS -> false
                },
                onRefresh = if (section == AppSection.OTHERS || section == AppSection.SETTINGS) null else refresh,
                onLogout = onLogout,
            )
            CompactSectionSwitcher(
                section = section,
                onSectionSelected = { section = it },
            )
            if (usesLegacySmartTransportFor(platform.family)) {
                LegacySmartTransportWarning(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            when (section) {
                AppSection.HOME -> HomeWorkspace(
                    model = homeModel,
                    platform = platform,
                    expanded = false,
                    homework = homeworkState.homework,
                    exams = examState.exams,
                    currentWeek = courseState.currentWeek,
                    now = homeworkState.now,
                    timeZone = homeworkState.timeZone,
                    isAgendaLoading = homeworkState.isLoading || examState.isLoading || courseState.isLoading,
                    isRefreshing = homeState.isRefreshing || homeworkState.isRefreshing ||
                        examState.isRefreshing || courseState.isRefreshing,
                    onRefresh = refresh,
                    onOpenMailbox = { section = AppSection.MAILBOX },
                    onOpenHomework = { section = AppSection.HOMEWORK },
                    onOpenExams = { section = AppSection.EXAMS },
                    changes = homeChanges,
                    onClearAllChanges = { scope.launch { homeChangeFeed.clear() } },
                    onClearChangeDomain = { domain -> scope.launch { homeChangeFeed.clear(domain) } },
                    onOpenChangeDomain = { domain -> section = domain.toAppSection() },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
                AppSection.GRADES -> GradeWorkspace(
                    state = gradeState,
                    expanded = false,
                    model = gradeModel,
                    onRefresh = refresh,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
                AppSection.SCHEDULE -> CourseScheduleWorkspace(
                    state = courseState,
                    expanded = false,
                    model = courseScheduleModel,
                    onRefresh = refresh,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
                AppSection.EXAMS -> ExamScheduleWorkspace(
                    state = examState,
                    expanded = false,
                    model = examScheduleModel,
                    onRefresh = refresh,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
                AppSection.HOMEWORK -> HomeworkWorkspace(
                    state = homeworkState,
                    expanded = false,
                    usesLegacySmartTransport = false,
                    model = homeworkModel,
                    fileGateway = homeworkFileGateway,
                    onRefresh = refresh,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
                AppSection.COURSEWARE -> CoursewareWorkspace(
                    state = coursewareState,
                    expanded = false,
                    usesLegacySmartTransport = false,
                    model = coursewareModel,
                    fileGateway = homeworkFileGateway,
                    directoryGateway = coursewareDirectoryGateway,
                    onRefresh = refresh,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
                AppSection.OTHERS -> OtherFunctionWorkspace(
                    model = otherFunctionModel,
                    expanded = false,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
                AppSection.CLASSROOMS -> ClassroomWorkspace(
                    model = classroomModel,
                    expanded = false,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
                AppSection.SETTINGS -> SettingsWorkspace(
                    model = settingsModel,
                    accountName = "${profile.name} · ${profile.studentId}",
                    platform = platform,
                    expanded = false,
                    onLogout = onLogout,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
                AppSection.MAILBOX -> MailboxWorkspace(
                    model = mailboxModel,
                    platform = platform,
                    expanded = false,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
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
                "${platform.displayName} · KMP M5",
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
                AppSection.entries.forEach { item ->
                    AppSidebarItem(
                        title = item.title,
                        selected = section == item,
                        onClick = { onSectionSelected(item) },
                    )
                }
                Text(
                    if (platform.family == PlatformFamily.MacOS) {
                        "macOS 已启用旧明文通道；作业与课件会话可能被窃听或篡改。"
                    } else {
                        "作业与课件平台若只提供旧明文通道，当前平台会停止连接，不发送登录会话。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (platform.family == PlatformFamily.MacOS) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
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
    platform: PlatformInfo,
    isRefreshing: Boolean,
    onRefresh: (() -> Unit)?,
    onLogout: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "${platform.displayName} · KMP M5",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (onRefresh != null) {
                TextButton(onClick = onRefresh, enabled = !isRefreshing) {
                    Text(if (isRefreshing) "同步中" else "刷新")
                }
            }
            TextButton(onClick = onLogout) { Text("退出") }
        }
    }
}

@Composable
private fun CompactSectionSwitcher(
    section: AppSection,
    onSectionSelected: (AppSection) -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        AppSection.entries.forEach { item ->
            FilterChip(
                selected = section == item,
                onClick = { onSectionSelected(item) },
                label = { Text(item.title) },
            )
        }
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
                Text(
                    grade.displayCourseName(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
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
