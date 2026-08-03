package team.bjtuss.bjtuselfservice.shared.webview

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@Composable
actual fun SchoolWebView(
    request: WebPageRequest,
    modifier: Modifier,
    onOpenExternal: (String) -> Unit,
) {
    if (SchoolWebDomainPolicy.validate(request) != WebPageValidation.Allowed) return
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                val cookieManager = CookieManager.getInstance()
                cookieManager.setAcceptCookie(true)
                request.cookies.forEach { cookie ->
                    val securePart = if (cookie.secure) "; Secure" else ""
                    cookieManager.setCookie(
                        "https://${cookie.domain.removePrefix(".")}",
                        "${cookie.name}=${cookie.value}; Domain=${cookie.domain}; Path=${cookie.path}$securePart",
                    )
                }
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView,
                        request2: WebResourceRequest,
                    ): Boolean {
                        val url = request2.url.toString()
                        return if (SchoolWebDomainPolicy.isSchoolHost(url)) {
                            false
                        } else {
                            if (request.externalLinkPolicy == ExternalLinkPolicy.OPEN_EXTERNALLY) {
                                onOpenExternal(url)
                            }
                            true
                        }
                    }
                }
                loadUrl(request.url)
            }
        },
    )
}

actual fun openExternalUrl(url: String) {
    // 由调用方在 Activity/Compose 环境中通过平台上下文处理；此处保持无操作，
    // Android 端的外部链接分流在 WebViewClient 中通过系统 Intent 完成。
}

internal fun externalIntent(url: String): Intent? =
    if (url.startsWith("https://")) {
        Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    } else {
        null
    }
