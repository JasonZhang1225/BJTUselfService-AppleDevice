package team.bjtuss.bjtuselfservice.shared.data.otherfunction

import com.fleeksoft.ksoup.Ksoup

/**
 * 校历页面 HTML 解析。
 *
 * 行为基线：原 Android App 用 Jsoup 提取 <script> 内容中的 `url: "..."`，
 * 拼成 `https://bksy.bjtu.edu.cn<postfix>`。本实现保持该行为，但要求：
 * 1. 单双引号均可；
 * 2. 缺失字段/格式异常返回明确的 Failure，不抛异常、不泄露 HTML 正文；
 * 3. 解析结果只返回 URL 尾部（postfix），域名边界由远端数据源强制校验。
 */
sealed interface CalendarUrlParseResult {
    data class Success(val postfix: String) : CalendarUrlParseResult
    data class Failure(val reason: CalendarParseFailure) : CalendarUrlParseResult
}

enum class CalendarParseFailure {
    /** HTML 解析异常。 */
    MALFORMED_HTML,

    /** 没有 script 标签。 */
    MISSING_SCRIPT,

    /** script 中没有 url 字段。 */
    MISSING_URL_FIELD,

    /** url 字段存在但引号不闭合或为空。 */
    MALFORMED_URL_VALUE,
}

private val urlFieldRegex = Regex("""url\s*:\s*([""'])(.*?)\1""")

/**
 * 从校历页面 HTML 中提取 `url: "..."` 的尾部路径。
 * 不会把 HTML 正文写入日志或异常消息。
 */
fun parseSchoolCalendarPostfix(html: String): CalendarUrlParseResult = try {
    val document = Ksoup.parse(html)
    val scriptElements = document.select("script")
    if (scriptElements.isEmpty()) {
        CalendarUrlParseResult.Failure(CalendarParseFailure.MISSING_SCRIPT)
    } else {
        // script.data() 取脚本原始文本，避免 HTML 转义影响引号匹配。
        // 真实页面有多个 script 块，url 字段不一定在第一个，必须聚合全部内容。
        val scriptContent = scriptElements.joinToString("\n") { element ->
            element.data().ifBlank { element.html() }
        }
        val match = urlFieldRegex.find(scriptContent)
        if (match == null) {
            CalendarUrlParseResult.Failure(CalendarParseFailure.MISSING_URL_FIELD)
        } else {
            val postfix = match.groupValues[2].trim()
            if (postfix.isBlank()) {
                CalendarUrlParseResult.Failure(CalendarParseFailure.MALFORMED_URL_VALUE)
            } else {
                CalendarUrlParseResult.Success(postfix)
            }
        }
    }
} catch (_: Exception) {
    CalendarUrlParseResult.Failure(CalendarParseFailure.MALFORMED_HTML)
}

/**
 * 成绩单下载 URL 的固定构造。
 * 路径与原 App 完全一致；仅按语言切换 type 参数。
 */
fun reportCardDownloadUrl(language: team.bjtuss.bjtuselfservice.shared.domain.otherfunction.ReportCardLanguage): String {
    val type = when (language) {
        team.bjtuss.bjtuselfservice.shared.domain.otherfunction.ReportCardLanguage.CHINESE -> "card_cn_sign"
        team.bjtuss.bjtuselfservice.shared.domain.otherfunction.ReportCardLanguage.ENGLISH -> "card_en_sign"
    }
    return "https://aa.bjtu.edu.cn/score/scorecard/stu/5201314/download_pdf/?type=$type&has_advance_query="
}
