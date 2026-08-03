package team.bjtuss.bjtuselfservice.shared.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class LoginHtmlParserTest {
    @Test
    fun parsesCaptchaForm() {
        val result = assertIs<ParseResult.Success<CasLoginForm>>(
            parseCasLoginForm(
                """<form><input name="csrfmiddlewaretoken" value="csrf-fixture"><input id="id_captcha_0" value="captcha-fixture"></form>""",
            ),
        )
        assertEquals("csrf-fixture", result.value.csrfToken)
        assertEquals("captcha-fixture", result.value.captchaId)
    }

    @Test
    fun parsesProfileAndAcademicRedirect() {
        val profile = assertIs<ParseResult.Success<StudentProfile>>(
            parseMisStudentProfile(
                """
                <section class="name_right">
                  <h3><a>测试用户，欢迎</a></h3>
                  <div class="nr_con"><span>身份：学生</span><span>部门：测试学院</span></div>
                </section>
                """.trimIndent(),
                studentId = "student-fixture",
            ),
        )
        assertEquals("测试用户", profile.value.name)
        assertEquals("学生", profile.value.identity)
        assertEquals("测试学院", profile.value.department)

        assertEquals(
            ParseResult.Success("https://aa.example/redirect"),
            parseAcademicRedirectUrl("""<form id="redirect" action="https://aa.example/redirect"></form>"""),
        )
    }

    @Test
    fun missingSensitiveFieldsFailsWithoutIncludingHtml() {
        assertEquals(ParseResult.Failure("captcha_id"), parseCasLoginForm("<html></html>"))
        assertEquals(ParseResult.Failure("name"), parseMisStudentProfile("<html></html>", "student"))
    }
}
