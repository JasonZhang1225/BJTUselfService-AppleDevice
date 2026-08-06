package team.bjtuss.bjtuselfservice.shared.feature.homework

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fleeksoft.ksoup.Ksoup
import kotlinx.coroutines.launch
import team.bjtuss.bjtuselfservice.shared.accessibleAlpha
import team.bjtuss.bjtuselfservice.shared.data.homework.HomeworkSyncFailure
import team.bjtuss.bjtuselfservice.shared.data.homework.HomeworkOperationResult
import team.bjtuss.bjtuselfservice.shared.domain.homework.Homework
import team.bjtuss.bjtuselfservice.shared.domain.homework.HomeworkAttachment
import team.bjtuss.bjtuselfservice.shared.domain.homework.HomeworkDetail
import team.bjtuss.bjtuselfservice.shared.domain.homework.HomeworkFileContent
import team.bjtuss.bjtuselfservice.shared.domain.homework.HomeworkSortOrder
import team.bjtuss.bjtuselfservice.shared.domain.homework.SubmittedHomeworkAttachment
import team.bjtuss.bjtuselfservice.shared.domain.homework.stableKey
import team.bjtuss.bjtuselfservice.shared.domain.homework.typeLabel
import team.bjtuss.bjtuselfservice.shared.files.HomeworkFileGateway
import team.bjtuss.bjtuselfservice.shared.files.HomeworkFilePickResult
import team.bjtuss.bjtuselfservice.shared.files.HomeworkFileSaveResult
import team.bjtuss.bjtuselfservice.shared.feature.shell.AppErrorBanner
import team.bjtuss.bjtuselfservice.shared.feature.shell.LegacySmartTransportWarning

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HomeworkWorkspace(
    state: HomeworkUiState,
    expanded: Boolean,
    usesLegacySmartTransport: Boolean = false,
    legacyWarningVisible: Boolean = false,
    onDismissLegacyWarning: () -> Unit = {},
    model: HomeworkScreenModel,
    fileGateway: HomeworkFileGateway,
    onRefresh: () -> Unit,
    modifier: Modifier,
) {
    val scope = rememberCoroutineScope()
    var showFilterSheet by remember { mutableStateOf(false) }
    var showUpload by remember { mutableStateOf(false) }
    var uploadFiles by remember { mutableStateOf<List<HomeworkFileContent>>(emptyList()) }
    var uploadContent by remember { mutableStateOf("") }
    var uploadFeedback by remember { mutableStateOf<String?>(null) }
    var fileFeedback by remember { mutableStateOf<String?>(null) }

    fun saveTeacherAttachment(attachmentId: Int) {
        if (!fileGateway.isAvailable) {
            fileFeedback = "当前平台的系统保存面板尚未接入。"
            return
        }
        scope.launch {
            fileFeedback = null
            when (val downloaded = model.downloadTeacherAttachment(attachmentId)) {
                is HomeworkOperationResult.Failure -> Unit
                is HomeworkOperationResult.Success -> {
                    fileFeedback = fileGateway.saveFile(downloaded.value).saveFeedback()
                }
            }
        }
    }

    fun saveSubmittedAttachment(attachmentId: String) {
        if (!fileGateway.isAvailable) {
            fileFeedback = "当前平台的系统保存面板尚未接入。"
            return
        }
        scope.launch {
            fileFeedback = null
            when (val downloaded = model.downloadSubmittedAttachment(attachmentId)) {
                is HomeworkOperationResult.Failure -> Unit
                is HomeworkOperationResult.Success -> {
                    fileFeedback = fileGateway.saveFile(downloaded.value).saveFeedback()
                }
            }
        }
    }

    fun openUpload() {
        if (!fileGateway.isAvailable) {
            fileFeedback = "当前平台的系统文件选择器尚未接入。"
            return
        }
        uploadFiles = emptyList()
        uploadContent = ""
        uploadFeedback = null
        showUpload = true
    }

    fun closeUpload() {
        if (state.isSubmitting) return
        showUpload = false
        uploadFiles = emptyList()
        uploadContent = ""
        uploadFeedback = null
    }

    fun pickUploadFiles() {
        scope.launch {
            when (val result = fileGateway.pickFiles()) {
                HomeworkFilePickResult.Cancelled -> Unit
                is HomeworkFilePickResult.Failed -> uploadFeedback = "无法读取所选文件，请重新选择。"
                is HomeworkFilePickResult.Selected -> {
                    uploadFiles = uploadFiles + result.files
                    uploadFeedback = null
                }
            }
        }
    }

    fun submitUpload() {
        if (uploadFiles.isEmpty()) {
            uploadFeedback = "请先选择至少一个文件。"
            return
        }
        scope.launch {
            uploadFeedback = null
            when (val result = model.submitHomework(uploadContent, uploadFiles)) {
                is HomeworkOperationResult.Failure -> {
                    uploadFeedback = when (result.reason) {
                        HomeworkSyncFailure.NETWORK -> "上传失败，请检查网络后重试。"
                        HomeworkSyncFailure.SESSION_EXPIRED -> "登录会话已失效，请重新登录。"
                        HomeworkSyncFailure.MALFORMED_RESPONSE -> "学校平台没有确认提交成功，请稍后重试。"
                        HomeworkSyncFailure.SECURE_CHANNEL_UNAVAILABLE -> "该资源地址不在允许的学校通道范围内。"
                        HomeworkSyncFailure.CACHE -> "提交已停止，本地缓存不可用。"
                    }
                }
                is HomeworkOperationResult.Success -> {
                    showUpload = false
                    uploadFiles = emptyList()
                    uploadContent = ""
                    fileFeedback = "作业已提交，列表和详情已刷新。"
                }
            }
        }
    }

    // 只灌缓存；网络自动同步由 shell 在登录成功后触发。
    LaunchedEffect(model) { model.initialize(refreshFromNetwork = false) }

    Column(
        modifier = if (expanded) {
            modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        } else {
            modifier.padding(horizontal = 16.dp).padding(top = 8.dp)
        },
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (expanded) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "作业",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        "按课程和截止时间整理平时作业、课程设计与实验报告",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FilledTonalButton(onClick = onRefresh, enabled = !state.isRefreshing) {
                    Text(if (state.isRefreshing) "正在同步" else "同步作业")
                }
            }
        }

        // 明文通道提示由 shell 控制显隐；宽度跟随父 Column 水平 padding，勿再叠 16.dp。
        if (usesLegacySmartTransport && !legacyWarningVisible) LegacySmartTransportWarning()
        if (legacyWarningVisible) {
            LegacySmartTransportWarning(onDismiss = onDismissLegacyWarning)
        }

        // 同步进度条由 DestinationPage 钉在顶栏下，此处不再重复。
        state.failure?.let { failure ->
            HomeworkFailureBanner(
                failure = failure,
                hasContent = state.homework.isNotEmpty(),
                onRetry = onRefresh,
                onDismiss = model::dismissFailure,
            )
        }

        when {
            state.isLoading && state.homework.isEmpty() -> HomeworkLoadingState()
            state.homework.isEmpty() -> HomeworkEmptyState(onRefresh)
            else -> {
                if (expanded) {
                    HomeworkSummary(state)
                    HomeworkExpandedFilters(state, model)
                    if (state.visibleHomework.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Text("当前筛选下没有作业", style = MaterialTheme.typography.titleMedium)
                                TextButton(onClick = model::clearCourseFilter) { Text("清除课程筛选") }
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            HomeworkList(
                                homework = state.visibleHomework,
                                selectedKey = state.selectedHomeworkKey,
                                sortOrder = state.sortOrder,
                                onOpen = { key ->
                                    fileFeedback = null
                                    scope.launch { model.showDetails(key) }
                                },
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                            )
                            HomeworkDetailPanel(
                                homework = state.selectedHomework,
                                detail = state.detail,
                                submittedAttachments = state.submittedAttachments,
                                isLoading = state.isDetailLoading,
                                isSubmittedLoading = state.isSubmittedAttachmentsLoading,
                                failure = state.detailFailure,
                                fileFailure = state.fileFailure,
                                isFileTransferInProgress = state.isFileTransferInProgress,
                                fileGatewayAvailable = fileGateway.isAvailable,
                                fileFeedback = fileFeedback,
                                onDownloadTeacher = ::saveTeacherAttachment,
                                onDownloadSubmitted = ::saveSubmittedAttachment,
                                onUpload = ::openUpload,
                                isSubmitting = state.isSubmitting,
                                modifier = Modifier.widthIn(min = 320.dp, max = 420.dp).fillMaxHeight(),
                            )
                        }
                    }
                } else {
                    // 紧凑端：Banner（含筛选按钮）+ 列表；筛选进 sheet；同步态在顶栏右上。
                    HomeworkScrollableContent(
                        state = state,
                        onOpenFilter = { showFilterSheet = true },
                        onOpen = { key ->
                            fileFeedback = null
                            scope.launch { model.showDetails(key) }
                        },
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )
                    state.selectedHomework?.takeIf { !showUpload }?.let { selected ->
                        ModalBottomSheet(onDismissRequest = model::dismissDetails) {
                            HomeworkDetailContent(
                                homework = selected,
                                detail = state.detail,
                                submittedAttachments = state.submittedAttachments,
                                isLoading = state.isDetailLoading,
                                isSubmittedLoading = state.isSubmittedAttachmentsLoading,
                                failure = state.detailFailure,
                                fileFailure = state.fileFailure,
                                isFileTransferInProgress = state.isFileTransferInProgress,
                                fileGatewayAvailable = fileGateway.isAvailable,
                                fileFeedback = fileFeedback,
                                onDownloadTeacher = ::saveTeacherAttachment,
                                onDownloadSubmitted = ::saveSubmittedAttachment,
                                onUpload = ::openUpload,
                                isSubmitting = state.isSubmitting,
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
            HomeworkFilterSheet(state = state, model = model)
        }
    }

    if (showUpload) {
        if (expanded) {
            AlertDialog(
                onDismissRequest = ::closeUpload,
                title = { Text("上传作业") },
                text = {
                    UploadHomeworkContent(
                        files = uploadFiles,
                        content = uploadContent,
                        feedback = uploadFeedback,
                        isSubmitting = state.isSubmitting,
                        onContentChange = { uploadContent = it },
                        onPickFiles = ::pickUploadFiles,
                        onRemoveFile = { index -> uploadFiles = uploadFiles.filterIndexed { itemIndex, _ -> itemIndex != index } },
                    )
                },
                confirmButton = {
                    Button(
                        onClick = ::submitUpload,
                        enabled = uploadFiles.isNotEmpty() && !state.isSubmitting,
                    ) { Text(if (state.isSubmitting) "正在提交" else "提交") }
                },
                dismissButton = {
                    TextButton(onClick = ::closeUpload, enabled = !state.isSubmitting) { Text("取消") }
                },
            )
        } else {
            ModalBottomSheet(
                onDismissRequest = ::closeUpload,
            ) {
                UploadHomeworkContent(
                    files = uploadFiles,
                    content = uploadContent,
                    feedback = uploadFeedback,
                    isSubmitting = state.isSubmitting,
                    onContentChange = { uploadContent = it },
                    onPickFiles = ::pickUploadFiles,
                    onRemoveFile = { index -> uploadFiles = uploadFiles.filterIndexed { itemIndex, _ -> itemIndex != index } },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TextButton(
                        onClick = ::closeUpload,
                        enabled = !state.isSubmitting,
                        modifier = Modifier.weight(1f),
                    ) { Text("取消") }
                    Button(
                        onClick = ::submitUpload,
                        enabled = uploadFiles.isNotEmpty() && !state.isSubmitting,
                        modifier = Modifier.weight(1f),
                    ) { Text(if (state.isSubmitting) "正在提交" else "提交") }
                }
                Spacer(Modifier.height(18.dp))
            }
        }
    }
}

@Composable
private fun HomeworkSummary(
    state: HomeworkUiState,
    onOpenFilter: (() -> Unit)? = null,
) {
    // 仅课程/过期过滤算「筛选」；改排序不算。
    val filtered = state.selectedCourses.isNotEmpty() || state.hideExpired
    val subtitle = buildString {
        append(
            if (state.dueSoonCount > 0) {
                "未来 48 小时内有 ${state.dueSoonCount} 项未提交"
            } else {
                "未来 48 小时内暂无临近截止项"
            },
        )
        if (filtered) {
            append(" · 已筛选")
        }
    }
    // 与成绩/课表 Banner 对齐：摘要 + 右侧筛选 pill；同步态在顶栏右上。
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
                    .then(
                        if (onOpenFilter != null) {
                            Modifier
                                .clickable(onClick = onOpenFilter)
                                .padding(vertical = 2.dp, horizontal = 4.dp)
                        } else {
                            Modifier
                        },
                    ),
            ) {
                Text(
                    "${state.visibleHomework.size} 项作业",
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
            if (onOpenFilter != null) {
                Surface(
                    onClick = onOpenFilter,
                    color = MaterialTheme.colorScheme.surface.accessibleAlpha(0.86f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier.semantics { contentDescription = "筛选与排序" },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        HomeworkFilterFunnelIcon(modifier = Modifier.size(18.dp))
                        HomeworkSortBarsIcon(modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeworkFilterFunnelIcon(
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    Canvas(modifier = modifier) {
        val stroke = 1.8.dp.toPx()
        val left = 2.5.dp.toPx()
        val right = size.width - left
        val top = 3.dp.toPx()
        val midY = size.height * 0.48f
        val neckLeft = size.width * 0.42f
        val neckRight = size.width * 0.58f
        val bottom = size.height - 2.5.dp.toPx()
        drawLine(tint, Offset(left, top), Offset(right, top), stroke, StrokeCap.Round)
        drawLine(tint, Offset(left, top), Offset(neckLeft, midY), stroke, StrokeCap.Round)
        drawLine(tint, Offset(right, top), Offset(neckRight, midY), stroke, StrokeCap.Round)
        drawLine(tint, Offset(neckLeft, midY), Offset(neckLeft, bottom), stroke, StrokeCap.Round)
        drawLine(tint, Offset(neckRight, midY), Offset(neckRight, bottom), stroke, StrokeCap.Round)
        drawLine(tint, Offset(neckLeft, bottom), Offset(neckRight, bottom), stroke, StrokeCap.Round)
    }
}

@Composable
private fun HomeworkSortBarsIcon(
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    Canvas(modifier = modifier) {
        val stroke = 1.8.dp.toPx()
        val left = 3.dp.toPx()
        val right = size.width - left
        val ys = listOf(size.height * 0.28f, size.height * 0.5f, size.height * 0.72f)
        val ends = listOf(right, right * 0.72f, right * 0.48f)
        ys.zip(ends).forEach { (y, endX) ->
            drawLine(tint, Offset(left, y), Offset(endX, y), stroke, StrokeCap.Round)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HomeworkExpandedFilters(state: HomeworkUiState, model: HomeworkScreenModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = state.selectedCourses.isEmpty(),
                onClick = model::clearCourseFilter,
                label = { Text("全部课程") },
            )
            state.courseOptions.forEach { course ->
                FilterChip(
                    selected = course in state.selectedCourses,
                    onClick = { model.toggleCourse(course) },
                    label = { Text(course, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                )
            }
        }
        HomeworkBehaviorFilters(state, model)
    }
}

@Composable
private fun HomeworkBehaviorFilters(state: HomeworkUiState, model: HomeworkScreenModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HomeworkDeadlineFilterChips(state = state, model = model)
        OutlinedButton(onClick = model::cycleSortOrder, modifier = Modifier.fillMaxWidth()) {
            Text(
                when (state.sortOrder) {
                    HomeworkSortOrder.ORIGINAL -> "截止时间 · 原顺序"
                    HomeworkSortOrder.ASCENDING -> "截止时间 · 由近到远"
                    HomeworkSortOrder.DESCENDING -> "截止时间 · 由远到近"
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 截止时间：两个圆角矩形，互斥（显示全部 / 隐藏已过期）。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HomeworkDeadlineFilterChips(
    state: HomeworkUiState,
    model: HomeworkScreenModel,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = !state.hideExpired,
            onClick = { model.setHideExpired(false) },
            shape = RoundedCornerShape(10.dp),
            label = { Text("显示全部日期") },
        )
        FilterChip(
            selected = state.hideExpired,
            onClick = { model.setHideExpired(true) },
            shape = RoundedCornerShape(10.dp),
            label = { Text("隐藏已过期") },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HomeworkFilterSheet(
    state: HomeworkUiState,
    model: HomeworkScreenModel,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text("筛选与排序", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "课程",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            if (state.courseOptions.isEmpty()) {
                Text(
                    "暂无课程数据",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = state.selectedCourses.isEmpty(),
                        onClick = model::clearCourseFilter,
                        label = { Text("全部") },
                    )
                    state.courseOptions.forEach { course ->
                        FilterChip(
                            selected = course in state.selectedCourses,
                            onClick = { model.toggleCourse(course) },
                            label = {
                                Text(course, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                        )
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "截止时间",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            HomeworkDeadlineFilterChips(state = state, model = model)
        }

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
                FilterChip(
                    selected = state.sortOrder == HomeworkSortOrder.ORIGINAL,
                    onClick = { model.setSortOrder(HomeworkSortOrder.ORIGINAL) },
                    shape = RoundedCornerShape(percent = 50),
                    label = { Text("原顺序") },
                )
                FilterChip(
                    selected = state.sortOrder == HomeworkSortOrder.ASCENDING,
                    onClick = { model.setSortOrder(HomeworkSortOrder.ASCENDING) },
                    shape = RoundedCornerShape(percent = 50),
                    label = { Text("由近到远") },
                )
                FilterChip(
                    selected = state.sortOrder == HomeworkSortOrder.DESCENDING,
                    onClick = { model.setSortOrder(HomeworkSortOrder.DESCENDING) },
                    shape = RoundedCornerShape(percent = 50),
                    label = { Text("由远到近") },
                )
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun CourseFilterRow(title: String, selected: Boolean, onClick: () -> Unit) {
    Surface(shape = RoundedCornerShape(14.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .toggleable(
                    value = selected,
                    role = Role.Checkbox,
                    onValueChange = { onClick() },
                )
                .semantics(mergeDescendants = true) {}
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = selected, onCheckedChange = null)
            Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

/** 紧凑作业页唯一纵向滚动体：Banner + 列表（筛选在 sheet）。 */
@Composable
private fun HomeworkScrollableContent(
    state: HomeworkUiState,
    onOpenFilter: () -> Unit,
    onOpen: (String) -> Unit,
    modifier: Modifier,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(state.sortOrder, state.hideExpired, state.selectedCourses) {
        listState.scrollToItem(0)
    }
    LazyColumn(
        state = listState,
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 18.dp),
    ) {
        item(key = "homework-summary") {
            HomeworkSummary(state = state, onOpenFilter = onOpenFilter)
        }
        if (state.visibleHomework.isEmpty()) {
            item(key = "homework-empty") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("当前筛选下没有作业", style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = onOpenFilter) { Text("调整筛选") }
                }
            }
        } else {
            items(state.visibleHomework, key = Homework::stableKey) { item ->
                HomeworkCard(
                    item = item,
                    selected = item.stableKey() == state.selectedHomeworkKey,
                    onOpen = onOpen,
                )
            }
        }
    }
}

@Composable
private fun HomeworkCard(
    item: Homework,
    selected: Boolean,
    onOpen: (String) -> Unit,
) {
    val key = item.stableKey()
    ElevatedCard(
        onClick = { onOpen(key) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(17.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                item.courseName,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                item.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            HomeworkCardLine("类型", item.typeLabel())
            HomeworkCardLine("提交状态", item.subStatus.ifBlank { "未标明" })
            HomeworkCardLine("截止", item.endTime.ifBlank { "未提供" })
            HomeworkCardLine("提交人数", "${item.submitCount} / ${item.allCount}")
            if (item.score.isNotBlank()) HomeworkCardLine("评分", item.score)
        }
    }
}

@Composable
private fun HomeworkList(
    homework: List<Homework>,
    selectedKey: String?,
    sortOrder: HomeworkSortOrder,
    onOpen: (String) -> Unit,
    modifier: Modifier,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(sortOrder) {
        listState.scrollToItem(0)
    }
    LazyColumn(
        state = listState,
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 18.dp),
    ) {
        items(homework, key = Homework::stableKey) { item ->
            HomeworkCard(
                item = item,
                selected = item.stableKey() == selectedKey,
                onOpen = onOpen,
            )
        }
    }
}

@Composable
private fun HomeworkCardLine(label: String, value: String) {
    Text(
        "$label · $value",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun HomeworkDetailPanel(
    homework: Homework?,
    detail: HomeworkDetail?,
    submittedAttachments: List<SubmittedHomeworkAttachment>,
    isLoading: Boolean,
    isSubmittedLoading: Boolean,
    failure: HomeworkSyncFailure?,
    fileFailure: HomeworkSyncFailure?,
    isFileTransferInProgress: Boolean,
    fileGatewayAvailable: Boolean,
    fileFeedback: String?,
    onDownloadTeacher: (Int) -> Unit,
    onDownloadSubmitted: (String) -> Unit,
    onUpload: () -> Unit,
    isSubmitting: Boolean,
    modifier: Modifier,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant.accessibleAlpha(0.54f),
        shape = RoundedCornerShape(22.dp),
    ) {
        if (homework == null) {
            Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text("选择一项作业查看要求和附件", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            HomeworkDetailContent(
                homework = homework,
                detail = detail,
                submittedAttachments = submittedAttachments,
                isLoading = isLoading,
                isSubmittedLoading = isSubmittedLoading,
                failure = failure,
                fileFailure = fileFailure,
                isFileTransferInProgress = isFileTransferInProgress,
                fileGatewayAvailable = fileGatewayAvailable,
                fileFeedback = fileFeedback,
                onDownloadTeacher = onDownloadTeacher,
                onDownloadSubmitted = onDownloadSubmitted,
                onUpload = onUpload,
                isSubmitting = isSubmitting,
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp),
            )
        }
    }
}

@Composable
private fun HomeworkDetailContent(
    homework: Homework,
    detail: HomeworkDetail?,
    submittedAttachments: List<SubmittedHomeworkAttachment>,
    isLoading: Boolean,
    isSubmittedLoading: Boolean,
    failure: HomeworkSyncFailure?,
    fileFailure: HomeworkSyncFailure?,
    isFileTransferInProgress: Boolean,
    fileGatewayAvailable: Boolean,
    fileFeedback: String?,
    onDownloadTeacher: (Int) -> Unit,
    onDownloadSubmitted: (String) -> Unit,
    onUpload: () -> Unit,
    isSubmitting: Boolean,
    modifier: Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            "作业详情",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() },
        )
        Text(homework.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        HomeworkDetailLine("课程", homework.courseName)
        HomeworkDetailLine("类型", homework.typeLabel())
        HomeworkDetailLine("开放时间", homework.openDate.ifBlank { "未提供" })
        HomeworkDetailLine("截止时间", homework.endTime.ifBlank { "未提供" })
        HomeworkDetailLine("提交状态", homework.subStatus.ifBlank { "未标明" })
        if (homework.score.isNotBlank()) HomeworkDetailLine("评分", homework.score)
        if (isLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        failure?.let {
            Text(
                "详情同步失败，正在显示列表中已有的内容。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        fileFailure?.let {
            Text(
                "附件或提交状态读取失败，请稍后重试。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (isFileTransferInProgress) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text("正在获取附件…", style = MaterialTheme.typography.bodySmall)
        }
        fileFeedback?.let { message ->
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text("作业要求", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            detail?.content.toPlainText().ifBlank { "老师未填写文字要求。" },
            style = MaterialTheme.typography.bodyLarge,
        )
        Text("老师提供的附件", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (detail?.attachments.isNullOrEmpty()) {
            Text("暂无附件", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            detail?.attachments.orEmpty().forEach { attachment ->
                HomeworkAttachmentRow(
                    attachment = attachment,
                    downloadEnabled = fileGatewayAvailable && !isFileTransferInProgress,
                    onDownload = { onDownloadTeacher(attachment.id) },
                )
            }
        }
        if (homework.idSnId != null || homework.subStatus == "已提交") {
            Text("我已提交的附件", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (isSubmittedLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else if (submittedAttachments.isEmpty()) {
                Text("没有找到已提交附件", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                submittedAttachments.forEach { attachment ->
                    SubmittedHomeworkAttachmentRow(
                        attachment = attachment,
                        downloadEnabled = fileGatewayAvailable && !isFileTransferInProgress,
                        onDownload = { onDownloadSubmitted(attachment.id) },
                    )
                }
            }
        }
        FilledTonalButton(
            onClick = onUpload,
            enabled = fileGatewayAvailable && !isSubmitting && !isFileTransferInProgress,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (isSubmitting) "正在提交" else "上传作业")
        }
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(14.dp),
        ) {
            Text(
                if (fileGatewayAvailable) {
                    "下载会先获取附件，再由系统保存面板选择位置；上传会先让你检查文件列表，再发送到学校平台。"
                } else {
                    "当前平台的系统文件面板仍在接入；不会静默写入文件，也不会把网络完成冒充为保存成功。"
                },
                modifier = Modifier.padding(14.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun UploadHomeworkContent(
    files: List<HomeworkFileContent>,
    content: String,
    feedback: String?,
    isSubmitting: Boolean,
    onContentChange: (String) -> Unit,
    onPickFiles: () -> Unit,
    onRemoveFile: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "先选择文件并检查列表，再提交到学校智慧教学平台。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = content,
            onValueChange = onContentChange,
            label = { Text("提交说明（可选）") },
            enabled = !isSubmitting,
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            maxLines = 5,
        )
        OutlinedButton(
            onClick = onPickFiles,
            enabled = !isSubmitting,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (files.isEmpty()) "选择文件" else "继续添加文件")
        }
        if (files.isEmpty()) {
            Text("尚未选择文件", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            files.forEachIndexed { index, file ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(file.fileName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                file.bytes.size.toLong().toReadableSize(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = { onRemoveFile(index) }, enabled = !isSubmitting) { Text("移除") }
                    }
                }
            }
        }
        feedback?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        if (isSubmitting) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text("正在上传并提交，请勿关闭窗口。", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun HomeworkAttachmentRow(
    attachment: HomeworkAttachment,
    downloadEnabled: Boolean,
    onDownload: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(12.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(attachment.fileName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                attachment.sizeBytes.toReadableSize(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FilledTonalButton(
                onClick = onDownload,
                enabled = downloadEnabled,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("下载")
            }
        }
    }
}

@Composable
private fun SubmittedHomeworkAttachmentRow(
    attachment: SubmittedHomeworkAttachment,
    downloadEnabled: Boolean,
    onDownload: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(12.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(attachment.fileName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                "已提交附件",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FilledTonalButton(
                onClick = onDownload,
                enabled = downloadEnabled,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("下载")
            }
        }
    }
}

@Composable
private fun HomeworkDetailLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun HomeworkFailureBanner(
    failure: HomeworkSyncFailure,
    hasContent: Boolean,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    AppErrorBanner(
        message = when (failure) {
            HomeworkSyncFailure.NETWORK -> if (hasContent) {
                "同步失败，正在显示本地作业。"
            } else {
                "无法连接智慧教学平台，请检查网络后重试。"
            }
            HomeworkSyncFailure.SESSION_EXPIRED -> "智慧教学平台会话已失效，请退出后重新登录。"
            HomeworkSyncFailure.MALFORMED_RESPONSE -> "智慧教学平台响应结构已变化，暂时无法解析。"
            // 已授权明文后仍可能因 URL 白名单失败；勿再写「没有 HTTPS」与顶栏授权提示打架。
            HomeworkSyncFailure.SECURE_CHANNEL_UNAVAILABLE ->
                "该资源地址不在允许的学校通道范围内。"
            HomeworkSyncFailure.CACHE -> "本地作业缓存操作失败。"
        },
        onRetry = if (failure != HomeworkSyncFailure.CACHE) onRetry else null,
        onDismiss = onDismiss,
    )
}

@Composable
private fun HomeworkLoadingState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator()
            Text("正在读取本地作业并连接智慧教学平台…")
        }
    }
}

@Composable
private fun HomeworkEmptyState(onRefresh: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("暂无作业", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                "本地没有缓存，智慧教学平台也没有返回可显示的任务。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onRefresh) { Text("重新同步") }
        }
    }
}

private fun String?.toPlainText(): String = this
    ?.takeIf(String::isNotBlank)
    ?.let { Ksoup.parse(it).text() }
    .orEmpty()

private fun Long.toReadableSize(): String = when {
    this <= 0L -> "大小未知"
    this < 1024L -> "$this B"
    this < 1024L * 1024L -> "${this / 1024L} KB"
    else -> "${this / (1024L * 1024L)} MB"
}

private fun HomeworkFileSaveResult.saveFeedback(): String? = when (this) {
    HomeworkFileSaveResult.Saved -> "附件已保存到你选择的位置。"
    HomeworkFileSaveResult.Cancelled -> null
    is HomeworkFileSaveResult.Failed -> "系统保存失败，请重新选择位置。"
}
