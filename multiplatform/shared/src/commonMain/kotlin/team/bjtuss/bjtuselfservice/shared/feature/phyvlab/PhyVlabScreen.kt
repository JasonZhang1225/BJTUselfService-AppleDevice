package team.bjtuss.bjtuselfservice.shared.feature.phyvlab

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import team.bjtuss.bjtuselfservice.shared.accessibleAlpha
import team.bjtuss.bjtuselfservice.shared.data.phyvlab.PhyVlabSyncFailure
import team.bjtuss.bjtuselfservice.shared.domain.phyvlab.PhyVlabActivity
import team.bjtuss.bjtuselfservice.shared.domain.phyvlab.PhyVlabCourse
import team.bjtuss.bjtuselfservice.shared.domain.phyvlab.PhyVlabEvent
import team.bjtuss.bjtuselfservice.shared.feature.scroll.desktopTouchScroll
import team.bjtuss.bjtuselfservice.shared.domain.homework.HomeworkFileContent
import team.bjtuss.bjtuselfservice.shared.files.HomeworkFileGateway
import team.bjtuss.bjtuselfservice.shared.files.HomeworkFilePickResult
import team.bjtuss.bjtuselfservice.shared.files.UnavailableHomeworkFileGateway

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhyVlabWorkspace(
    model: PhyVlabScreenModel,
    modifier: Modifier = Modifier,
    // 静默自动登录期间不能抢先用空的 Ktor Cookie jar 建立 Moodle 会话。
    holdNetwork: Boolean = false,
    fileGateway: HomeworkFileGateway = UnavailableHomeworkFileGateway,
    onOpenCourse: (String) -> Unit = {},
    onOpenActivity: (String) -> Unit = {},
    onOpenEvent: (String) -> Unit = {},
    onLogout: () -> Unit = {},
) {
    val state by model.state.collectAsState()
    val scope = rememberCoroutineScope()
    var showUpload by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var showUploadConfirm by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var uploadFiles by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<List<HomeworkFileContent>>(emptyList()) }
    var uploadFeedback by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    var activityOrderDescending by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(true) }
    val listState = rememberLazyListState()
    val displayedActivities = orderPhyVlabActivities(state.activities, activityOrderDescending)

    LaunchedEffect(model, holdNetwork) {
        if (!holdNetwork) model.initialize()
    }
    LaunchedEffect(state.selectedCourse) {
        state.selectedCourse?.let { model.loadSelectedActivities() }
    }
    LaunchedEffect(state.selectedActivity) {
        if (state.selectedActivity != null) model.loadSelectedActivityDetail()
    }
    LaunchedEffect(state.selectedActivity) {
        showUpload = false
        showUploadConfirm = false
        uploadFiles = emptyList()
        uploadFeedback = null
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (holdNetwork) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CircularProgressIndicator()
                    Text("正在完成统一身份认证…", style = MaterialTheme.typography.bodyMedium)
                }
            }
            return
        }
        if (state.isLoading && state.courses.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return
        }
        state.failure?.let {
            PhyVlabFailureBanner(
                failure = it,
                detail = state.failureDetail,
                onRetry = { scope.launch { model.refresh() } },
            )
        }
        if (state.casLoginRequired) {
            PhyVlabCasLoginRequiredState(
                onRetry = { scope.launch { model.refresh() } },
                onLogout = onLogout,
            )
            return
        }
        if (state.courses.isEmpty()) {
            PhyVlabEmptyState(
                onRetry = { scope.launch { model.refresh() } },
                onOpenWeb = { onOpenCourse("https://phyvlab.bjtu.edu.cn/my/courses.php") },
            )
            return
        }
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .desktopTouchScroll(listState),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "schedule") {
                PhyVlabScheduleCard(state = state, onPrev = { scope.launch { model.changeMonth(-1) } }, onNext = { scope.launch { model.changeMonth(1) } })
            }
            items(state.events, key = { "event-${it.id}" }) { event ->
                PhyVlabEventRow(event = event, onOpen = { event.eventUrl?.let(onOpenEvent) })
            }
            item(key = "courses") {
                Text("我的课程", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            items(state.courses, key = { "course-${it.id}" }) { course ->
                PhyVlabCourseRow(course = course, selected = course.id == state.selectedCourse?.id) {
                    model.selectCourse(course)
                }
            }
            item(key = "activities") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "课程作业",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { activityOrderDescending = !activityOrderDescending }) {
                        Text(if (activityOrderDescending) "最新在前" else "最旧在前")
                    }
                }
            }
            items(displayedActivities, key = { "activity-${it.id}" }) { activity ->
                PhyVlabActivityRow(activity = activity, onOpen = { model.showActivityDetails(activity) })
            }
        }
    }

    state.selectedActivity?.let { activity ->
        val detailSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = model::dismissActivityDetails,
            sheetState = detailSheetState,
            sheetGesturesEnabled = true,
            contentWindowInsets = { androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0) },
        ) {
            PhyVlabAssignmentDetailContent(
                activity = activity,
                detail = state.assignmentDetail,
                isLoading = state.isDetailLoading,
                failure = state.detailFailure,
                feedback = state.submissionFeedback,
                fileGatewayAvailable = fileGateway.isAvailable,
                onRetry = { scope.launch { model.loadSelectedActivityDetail(force = true) } },
                onUpload = {
                    uploadFiles = emptyList()
                    uploadFeedback = null
                    showUpload = true
                },
                onOpenWeb = { onOpenActivity(activity.activityUrl) },
            )
        }
    }

    if (showUpload) {
        PhyVlabUploadDialog(
            files = uploadFiles,
            feedback = uploadFeedback,
            isSubmitting = state.isSubmitting,
            onPickFiles = {
                scope.launch {
                    when (val result = fileGateway.pickFiles()) {
                        HomeworkFilePickResult.Cancelled -> Unit
                        is HomeworkFilePickResult.Failed -> uploadFeedback = "无法读取所选文件，请重新选择。"
                        is HomeworkFilePickResult.Selected -> {
                            uploadFiles = (uploadFiles + result.files).distinctBy { it.fileName }
                            uploadFeedback = null
                        }
                    }
                }
            },
            onRemoveFile = { index -> uploadFiles = uploadFiles.filterIndexed { i, _ -> i != index } },
            onSubmit = { showUploadConfirm = true },
            onDismiss = { if (!state.isSubmitting) showUpload = false },
        )
    }

    if (showUploadConfirm) {
        AlertDialog(
            onDismissRequest = { if (!state.isSubmitting) showUploadConfirm = false },
            title = { Text("确认提交物理在线作业？") },
            text = { Text("将把已选择的 ${uploadFiles.size} 个文件提交到“${state.selectedActivity?.title.orEmpty()}”。提交后可在详情中查看最新状态。") },
            confirmButton = {
                Button(
                    onClick = {
                        showUploadConfirm = false
                        showUpload = false
                        scope.launch {
                            model.submitSelectedActivity(uploadFiles)
                        }
                    },
                    enabled = uploadFiles.isNotEmpty() && !state.isSubmitting,
                ) { Text("确认提交") }
            },
            dismissButton = { TextButton(onClick = { showUploadConfirm = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun PhyVlabScheduleCard(state: PhyVlabUiState, onPrev: () -> Unit, onNext: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.accessibleAlpha(0.55f),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("安排", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(
                    state.monthLabel.ifBlank { "本月" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 8.dp),
                )
                OutlinedButton(onClick = onPrev) { Text("上月") }
                OutlinedButton(onClick = onNext, modifier = Modifier.padding(start = 8.dp)) { Text("下月") }
            }
            if (state.events.isEmpty()) {
                Text("本月暂无安排", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun PhyVlabEventRow(event: PhyVlabEvent, onOpen: () -> Unit) {
    ElevatedCard(onClick = onOpen, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(event.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                Text(event.dateText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("打开", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun PhyVlabCourseRow(course: PhyVlabCourse, selected: Boolean, onSelect: () -> Unit) {
    Surface(
        onClick = onSelect,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(course.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                if (course.category.isNotBlank()) {
                    Text(course.category, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (course.progressPercent > 0) {
                    Text("进度 ${course.progressPercent}%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun PhyVlabActivityRow(activity: PhyVlabActivity, onOpen: () -> Unit) {
    ElevatedCard(onClick = onOpen, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(activity.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                activity.openText?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                activity.dueText?.let { Text("截止：$it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                Text(if (activity.completed) "已完成" else "未完成", style = MaterialTheme.typography.bodySmall, color = if (activity.completed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun PhyVlabAssignmentDetailContent(
    activity: PhyVlabActivity,
    detail: team.bjtuss.bjtuselfservice.shared.domain.phyvlab.PhyVlabAssignmentDetail?,
    isLoading: Boolean,
    failure: PhyVlabSyncFailure?,
    feedback: String?,
    fileGatewayAvailable: Boolean,
    onRetry: () -> Unit,
    onUpload: () -> Unit,
    onOpenWeb: () -> Unit,
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 680.dp)
            .verticalScroll(scrollState)
            .desktopTouchScroll(scrollState)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(activity.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(activity.courseName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        activity.openText?.let { Text("开放：$it", style = MaterialTheme.typography.bodySmall) }
        activity.dueText?.let { Text("截止：$it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        feedback?.let {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(it, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (isLoading && detail == null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator(modifier = Modifier.padding(2.dp))
                Text("正在读取提交与批改状态…")
            }
        }
        failure?.let {
            Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        when (it) {
                            PhyVlabSyncFailure.NETWORK -> "详情读取失败，请检查网络。"
                            PhyVlabSyncFailure.PARSE -> "详情页面结构变化，暂时无法读取。"
                            PhyVlabSyncFailure.SESSION_EXPIRED -> "物理在线会话已失效，请重新登录。"
                        },
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    TextButton(onClick = onRetry) { Text("重试") }
                }
            }
        }
        detail?.let { page ->
            PhyVlabDetailLine("提交状态", page.submissionStatus.ifBlank { "未提供" })
            page.submissionDateText?.let { PhyVlabDetailLine("提交时间", it) }
            page.gradingStatus?.let { PhyVlabDetailLine("批改状态", it) }
            PhyVlabDetailLine("批改成绩", page.gradeText?.ifBlank { "未批改" } ?: "未批改")
            if (!page.feedbackText.isNullOrBlank()) {
                PhyVlabDetailLine("教师评语", page.feedbackText)
            }
            if (page.description.isNotBlank()) {
                Text("作业要求", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(page.description, style = MaterialTheme.typography.bodyMedium)
            }
            if (page.submittedFiles.isNotEmpty()) {
                Text("已提交文件", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                page.submittedFiles.forEach { file ->
                    Text("• ${file.fileName}", style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (page.canSubmit) {
                Button(
                    onClick = onUpload,
                    enabled = fileGatewayAvailable,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (fileGatewayAvailable) "上传作业" else "当前平台未接入文件选择器")
                }
            }
        }
        TextButton(onClick = onOpenWeb, modifier = Modifier.fillMaxWidth()) { Text("在网页中打开（备用）") }
    }
}

@Composable
private fun PhyVlabDetailLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun PhyVlabUploadDialog(
    files: List<HomeworkFileContent>,
    feedback: String?,
    isSubmitting: Boolean,
    onPickFiles: () -> Unit,
    onRemoveFile: (Int) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scrollState = rememberScrollState()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("上传物理在线作业") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(scrollState)
                    .desktopTouchScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("先选择文件，确认后才会发送到物理在线。", style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = onPickFiles, enabled = !isSubmitting, modifier = Modifier.fillMaxWidth()) {
                    Text(if (files.isEmpty()) "选择文件" else "继续添加文件")
                }
                if (files.isEmpty()) {
                    Text("尚未选择文件", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    files.forEachIndexed { index, file ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(file.fileName, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                            TextButton(onClick = { onRemoveFile(index) }, enabled = !isSubmitting) { Text("移除") }
                        }
                    }
                }
                feedback?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = onSubmit, enabled = files.isNotEmpty() && !isSubmitting) {
                Text(if (isSubmitting) "提交中" else "继续")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !isSubmitting) { Text("取消") } },
    )
}

@Composable
private fun PhyVlabFailureBanner(
    failure: PhyVlabSyncFailure,
    detail: String?,
    onRetry: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    when (failure) {
                        PhyVlabSyncFailure.NETWORK -> "网络失败，请检查网络后重试。"
                        PhyVlabSyncFailure.PARSE -> "页面结构变化，暂时无法读取。"
                        PhyVlabSyncFailure.SESSION_EXPIRED -> "物理在线会话已失效，请重新登录。"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                detail?.let {
                    Text(
                        text = "诊断：$it",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
            // 仅显示协议层的粗粒度标签，便于定位部署差异；不显示 URL、Cookie
            // 或 OAuth/Moodle 会话参数。
            // 这些标签在正常成功状态下不会出现在界面上。
            TextButton(onClick = onRetry) { Text("重试") }
        }
    }
}

@Composable
private fun PhyVlabEmptyState(onRetry: () -> Unit, onOpenWeb: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("暂未找到物理在线课程", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                "课程、作业和安排会在这里原生显示；如果尚未选课，可打开网页版完成选课。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Button(onClick = onRetry) { Text("重新同步") }
            TextButton(onClick = onOpenWeb) { Text("打开网页版选课") }
        }
    }
}

@Composable
private fun PhyVlabCasLoginRequiredState(onRetry: () -> Unit, onLogout: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("需要完成统一身份认证", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                "物理在线需要单独建立 Moodle 会话。App 会先尝试复用当前统一身份认证；" +
                    "如果仍未成功，请退出并重新登录主账号。不会自动跳转浏览器。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Button(onClick = onRetry) { Text("重新建立认证") }
            TextButton(onClick = onLogout) { Text("退出并重新登录") }
        }
    }
}

/**
 * 课程作业的默认顺序是“新的在上面”：Moodle 活动 ID 是当前课程页唯一且
 * 单调的活动顺序标识，直接反转解析器原本的正序，避免老师调整截止日期后
 * 把“新旧”误判成“截止日期先后”。
 */
internal fun orderPhyVlabActivities(
    activities: List<PhyVlabActivity>,
    descending: Boolean,
): List<PhyVlabActivity> {
    val ordered = activities.sortedWith(
        compareBy<PhyVlabActivity> { it.id }.thenBy { it.title },
    )
    return if (descending) ordered.asReversed() else ordered
}
