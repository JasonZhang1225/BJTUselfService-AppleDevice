package team.bjtuss.bjtuselfservice.shared.feature.otherfunction

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import team.bjtuss.bjtuselfservice.shared.domain.otherfunction.OtherFunctionFailure
import team.bjtuss.bjtuselfservice.shared.domain.otherfunction.OtherFunctionTask
import team.bjtuss.bjtuselfservice.shared.domain.otherfunction.OtherFunctionTaskState
import team.bjtuss.bjtuselfservice.shared.domain.otherfunction.ReportCardLanguage

/**
 * 其他功能：校历下载 + 成绩单下载。
 * 两个任务互不阻塞；保存取消不会显示为红色失败。
 */
@Composable
fun OtherFunctionWorkspace(
    model: OtherFunctionScreenModel,
    expanded: Boolean,
    modifier: Modifier = Modifier,
) {
    val state by model.state.collectAsState()
    val scope = rememberCoroutineScope()

    Column(
        modifier = if (expanded) {
            modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        } else {
            modifier.padding(horizontal = 16.dp).padding(top = 14.dp)
        },
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (expanded) {
            Text(
                "其他功能",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "下载学校最新校历与中英文成绩单",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        OtherFunctionCard(
            title = "校历下载",
            description = "查看并下载学校最新的校历信息",
            actionLabel = "下载",
            taskState = state.calendarState,
            enabled = !state.isAnyTaskRunning,
            onAction = { scope.launch { model.downloadCalendar() } },
            onDismiss = { model.clearTaskState(OtherFunctionTask.CALENDAR) },
        )

        OtherFunctionCard(
            title = "成绩单下载",
            description = "下载个人学习成绩单，支持中英文版本",
            actionLabel = "下载",
            taskState = state.reportCardState,
            enabled = !state.isAnyTaskRunning,
            onAction = { scope.launch { model.downloadReportCard() } },
            onDismiss = { model.clearTaskState(OtherFunctionTask.REPORT_CARD) },
            extraContent = {
                val english = state.reportCardLanguage == ReportCardLanguage.ENGLISH
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .toggleable(
                            value = english,
                            role = Role.Switch,
                            onValueChange = { checked ->
                                model.setReportCardLanguage(
                                    if (checked) ReportCardLanguage.ENGLISH else ReportCardLanguage.CHINESE,
                                )
                            },
                        )
                        .semantics(mergeDescendants = true) {}
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (english) "英文版" else "中文版",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Switch(
                        checked = english,
                        onCheckedChange = null,
                    )
                }
            },
        )
    }
}

@Composable
private fun OtherFunctionCard(
    title: String,
    description: String,
    actionLabel: String,
    taskState: OtherFunctionTaskState,
    enabled: Boolean,
    onAction: () -> Unit,
    onDismiss: () -> Unit,
    extraContent: @Composable (() -> Unit)? = null,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            extraContent?.invoke()

            TaskStatusRow(
                taskState = taskState,
                onDismiss = onDismiss,
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                Button(
                    onClick = onAction,
                    enabled = enabled && taskState != OtherFunctionTaskState.Downloading,
                ) {
                    if (taskState == OtherFunctionTaskState.Downloading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
private fun TaskStatusRow(
    taskState: OtherFunctionTaskState,
    onDismiss: () -> Unit,
) {
    when (taskState) {
        OtherFunctionTaskState.Idle,
        OtherFunctionTaskState.SaveCancelled,
        -> Unit
        OtherFunctionTaskState.Downloading -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                )
                Text(
                    "正在下载…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        is OtherFunctionTaskState.Saved -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    "已保存：${taskState.fileName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onDismiss) { Text("知道了") }
            }
        }
        is OtherFunctionTaskState.Failed -> {
            val message = when (taskState.reason) {
                OtherFunctionFailure.NETWORK -> "网络请求失败，请检查网络后重试。"
                OtherFunctionFailure.PARSE -> "页面格式异常，暂时无法解析文件地址。"
                OtherFunctionFailure.SESSION_EXPIRED -> "登录会话已过期，请重新登录后再下载。"
                OtherFunctionFailure.SAVE_FAILED -> "系统保存失败，请重新选择保存位置。"
                OtherFunctionFailure.SAVE_UNAVAILABLE -> "当前平台不支持系统保存面板。"
            }
            Column(modifier = Modifier.fillMaxWidth()) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = onDismiss) { Text("关闭") }
                    }
                }
            }
        }
    }
}
