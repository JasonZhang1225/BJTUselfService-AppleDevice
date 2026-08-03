package team.bjtuss.bjtuselfservice.shared.feature.exam

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import team.bjtuss.bjtuselfservice.shared.accessibleAlpha
import team.bjtuss.bjtuselfservice.shared.data.exam.ExamScheduleSyncFailure
import team.bjtuss.bjtuselfservice.shared.domain.exam.ExamSchedule

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ExamScheduleWorkspace(
    state: ExamScheduleUiState,
    expanded: Boolean,
    model: ExamScheduleScreenModel,
    onRefresh: () -> Unit,
    modifier: Modifier,
) {
    LaunchedEffect(model) {
        model.initialize()
    }

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
                        "考试安排",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        "按考试类型筛选并查看完整时间、地点和状态",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FilledTonalButton(onClick = onRefresh, enabled = !state.isRefreshing) {
                    Text(if (state.isRefreshing) "正在同步" else "同步考试")
                }
            }
        }

        if (state.isRefreshing) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        state.failure?.let { failure ->
            ExamFailureBanner(
                failure = failure,
                hasContent = state.exams.isNotEmpty(),
                onRetry = onRefresh,
                onDismiss = model::dismissFailure,
            )
        }

        when {
            state.isLoading && state.exams.isEmpty() -> ExamLoadingState()
            state.exams.isEmpty() -> ExamEmptyState(onRefresh)
            else -> {
                ExamSummary(state)
                ExamTypeFilters(state, model)
                if (state.visibleExams.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("当前类型下没有考试安排", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else if (expanded) {
                    Row(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        ExamList(
                            exams = state.visibleExams,
                            selectedExamId = state.selectedExamId,
                            onOpen = model::showExamDetails,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                        ExamDetailPanel(
                            exam = state.selectedExam,
                            modifier = Modifier.widthIn(min = 290.dp, max = 380.dp).fillMaxHeight(),
                        )
                    }
                } else {
                    ExamList(
                        exams = state.visibleExams,
                        selectedExamId = state.selectedExamId,
                        onOpen = model::showExamDetails,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )
                    state.selectedExam?.let { exam ->
                        ModalBottomSheet(onDismissRequest = model::dismissExamDetails) {
                            ExamDetailContent(
                                exam = exam,
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
private fun ExamSummary(state: ExamScheduleUiState) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "考试安排：${state.visibleExams.size} 项",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    state.selectedType ?: "全部类型",
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
                        state.source == ExamScheduleContentSource.CACHE -> "本地缓存"
                        state.source == ExamScheduleContentSource.NETWORK -> "已同步"
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
private fun ExamTypeFilters(state: ExamScheduleUiState, model: ExamScheduleScreenModel) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = state.selectedType == null,
            onClick = { model.selectType(null) },
            label = { Text("全部") },
        )
        state.typeOptions.forEach { type ->
            FilterChip(
                selected = state.selectedType == type,
                onClick = { model.selectType(type) },
                label = { Text(type) },
            )
        }
    }
}

@Composable
private fun ExamList(
    exams: List<ExamSchedule>,
    selectedExamId: Int?,
    onOpen: (Int) -> Unit,
    modifier: Modifier,
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 18.dp),
    ) {
        items(exams, key = ExamSchedule::id) { exam ->
            ElevatedCard(
                onClick = { onOpen(exam.id) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(17.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = if (exam.id == selectedExamId) {
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
                        exam.courseName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        exam.examType,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    ExamCardLine("时间地点", exam.examTimeAndPlace)
                    ExamCardLine("状态", exam.examStatus)
                    if (exam.detail.isNotBlank()) {
                        ExamCardLine("详情", exam.detail)
                    }
                }
            }
        }
    }
}

@Composable
private fun ExamCardLine(label: String, value: String) {
    Text(
        "$label · ${value.ifBlank { "未提供" }}",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun ExamDetailPanel(exam: ExamSchedule?, modifier: Modifier) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant.accessibleAlpha(0.54f),
        shape = RoundedCornerShape(22.dp),
    ) {
        if (exam == null) {
            Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text("选择一项考试查看详情", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            ExamDetailContent(
                exam = exam,
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp),
            )
        }
    }
}

@Composable
private fun ExamDetailContent(exam: ExamSchedule, modifier: Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            "考试详情",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() },
        )
        Text(exam.courseName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        ExamDetailLine("类型", exam.examType)
        ExamDetailLine("时间地点", exam.examTimeAndPlace)
        ExamDetailLine("状态", exam.examStatus)
        ExamDetailLine("详情", exam.detail.ifBlank { "未提供" })
    }
}

@Composable
private fun ExamDetailLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ExamFailureBanner(
    failure: ExamScheduleSyncFailure,
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
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                when (failure) {
                    ExamScheduleSyncFailure.NETWORK -> if (hasContent) {
                        "同步失败，正在显示本地考试安排。"
                    } else {
                        "无法连接教务系统，请检查网络后重试。"
                    }
                    ExamScheduleSyncFailure.SESSION_EXPIRED -> "教务会话已失效，请退出后重新登录。"
                    ExamScheduleSyncFailure.MALFORMED_RESPONSE -> "教务考试页面结构已变化，暂时无法解析。"
                    ExamScheduleSyncFailure.CACHE -> "本地考试缓存操作失败。"
                },
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (failure != ExamScheduleSyncFailure.CACHE) {
                TextButton(onClick = onRetry) { Text("重试") }
            }
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    }
}

@Composable
private fun ExamLoadingState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator()
            Text("正在读取本地考试安排并连接教务系统…")
        }
    }
}

@Composable
private fun ExamEmptyState(onRefresh: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("暂无考试安排", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                "本地没有缓存，教务系统也没有返回可显示的考试。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onRefresh) { Text("重新同步") }
        }
    }
}
