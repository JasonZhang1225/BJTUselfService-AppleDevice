package team.bjtuss.bjtuselfservice.shared

import androidx.compose.ui.window.ComposeUIViewController
import team.bjtuss.bjtuselfservice.shared.cache.createIosCacheStore
import team.bjtuss.bjtuselfservice.shared.security.createIosAccountSecurityStore
import team.bjtuss.bjtuselfservice.shared.files.IosHomeworkFileGateway
import team.bjtuss.bjtuselfservice.shared.auth.IosCoreMlCaptchaRecognizer
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController = run {
    val accountSecurityStore = createIosAccountSecurityStore()
    val cacheStoreHandle = createIosCacheStore()
    lateinit var controller: UIViewController
    val homeworkFileGateway = IosHomeworkFileGateway { controller }
    val captchaRecognizer = IosCoreMlCaptchaRecognizer()
    controller = ComposeUIViewController {
        App(
            accountSecurityStore = accountSecurityStore,
            cacheStoreHandle = cacheStoreHandle,
            homeworkFileGateway = homeworkFileGateway,
            coursewareDirectoryGateway = homeworkFileGateway,
            captchaRecognizer = captchaRecognizer,
        )
    }
    controller
}
