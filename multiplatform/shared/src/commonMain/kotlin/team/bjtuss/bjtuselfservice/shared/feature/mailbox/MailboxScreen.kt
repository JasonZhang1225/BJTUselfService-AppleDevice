package team.bjtuss.bjtuselfservice.shared.feature.mailbox

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import team.bjtuss.bjtuselfservice.shared.PlatformFamily
import team.bjtuss.bjtuselfservice.shared.PlatformInfo
import team.bjtuss.bjtuselfservice.shared.webview.SchoolWebView

@Composable
fun MailboxWorkspace(
    model: MailboxScreenModel,
    platform: PlatformInfo,
    expanded: Boolean,
    modifier: Modifier = Modifier,
) {
    val state by model.state.collectAsState()
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    val isMac = platform.family == PlatformFamily.MacOS
    LaunchedEffect(model) {
        if (model.state.value == MailboxUiState.Idle) {
            // 首次进入先等进页转场播完再准备会话/创建 WebView：WKWebView/WebView 的首次初始化
            // 在主线程耗时明显，若与转场同帧进行会把整个进入动画卡住（2026-08-05 真机反馈）。
            delay(450)
        }
        model.initialize()
    }

    Column(modifier = modifier.fillMaxSize()) {
        when (val current = state) {
            MailboxUiState.Idle,
            MailboxUiState.Preparing,
            -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            MailboxUiState.SessionUnavailable -> Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("当前登录会话无法交给邮箱页面，请退出后重新登录。")
                Button(
                    onClick = { scope.launch { model.refresh() } },
                    modifier = Modifier.padding(top = 16.dp),
                ) { Text("重试") }
            }
            is MailboxUiState.Ready -> {
                if (isMac) {
                    // macOS 走系统浏览器：会话 Cookie 不会注入浏览器，需在学校页面登录后自动跳转邮箱。
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = if (expanded) 24.dp else 20.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            "点击跳转浏览器，在浏览器中登录，自动跳转校内邮箱。",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                        )
                        Button(
                            onClick = { uriHandler.openUri(current.request.url) },
                            modifier = Modifier.padding(top = 24.dp),
                        ) {
                            Text("打开校内邮箱")
                        }
                    }
                } else {
                    SchoolWebView(
                        request = current.request,
                        modifier = Modifier.fillMaxSize(),
                        onOpenExternal = uriHandler::openUri,
                    )
                }
            }
        }
    }
}
