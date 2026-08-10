package team.bjtuss.bjtuselfservice.shared.auth

/** 按桌面平台选择原生可用的验证码推理后端。 */
fun createDesktopCaptchaRecognizer(): CaptchaRecognizer =
    if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
        WindowsTorchCaptchaRecognizer()
    } else {
        DesktopCoreMlCaptchaRecognizer()
    }
