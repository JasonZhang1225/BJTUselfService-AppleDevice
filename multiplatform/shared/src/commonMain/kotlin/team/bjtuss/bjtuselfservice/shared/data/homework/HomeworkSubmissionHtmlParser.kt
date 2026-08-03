package team.bjtuss.bjtuselfservice.shared.data.homework

import com.fleeksoft.ksoup.Ksoup
import team.bjtuss.bjtuselfservice.shared.domain.homework.SubmittedHomeworkAttachment

sealed interface SubmittedHomeworkParseResult {
    data class Success(val attachments: List<SubmittedHomeworkAttachment>) : SubmittedHomeworkParseResult
    data object Failure : SubmittedHomeworkParseResult
}

private val submittedAttachmentPattern =
    """\('([^']*)',\s*'([^']*)',\s*'([^']*)'\)""".toRegex()

fun parseSubmittedHomeworkAttachments(html: String): SubmittedHomeworkParseResult = try {
    val document = Ksoup.parse(html)
    if (document.text().contains("系统发生了未处理的异常")) {
        return SubmittedHomeworkParseResult.Failure
    }
    val attachments = document.select("div.homeworkContent").mapNotNull { element ->
        val match = submittedAttachmentPattern.find(element.attr("onclick")) ?: return@mapNotNull null
        val (path, rawFileName, id) = match.destructured
        val fileName = rawFileName.replace('+', ' ').trim()
        if (path.isBlank() || fileName.isBlank() || id.isBlank()) return@mapNotNull null
        SubmittedHomeworkAttachment(
            id = id,
            fileName = fileName,
            sourcePath = path,
        )
    }.distinctBy { it.id to it.fileName }
    SubmittedHomeworkParseResult.Success(attachments)
} catch (_: Exception) {
    SubmittedHomeworkParseResult.Failure
}
