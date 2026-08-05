package team.bjtuss.bjtuselfservice.shared.feature.shell

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 作业 / 课件（智慧教学旧 HTTP）与教室人数（第三方明文接口）共用的授权风险说明。
 * 仅在本会话内「知道了」关闭，不替代真实网络/会话失败条。
 */
const val LEGACY_SMART_TRANSPORT_WARNING =
    "由于学校相关系统较旧，作业、课件和教室功能获取信息不支持 HTTPS 加密，采用 HTTP 明文传输，请勿在不可信网络中使用。"

@Composable
fun LegacySmartTransportWarning(modifier: Modifier = Modifier, onDismiss: (() -> Unit)? = null) {
    AppErrorBanner(
        title = "明文传输已启用",
        message = LEGACY_SMART_TRANSPORT_WARNING,
        modifier = modifier,
        onDismiss = onDismiss,
        dismissLabel = "知道了",
    )
}
