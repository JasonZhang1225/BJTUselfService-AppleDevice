package team.bjtuss.bjtuselfservice.kmp

import android.os.Bundle
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
            )
        }
    }
}
