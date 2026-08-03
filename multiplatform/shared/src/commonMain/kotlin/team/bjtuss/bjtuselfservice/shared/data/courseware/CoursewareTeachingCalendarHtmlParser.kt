package team.bjtuss.bjtuselfservice.shared.data.courseware

import com.fleeksoft.ksoup.Ksoup

sealed interface CoursewareHtmlParseResult {
    data class Success(val value: String) : CoursewareHtmlParseResult
    data class Failure(val field: String) : CoursewareHtmlParseResult
}

fun parseCoursewareTeacherId(html: String): CoursewareHtmlParseResult = try {
    Ksoup.parse(html).selectFirst("input#teacherId")?.attr("value").orEmpty().trim()
        .takeIf(String::isNotBlank)
        ?.let(CoursewareHtmlParseResult::Success)
        ?: CoursewareHtmlParseResult.Failure("element")
} catch (_: Exception) {
    CoursewareHtmlParseResult.Failure("html")
}

fun parseTeachingCalendarFrameUrl(html: String): CoursewareHtmlParseResult = try {
    Ksoup.parse(html).selectFirst("iframe#pdfIframe")?.attr("src").orEmpty().trim()
        .takeIf(String::isNotBlank)
        ?.let(CoursewareHtmlParseResult::Success)
        ?: CoursewareHtmlParseResult.Failure("element")
} catch (_: Exception) {
    CoursewareHtmlParseResult.Failure("html")
}
