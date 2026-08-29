package team.bjtuss.bjtuselfservice.shared.data.mailbox

import io.ktor.http.Url
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import team.bjtuss.bjtuselfservice.shared.domain.mailbox.MailboxPage
import team.bjtuss.bjtuselfservice.shared.domain.mailbox.MailMessage
import team.bjtuss.bjtuselfservice.shared.network.SchoolHttpMethod
import team.bjtuss.bjtuselfservice.shared.network.SchoolHttpRequest
import team.bjtuss.bjtuselfservice.shared.network.SchoolHttpResponse
import team.bjtuss.bjtuselfservice.shared.network.SchoolHttpTransport

private const val MIS_MAILBOX_ENTRY_URL = "https://mis.bjtu.edu.cn/module/module/26/"
private const val COREMAIL_ORIGIN = "https://mail.bjtu.edu.cn"
private const val COREMAIL_HOST = "mail.bjtu.edu.cn"
private const val COREMAIL_JSON_PATH = "/coremail/s/json"
private const val COREMAIL_READ_MESSAGE_URL = "$COREMAIL_ORIGIN/coremail/XT/jsp/readMessage.jsp"
private const val COREMAIL_INDEX_URL = "$COREMAIL_ORIGIN/coremail/XT/index.jsp"

enum class MailboxRemoteFailure {
    NETWORK,
    PARSE,
    SESSION_EXPIRED,
}

class MailboxRemoteException(
    val reason: MailboxRemoteFailure,
) : Exception("Mailbox request failed: ${reason.name}")

interface MailboxRemoteDataSource {
    suspend fun listMessages(folderId: Int, start: Int, limit: Int, descending: Boolean): MailboxPage
    suspend fun readMessage(messageId: String): MailMessage
}

/**
 * Coremail 的只读 HTTP 适配器。
 * 先通过 MIS 入口建立 Coremail 会话，再调用已由 Chrome DevTools 取证的列表/详情接口。
 */
class SchoolMailboxRemoteDataSource(
    private val transport: SchoolHttpTransport,
) : MailboxRemoteDataSource {
    private var coremailSessionId: String? = null

    override suspend fun listMessages(
        folderId: Int,
        start: Int,
        limit: Int,
        descending: Boolean,
    ): MailboxPage {
        val sid = ensureCoremailSession()
        val body = buildJsonObject {
            put("start", start)
            put("limit", limit)
            put("mode", "count")
            put("order", "date")
            put("desc", descending)
            put("returnTotal", true)
            put("returnTag", false)
            put("summaryWindowSize", limit)
            put("fid", folderId)
            put("mboxa", "")
            put("topFirst", true)
        }.toString()
        val response = execute(
            SchoolHttpRequest(
                method = SchoolHttpMethod.POST,
                url = buildJsonEndpoint(sid, "mbox:listMessages"),
                headers = coremailJsonHeaders,
                rawBody = body.encodeToByteArray(),
                rawBodyContentType = "text/x-json; tz=\"Asia/Shanghai\"",
            ),
        )
        return when (val parsed = parseMailboxMessageList(response.bodyText())) {
            is MailboxJsonParseResult.Success -> parsed.value
            is MailboxJsonParseResult.Failure -> {
                if (parsed.field == "code") sessionExpired()
                parse(parsed.field)
            }
        }
    }

    override suspend fun readMessage(messageId: String): MailMessage {
        require(messageId.isNotBlank()) { "messageId must not be blank" }
        // 详情页也可被独立调用；不要假设列表请求一定已经成功建立了 sid。
        ensureCoremailSession()
        val response = execute(
            SchoolHttpRequest(
                method = SchoolHttpMethod.POST,
                url = COREMAIL_READ_MESSAGE_URL,
                headers = coremailFormHeaders,
                formFields = mapOf(
                    "mid" to messageId,
                    "mboxa" to "",
                    "part" to "",
                    "mailCipherPassword" to "",
                ),
            ),
        )
        return when (val parsed = parseMailboxMessage(response.bodyText(), messageId)) {
            is MailboxJsonParseResult.Success -> parsed.value
            is MailboxJsonParseResult.Failure -> {
                if (parsed.field == "code") sessionExpired()
                parse(parsed.field)
            }
        }
    }

    private suspend fun ensureCoremailSession(): String {
        coremailSessionId?.let { return it }
        val response = executeRaw(
            SchoolHttpRequest(
                method = SchoolHttpMethod.GET,
                url = MIS_MAILBOX_ENTRY_URL,
                headers = mapOf("Accept" to "text/html,application/xhtml+xml;q=0.9,*/*;q=0.8"),
            ),
        )
        if (response.statusCode !in 200..299) network()
        val finalUrl = runCatching { Url(response.finalUrl) }.getOrNull()
        if (finalUrl?.host?.lowercase() != COREMAIL_HOST) sessionExpired()
        val sid = finalUrl.parameters["sid"]?.takeIf(String::isNotBlank) ?: sessionExpired()
        coremailSessionId = sid
        return sid
    }

    private suspend fun execute(request: SchoolHttpRequest): SchoolHttpResponse {
        val response = executeRaw(request)
        if (response.statusCode == 401 || response.statusCode == 403) sessionExpired()
        if (response.statusCode !in 200..299) network()
        val host = runCatching { Url(response.finalUrl).host.lowercase() }.getOrNull()
        if (host != COREMAIL_HOST) sessionExpired()
        return response
    }

    private suspend fun executeRaw(request: SchoolHttpRequest): SchoolHttpResponse = try {
        transport.execute(request)
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        network()
    }

    private fun buildJsonEndpoint(sessionId: String, function: String): String =
        "$COREMAIL_ORIGIN$COREMAIL_JSON_PATH?sid=${sessionId.encodeURLParameter()}&func=${function.encodeURLParameter()}"

    private fun network(): Nothing = throw MailboxRemoteException(MailboxRemoteFailure.NETWORK)
    private fun parse(field: String): Nothing = throw MailboxRemoteException(
        MailboxRemoteFailure.PARSE,
    )
    private fun sessionExpired(): Nothing {
        coremailSessionId = null
        throw MailboxRemoteException(MailboxRemoteFailure.SESSION_EXPIRED)
    }

    private companion object {
        val coremailJsonHeaders = mapOf(
            "Accept" to "text/x-json",
            "Content-Type" to "text/x-json; tz=\"Asia/Shanghai\"",
            "X-Requested-With" to "XMLHttpRequest",
            "Referer" to COREMAIL_INDEX_URL,
        )
        val coremailFormHeaders = mapOf(
            "Accept" to "text/x-json",
            "X-Requested-With" to "XMLHttpRequest",
            "Referer" to COREMAIL_INDEX_URL,
        )
    }
}
