package team.bjtuss.bjtuselfservice.shared.data.otherfunction

import kotlinx.coroutines.CancellationException
import io.ktor.http.decodeURLPart
import io.ktor.http.encodeURLPath
import team.bjtuss.bjtuselfservice.shared.domain.homework.HomeworkFileContent
import team.bjtuss.bjtuselfservice.shared.domain.otherfunction.ReportCardLanguage
import team.bjtuss.bjtuselfservice.shared.network.SchoolHttpMethod
import team.bjtuss.bjtuselfservice.shared.network.SchoolHttpRequest
import team.bjtuss.bjtuselfservice.shared.network.SchoolHttpResponse
import team.bjtuss.bjtuselfservice.shared.network.SchoolHttpTransport

private const val BKSY_ORIGIN = "https://bksy.bjtu.edu.cn"
private const val AA_ORIGIN = "https://aa.bjtu.edu.cn"
private const val CALENDAR_PAGE_URL = "$BKSY_ORIGIN/Admin/SemesterTranPage.aspx?noRemark=1"

enum class OtherFunctionRemoteFailure {
    NETWORK,
    PARSE,
    SESSION_EXPIRED,
}

class OtherFunctionRemoteException(
    val reason: OtherFunctionRemoteFailure,
) : Exception("Other function request failed: ${reason.name}")

interface OtherFunctionRemoteDataSource {
    suspend fun fetchCalendarFile(): HomeworkFileContent
    suspend fun fetchCalendarFileName(): String
    suspend fun fetchReportCardFile(language: ReportCardLanguage): HomeworkFileContent
}

/**
 * 通过共享 transport 访问教务处公开页面与会话接口。
 *
 * 域名安全边界：校历最终 PDF 仍必须位于 bksy.bjtu.edu.cn；
 * 成绩单必须位于 aa.bjtu.edu.cn，若被重定向回登录页则判为会话失效。
 */
class SchoolOtherFunctionRemoteDataSource(
    private val transport: SchoolHttpTransport,
) : OtherFunctionRemoteDataSource {

    override suspend fun fetchCalendarFile(): HomeworkFileContent {
        val postfix = fetchCalendarPostfix()
        val calendarUrl = postfix.toAllowedCalendarUrl() ?: parse()
        val fileResponse = execute(
            SchoolHttpRequest(
                method = SchoolHttpMethod.GET,
                url = calendarUrl,
                headers = mapOf("Accept" to "application/pdf,image/*;q=0.8,*/*;q=0.5"),
            ),
        )
        if (fileResponse.statusCode !in 200..299) network()
        if (!fileResponse.finalUrl.isAllowedCalendarUrl()) parse()
        if (fileResponse.body.isEmpty()) parse()
        return HomeworkFileContent(
            fileName = postfix.substringAfterLast('/'),
            contentType = fileResponse.contentTypeOrDefault(),
            bytes = fileResponse.body,
        )
    }

    /**
     * 只解析校历页上的最新文件路径并返回文件名（如 "2024-2025校历.pdf"），
     * 供页面在下载前展示“当前最新”信息；不下载文件本体。
     */
    override suspend fun fetchCalendarFileName(): String =
        fetchCalendarPostfix().substringAfterLast('/').decodeURLPart()

    /** 请求校历页并解析出文件路径尾部；页面非 200 或解析失败按既有语义抛错。 */
    private suspend fun fetchCalendarPostfix(): String {
        val pageResponse = execute(
            SchoolHttpRequest(
                method = SchoolHttpMethod.GET,
                url = CALENDAR_PAGE_URL,
                headers = mapOf("Accept" to "text/html,application/xhtml+xml;q=0.9,*/*;q=0.8"),
            ),
        )
        if (pageResponse.statusCode !in 200..299) network()
        return when (val parsed = parseSchoolCalendarPostfix(pageResponse.bodyText())) {
            is CalendarUrlParseResult.Failure -> parse()
            is CalendarUrlParseResult.Success -> parsed.postfix
        }
    }

    override suspend fun fetchReportCardFile(language: ReportCardLanguage): HomeworkFileContent {
        val url = reportCardDownloadUrl(language)
        val response = execute(
            SchoolHttpRequest(
                method = SchoolHttpMethod.GET,
                url = url,
                headers = mapOf("Accept" to "application/pdf;q=0.9,*/*;q=0.5"),
            ),
        )
        if (response.statusCode !in 200..299) network()
        if (!response.finalUrl.isAllowedAaUrl()) sessionExpired()
        // 会话失效时服务器通常返回 HTML 登录页而不是 PDF。
        val contentType = response.contentTypeOrDefault()
        if (response.body.isEmpty() ||
            (!contentType.equals("application/pdf", ignoreCase = true) && !response.body.isPdfBytes())
        ) {
            sessionExpired()
        }
        val fileName = when (language) {
            ReportCardLanguage.CHINESE -> "中文成绩单.pdf"
            ReportCardLanguage.ENGLISH -> "英文成绩单.pdf"
        }
        return HomeworkFileContent(
            fileName = fileName,
            contentType = "application/pdf",
            bytes = response.body,
        )
    }

    private suspend fun execute(request: SchoolHttpRequest): SchoolHttpResponse = try {
        transport.execute(request)
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        network()
    }
}

private fun String.toAllowedCalendarUrl(): String? {
    // 解析出的路径可能含中文（如 2024-2025校历.pdf）。服务器对未编码的
    // 非 ASCII 路径返回 404，必须百分号编码；已含 % 的视为已编码不重复处理。
    val encoded = if ('%' in this) this else encodeURLPath()
    val resolved = when {
        encoded.startsWith('/') -> "$BKSY_ORIGIN$encoded"
        encoded.startsWith("https://", ignoreCase = true) -> encoded
        else -> return null
    }
    return resolved.takeIf(String::isAllowedCalendarUrl)
}

private fun String.isAllowedCalendarUrl(): Boolean {
    if (!startsWith("https://", ignoreCase = true)) return false
    val host = substringAfter("https://").substringBefore('/').substringBefore('?').substringBefore('#')
        .substringAfterLast('@').substringBefore(':').lowercase()
    return host == "bksy.bjtu.edu.cn"
}

private fun String.isAllowedAaUrl(): Boolean {
    if (!startsWith("https://", ignoreCase = true)) return false
    val host = substringAfter("https://").substringBefore('/').substringBefore('?').substringBefore('#')
        .substringAfterLast('@').substringBefore(':').lowercase()
    return host == "aa.bjtu.edu.cn"
}

private fun ByteArray.isPdfBytes(): Boolean = size >= 5 &&
    this[0] == '%'.code.toByte() &&
    this[1] == 'P'.code.toByte() &&
    this[2] == 'D'.code.toByte() &&
    this[3] == 'F'.code.toByte() &&
    this[4] == '-'.code.toByte()

private fun SchoolHttpResponse.contentTypeOrDefault(): String = headers.entries
    .firstOrNull { (name, _) -> name.equals("Content-Type", ignoreCase = true) }
    ?.value?.firstOrNull()?.substringBefore(';')?.trim()
    ?.takeIf(String::isNotBlank)
    ?: "application/octet-stream"

private fun network(): Nothing = throw OtherFunctionRemoteException(OtherFunctionRemoteFailure.NETWORK)
private fun parse(): Nothing = throw OtherFunctionRemoteException(OtherFunctionRemoteFailure.PARSE)
private fun sessionExpired(): Nothing = throw OtherFunctionRemoteException(OtherFunctionRemoteFailure.SESSION_EXPIRED)
