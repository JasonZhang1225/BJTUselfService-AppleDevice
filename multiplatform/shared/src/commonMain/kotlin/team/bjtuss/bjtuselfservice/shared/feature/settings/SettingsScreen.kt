package team.bjtuss.bjtuselfservice.shared.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import team.bjtuss.bjtuselfservice.shared.PlatformInfo

@Composable
fun SettingsWorkspace(
    model: SettingsScreenModel,
    accountName: String,
    platform: PlatformInfo,
    expanded: Boolean,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by model.state.collectAsState()
    val scope = rememberCoroutineScope()
    var confirmClear by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清除当前账号离线缓存？") },
            text = { Text("成绩、课表、考试和作业的离线副本会被删除；不会退出账号，也不会清除主题设置。之后可从学校系统重新下载。") },
            confirmButton = {
                Button(onClick = {
                    confirmClear = false
                    scope.launch { model.clearOfflineCache() }
                }) { Text("清除缓存") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("取消") } },
        )
    }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(
            horizontal = if (expanded) 8.dp else 16.dp,
            vertical = 14.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 紧凑布局下 shell 顶栏已显示“设置”，页内不再重复；宽屏侧栏布局没有顶栏，保留页内标题。
        if (expanded) {
            Text("设置", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
        SettingCard("账户", accountName.ifBlank { "未登录" })

        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("主题设置", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "应用始终跟随系统浅色/深色外观，不再提供单独切换。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("自动同步", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "打开后，每次登录成功会先显示离线缓存，再自动拉取最新数据；首次失败会自动重试一次。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AutoSyncSettingRow("自动同步成绩", state.preferences.autoSyncGrades, model::setAutoSyncGrades)
                AutoSyncSettingRow("自动同步作业", state.preferences.autoSyncHomework, model::setAutoSyncHomework)
                AutoSyncSettingRow("自动同步课表", state.preferences.autoSyncSchedule, model::setAutoSyncSchedule)
                AutoSyncSettingRow("自动同步考试", state.preferences.autoSyncExams, model::setAutoSyncExams)
                if (state.saveFailed) {
                    Feedback("同步设置保存失败，请重试。", true, model::dismissFeedback)
                }
            }
        }

        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("版本与项目", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "${platform.displayName} · KMP 迁移构建\n功能对齐基线：v1.7.0",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedButton(onClick = { uriHandler.openUri("https://github.com/HFDLYS/BJTUselfService") }) {
                    Text("打开 GitHub 项目")
                }
                Text(
                    "Apple 端不复制 Android APK 下载流程；正式发布后由 App Store、签名安装包或项目发布页提供更新。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("本地数据与会话", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                when (state.cacheAction) {
                    OfflineCacheActionState.Clearing -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    OfflineCacheActionState.Cleared -> Feedback("离线缓存已清除；账号和主题仍保留。", false, model::dismissFeedback)
                    OfflineCacheActionState.Failed -> Feedback("离线缓存清除失败，请稍后重试。", true, model::dismissFeedback)
                    OfflineCacheActionState.Idle -> Unit
                }
                if (expanded) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        CacheClearButton(
                            onClick = { confirmClear = true },
                            enabled = state.cacheAction != OfflineCacheActionState.Clearing,
                            modifier = Modifier.weight(1f),
                        )
                        LogoutButton(onClick = onLogout, modifier = Modifier.weight(1f))
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        CacheClearButton(
                            onClick = { confirmClear = true },
                            enabled = state.cacheAction != OfflineCacheActionState.Clearing,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        LogoutButton(onClick = onLogout, modifier = Modifier.fillMaxWidth())
                    }
                }
                Text(
                    "macOS 关闭窗口只关闭窗口，不退出账号、不清除会话。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CacheClearButton(onClick: () -> Unit, enabled: Boolean, modifier: Modifier) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier) {
        Text("清除离线缓存")
    }
}

@Composable
private fun LogoutButton(onClick: () -> Unit, modifier: Modifier) {
    Button(onClick = onClick, modifier = modifier) {
        Text("退出并清除登录信息")
    }
}

@Composable
private fun AutoSyncSettingRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingCard(title: String, value: String) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun Feedback(message: String, isError: Boolean, onDismiss: () -> Unit) {
    Surface(
        color = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                message,
                color = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    }
}
