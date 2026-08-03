package team.bjtuss.bjtuselfservice.shared.auth

import com.fleeksoft.ksoup.Ksoup

data class CasLoginForm(
    val csrfToken: String,
    val captchaId: String,
)

sealed interface ParseResult<out T> {
    data class Success<T>(val value: T) : ParseResult<T>
    data class Failure(val missingField: String) : ParseResult<Nothing>
}

fun parseCasLoginForm(html: String): ParseResult<CasLoginForm> {
    val document = Ksoup.parse(html)
    val captchaId = document.selectFirst("input#id_captcha_0")?.attr("value").orEmpty()
    if (captchaId.isBlank()) return ParseResult.Failure("captcha_id")
    val csrf = document.selectFirst("input[name=csrfmiddlewaretoken]")?.attr("value").orEmpty()
    if (csrf.isBlank()) return ParseResult.Failure("csrf_token")
    return ParseResult.Success(CasLoginForm(csrfToken = csrf, captchaId = captchaId))
}

fun parseMisStudentProfile(
    html: String,
    studentId: String,
): ParseResult<StudentProfile> {
    val document = Ksoup.parse(html)
    val rawName = document.selectFirst(".name_right > h3 > a")?.text().orEmpty()
    if (rawName.isBlank()) return ParseResult.Failure("name")

    val spans = document.select(".name_right .nr_con span")
    val identity = spans.firstOrNull { it.text().contains("身份") }
        ?.text()?.removePrefix("身份：").orEmpty()
    if (identity.isBlank()) return ParseResult.Failure("identity")
    val department = spans.firstOrNull { it.text().contains("部门") }
        ?.text()?.removePrefix("部门：").orEmpty()
    if (department.isBlank()) return ParseResult.Failure("department")

    return ParseResult.Success(
        StudentProfile(
            name = rawName.substringBefore("，"),
            studentId = studentId,
            identity = identity,
            department = department,
        ),
    )
}

fun parseAcademicRedirectUrl(html: String): ParseResult<String> {
    val action = Ksoup.parse(html).selectFirst("form#redirect")?.attr("action").orEmpty()
    return if (action.isBlank()) ParseResult.Failure("redirect_action") else ParseResult.Success(action)
}
