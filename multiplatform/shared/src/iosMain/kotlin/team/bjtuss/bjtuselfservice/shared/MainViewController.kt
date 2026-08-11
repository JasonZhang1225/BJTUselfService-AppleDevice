package team.bjtuss.bjtuselfservice.shared

import androidx.compose.ui.window.ComposeUIViewController
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIViewController
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import team.bjtuss.bjtuselfservice.shared.cache.createIosCacheStore
import team.bjtuss.bjtuselfservice.shared.security.createIosAccountSecurityStore
import team.bjtuss.bjtuselfservice.shared.files.IosHomeworkFileGateway
import team.bjtuss.bjtuselfservice.shared.auth.IosCoreMlCaptchaRecognizer
import team.bjtuss.bjtuselfservice.shared.calendar.IosSystemCalendarGateway

// 预热 WebKit：首次创建 WKWebView 要启动 WebContent 进程并初始化渲染子系统（主线程，秒级），
// 若等到进入邮箱页才创建会卡住主线程和进页转场动画。保留引用让进程池常驻。
private var prewarmedWebView: WKWebView? = null

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
fun MainViewController(): UIViewController = run {
    createMainViewController(
        nativeNavigationEnabled = false,
        onAuthenticatedSessionChanged = {},
        onOpenNativeRoute = {},
    )
}

/** Swift UINavigationController 宿主使用的根 Compose 控制器。 */
fun NativeMainViewController(
    onAuthenticatedSessionChanged: (AuthenticatedSession?) -> Unit,
    onOpenNativeRoute: (String) -> Unit,
): UIViewController = createMainViewController(
    nativeNavigationEnabled = true,
    onAuthenticatedSessionChanged = onAuthenticatedSessionChanged,
    onOpenNativeRoute = onOpenNativeRoute,
)

/** Swift 原生导航栈中的单个 Compose 目的地控制器。 */
fun NativeDestinationViewController(
    session: AuthenticatedSession,
    routeId: String,
    onOpenNativeRoute: (String) -> Unit,
    onCloseNativeRoute: () -> Unit,
): UIViewController {
    lateinit var controller: UIViewController
    val homeworkFileGateway = IosHomeworkFileGateway { controller }
    controller = ComposeUIViewController {
        AuthenticatedDestinationApp(
            session = session,
            routeId = routeId,
            homeworkFileGateway = homeworkFileGateway,
            coursewareDirectoryGateway = homeworkFileGateway,
            onOpenNativeRoute = onOpenNativeRoute,
            onCloseNativeRoute = onCloseNativeRoute,
        )
    }
    return controller
}

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
private fun createMainViewController(
    nativeNavigationEnabled: Boolean,
    onAuthenticatedSessionChanged: (AuthenticatedSession?) -> Unit,
    onOpenNativeRoute: (String) -> Unit,
): UIViewController = run {
    if (prewarmedWebView == null) {
        prewarmedWebView = WKWebView(
            frame = CGRectMake(0.0, 0.0, 0.0, 0.0),
            configuration = WKWebViewConfiguration(),
        )
    }
    val accountSecurityStore = createIosAccountSecurityStore()
    val cacheStoreHandle = createIosCacheStore()
    lateinit var controller: UIViewController
    val homeworkFileGateway = IosHomeworkFileGateway { controller }
    val systemCalendarGateway = IosSystemCalendarGateway()
    val captchaRecognizer = IosCoreMlCaptchaRecognizer()
    controller = ComposeUIViewController {
        App(
            accountSecurityStore = accountSecurityStore,
            cacheStoreHandle = cacheStoreHandle,
            homeworkFileGateway = homeworkFileGateway,
            coursewareDirectoryGateway = homeworkFileGateway,
            systemCalendarGateway = systemCalendarGateway,
            captchaRecognizer = captchaRecognizer,
            nativeNavigationEnabled = nativeNavigationEnabled,
            onOpenNativeRoute = onOpenNativeRoute,
            onAuthenticatedSessionChanged = onAuthenticatedSessionChanged,
        )
    }
    controller
}
