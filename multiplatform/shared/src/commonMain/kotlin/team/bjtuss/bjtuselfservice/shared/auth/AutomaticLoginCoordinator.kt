package team.bjtuss.bjtuselfservice.shared.auth

const val DEFAULT_AUTO_LOGIN_ATTEMPTS = 3

interface LoginAutomationGateway {
    suspend fun requestCaptchaChallenge(studentId: String): ChallengeResult

    suspend fun authenticateMis(
        credentials: Credentials,
        challenge: CaptchaChallenge,
        captchaAnswer: String,
    ): AuthenticationResult
}

sealed interface AutomaticLoginResult {
    data class SessionActive(val profile: StudentProfile) : AutomaticLoginResult
    data class Authenticated(val profile: StudentProfile, val attempts: Int) : AutomaticLoginResult
    data class ManualRequired(
        val challenge: CaptchaChallenge?,
        val reason: LoginFailure,
        val attempts: Int,
    ) : AutomaticLoginResult
}

class AutomaticLoginCoordinator(
    private val gateway: LoginAutomationGateway,
    private val captchaRecognizer: CaptchaRecognizer,
    private val maximumAttempts: Int = DEFAULT_AUTO_LOGIN_ATTEMPTS,
) {
    init {
        require(maximumAttempts in 1..3)
    }

    suspend fun login(
        credentials: Credentials,
        onAttempt: (Int, Int) -> Unit = { _, _ -> },
    ): AutomaticLoginResult {
        if (!credentials.isValid) {
            return AutomaticLoginResult.ManualRequired(
                challenge = null,
                reason = LoginFailure.INVALID_CREDENTIALS,
                attempts = 0,
            )
        }

        var latestChallenge: CaptchaChallenge? = null
        var latestFailure = LoginFailure.CAPTCHA_RECOGNITION_FAILED
        repeat(maximumAttempts) { index ->
            val attempt = index + 1
            onAttempt(attempt, maximumAttempts)
            when (val challenge = gateway.requestCaptchaChallenge(credentials.username)) {
                is ChallengeResult.SessionActive -> {
                    return AutomaticLoginResult.SessionActive(challenge.profile)
                }
                is ChallengeResult.Failed -> {
                    latestFailure = challenge.reason
                }
                is ChallengeResult.Ready -> {
                    latestChallenge = challenge.challenge
                    when (val recognition = captchaRecognizer.recognize(challenge.challenge.imageBytes)) {
                        is CaptchaRecognitionResult.Failed -> {
                            latestFailure = if (
                                recognition.reason == CaptchaRecognitionFailure.MODEL_UNAVAILABLE
                            ) {
                                return AutomaticLoginResult.ManualRequired(
                                    challenge = latestChallenge,
                                    reason = LoginFailure.CAPTCHA_RECOGNITION_FAILED,
                                    attempts = attempt,
                                )
                            } else {
                                LoginFailure.CAPTCHA_RECOGNITION_FAILED
                            }
                        }
                        is CaptchaRecognitionResult.Success -> {
                            when (
                                val authentication = gateway.authenticateMis(
                                    credentials,
                                    challenge.challenge,
                                    recognition.value.answer,
                                )
                            ) {
                                is AuthenticationResult.Success -> {
                                    return AutomaticLoginResult.Authenticated(
                                        profile = authentication.profile,
                                        attempts = attempt,
                                    )
                                }
                                is AuthenticationResult.Failed -> {
                                    latestFailure = authentication.reason
                                }
                            }
                        }
                    }
                }
            }
        }
        return AutomaticLoginResult.ManualRequired(
            challenge = latestChallenge,
            reason = latestFailure,
            attempts = maximumAttempts,
        )
    }
}
