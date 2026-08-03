package team.bjtuss.bjtuselfservice.shared.feature.mailbox

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import team.bjtuss.bjtuselfservice.shared.network.SchoolHttpTransport
import team.bjtuss.bjtuselfservice.shared.webview.SchoolWebDomainPolicy
import team.bjtuss.bjtuselfservice.shared.webview.WebCookie
import team.bjtuss.bjtuselfservice.shared.webview.WebPageRequest
import team.bjtuss.bjtuselfservice.shared.webview.WebPageValidation

private const val MAILBOX_URL = "https://mis.bjtu.edu.cn/module/module/26/"
private const val MAILBOX_HOST = "mis.bjtu.edu.cn"

sealed interface MailboxUiState {
    data object Idle : MailboxUiState
    data object Preparing : MailboxUiState
    data class Ready(val request: WebPageRequest) : MailboxUiState
    data object SessionUnavailable : MailboxUiState
}

class MailboxScreenModel(
    private val transport: SchoolHttpTransport,
) {
    private val mutableState = MutableStateFlow<MailboxUiState>(MailboxUiState.Idle)
    val state: StateFlow<MailboxUiState> = mutableState.asStateFlow()

    suspend fun initialize() {
        if (mutableState.value == MailboxUiState.Idle) refresh()
    }

    suspend fun refresh() {
        mutableState.value = MailboxUiState.Preparing
        val cookies = runCatching { transport.sessionCookiesFor(MAILBOX_URL) }
            .getOrElse {
                mutableState.value = MailboxUiState.SessionUnavailable
                return
            }
        if (cookies.isEmpty()) {
            mutableState.value = MailboxUiState.SessionUnavailable
            return
        }
        // 只导出对目标 URL 生效的 Cookie，并把 Domain 收窄到当前 MIS host。
        val request = WebPageRequest(
            url = MAILBOX_URL,
            title = "校内邮箱",
            cookies = cookies.map { cookie ->
                WebCookie(
                    name = cookie.name,
                    value = cookie.value,
                    domain = MAILBOX_HOST,
                    path = cookie.path,
                    secure = cookie.secure,
                )
            },
        )
        mutableState.value = if (SchoolWebDomainPolicy.validate(request) == WebPageValidation.Allowed) {
            MailboxUiState.Ready(request)
        } else {
            MailboxUiState.SessionUnavailable
        }
    }
}
