package team.bjtuss.bjtuselfservice.shared.data.phyvlab

import team.bjtuss.bjtuselfservice.shared.auth.AutomaticLoginCoordinator
import team.bjtuss.bjtuselfservice.shared.auth.AutomaticLoginResult
import team.bjtuss.bjtuselfservice.shared.auth.CaptchaRecognizer
import team.bjtuss.bjtuselfservice.shared.auth.Credentials
import team.bjtuss.bjtuselfservice.shared.auth.LoginAutomationGateway
import team.bjtuss.bjtuselfservice.shared.auth.SchoolLoginProtocol

/**
 * 在物理在线自己的 Moodle 会话失效时，尝试在 App 内恢复 CAS 会话。
 *
 * Moodle 的 OAuth 登录与智慧教学使用同一 CAS，但 CAS Cookie 的有效期和
 * MoodleSession 的有效期并不相同。不能依赖系统浏览器把 Cookie 传回 App，
 * 因此这里复用已经登录 App 的凭据提供器，只在内存中重新走现有验证码认证。
 * 凭据提供器由登录界面持有，不在此类中落盘或写日志。
 */
class PhyVlabSessionRecovery(
    private val protocol: SchoolLoginProtocol,
    private val captchaRecognizer: CaptchaRecognizer,
    private val credentialsProvider: () -> Credentials?,
) {
    suspend fun attempt(): Boolean {
        val credentials = credentialsProvider()?.takeIf { it.isValid } ?: return false
        val gateway = object : LoginAutomationGateway {
            override suspend fun requestCaptchaChallenge(studentId: String) =
                protocol.requestFreshCaptchaChallenge(studentId)

            override suspend fun authenticateMis(
                credentials: Credentials,
                challenge: team.bjtuss.bjtuselfservice.shared.auth.CaptchaChallenge,
                captchaAnswer: String,
            ) = protocol.authenticateMis(credentials, challenge, captchaAnswer)
        }
        val result = AutomaticLoginCoordinator(
            gateway = gateway,
            captchaRecognizer = captchaRecognizer,
        ).login(credentials)
        return when (result) {
            is AutomaticLoginResult.SessionActive,
            is AutomaticLoginResult.Authenticated,
            -> true
            is AutomaticLoginResult.ManualRequired -> false
        }
    }
}
