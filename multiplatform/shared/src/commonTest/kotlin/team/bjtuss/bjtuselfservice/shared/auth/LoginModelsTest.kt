package team.bjtuss.bjtuselfservice.shared.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class LoginModelsTest {
    @Test
    fun credentialsNeverExposePasswordInToString() {
        val credentials = Credentials("student", "top-secret")
        assertFalse(credentials.toString().contains("top-secret"))
        assertFalse(credentials.toString().contains("student"))
        assertEquals(true, credentials.isValid)
    }

    @Test
    fun stateMachineOnlyAcceptsOrderedTransitions() {
        val challenge = CaptchaChallenge("https://cas.example/login", "csrf", "captcha", byteArrayOf(1))
        val profile = StudentProfile("测试用户", "student", "学生", "测试学院")

        var state: LoginState = LoginState.SignedOut
        state = reduceLoginState(state, LoginEvent.Start)
        assertEquals(LoginState.CheckingSession, state)
        state = reduceLoginState(state, LoginEvent.ChallengeLoaded(challenge))
        state = reduceLoginState(state, LoginEvent.SubmitCredentials)
        assertEquals(LoginState.SubmittingCredentials, state)
        state = reduceLoginState(state, LoginEvent.MisAuthenticated(profile))
        state = reduceLoginState(state, LoginEvent.AcademicLinked)
        assertEquals(LoginState.SignedIn(profile), state)
        assertEquals(LoginState.SignedOut, reduceLoginState(state, LoginEvent.Logout))
    }

    @Test
    fun outOfOrderEventsDoNotSkipAuthenticationSteps() {
        val profile = StudentProfile("测试用户", "student", "学生", "测试学院")
        val state: LoginState = LoginState.SignedOut

        assertEquals(state, reduceLoginState(state, LoginEvent.MisAuthenticated(profile)))
        assertEquals(state, reduceLoginState(state, LoginEvent.AcademicLinked))
    }
}
