package team.bjtuss.bjtuselfservice.shared.webview

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 平台网页容器。把一个经过 `SchoolWebDomainPolicy` 校验的请求渲染为
 * 应用内网页；Cookie 同步、外部链接和系统浏览器分流由平台实现。
 *
 * 调用方必须先通过 `SchoolWebDomainPolicy.validate` 校验；
 * 校验失败的请求平台实现应直接拒绝渲染。
 */
@Composable
expect fun SchoolWebView(
    request: WebPageRequest,
    modifier: Modifier = Modifier,
    onOpenExternal: (String) -> Unit = {},
)

/** 用系统浏览器/默认方式打开一个外部链接。 */
expect fun openExternalUrl(url: String)
