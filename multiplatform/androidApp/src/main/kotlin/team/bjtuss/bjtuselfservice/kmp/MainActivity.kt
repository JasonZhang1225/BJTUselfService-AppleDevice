package team.bjtuss.bjtuselfservice.kmp

import android.os.Bundle
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import team.bjtuss.bjtuselfservice.shared.App
import team.bjtuss.bjtuselfservice.shared.cache.createAndroidCacheStore
import team.bjtuss.bjtuselfservice.shared.security.createAndroidAccountSecurityStore
import team.bjtuss.bjtuselfservice.shared.auth.AndroidTorchCaptchaRecognizer

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 预热 WebView 内核：首次创建 WebView 要同步初始化 Chromium（秒级），
        // 若等到进入邮箱页才初始化会卡住主线程和进页转场动画。
        runCatching { WebView(this).destroy() }
        val accountSecurityStore = createAndroidAccountSecurityStore(this)
        val cacheStoreHandle = createAndroidCacheStore(this)
        val homeworkFileGateway = AndroidHomeworkFileGateway(this)
        val captchaRecognizer = AndroidTorchCaptchaRecognizer(this)
        setContent {
            App(
                accountSecurityStore = accountSecurityStore,
                cacheStoreHandle = cacheStoreHandle,
                homeworkFileGateway = homeworkFileGateway,
                coursewareDirectoryGateway = homeworkFileGateway,
                captchaRecognizer = captchaRecognizer,
                nativeNavigationEnabled = true,
                onOpenNativeRoute = { routeId ->
                    startActivity(NativeDetailActivity.intentFor(this, routeId))
                },
                onAuthenticatedSessionChanged = AndroidAuthenticatedSessionRegistry::update,
            )
        }
    }

    override fun onDestroy() {
        if (isFinishing) AndroidAuthenticatedSessionRegistry.update(null)
        super.onDestroy()
    }
}
