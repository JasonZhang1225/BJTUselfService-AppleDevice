package team.bjtuss.bjtuselfservice.shared.feature.mailbox

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import team.bjtuss.bjtuselfservice.shared.data.mailbox.MailboxRemoteDataSource
import team.bjtuss.bjtuselfservice.shared.data.mailbox.MailboxRemoteFailure
import team.bjtuss.bjtuselfservice.shared.data.mailbox.MailboxRemoteException
import team.bjtuss.bjtuselfservice.shared.data.mailbox.SchoolMailboxRemoteDataSource
import team.bjtuss.bjtuselfservice.shared.domain.mailbox.MailComposeDraft
import team.bjtuss.bjtuselfservice.shared.domain.mailbox.MailMessage
import team.bjtuss.bjtuselfservice.shared.domain.mailbox.MailSummary
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
    data class Ready(
        val request: WebPageRequest,
        val folders: List<MailboxFolderUi> = defaultMailboxFolders,
        val selectedFolderId: Int = INBOX_FOLDER_ID,
        val messages: List<MailSummary> = emptyList(),
        val totalCount: Int = 0,
        val selectedMessage: MailMessage? = null,
        val isListLoading: Boolean = false,
        val isLoadingMore: Boolean = false,
        val hasMoreMessages: Boolean = false,
        val isMessageLoading: Boolean = false,
        val failure: MailboxFailure? = null,
        val compose: MailboxComposeState = MailboxComposeState(),
    ) : MailboxUiState
    data object SessionUnavailable : MailboxUiState
}

data class MailboxFolderUi(
    val id: Int,
    val name: String,
    val kind: MailboxFolderKind = MailboxFolderKind.CUSTOM,
    val section: MailboxFolderSection = MailboxFolderSection.PRIMARY,
    val unreadCount: Int? = null,
)

data class MailboxComposeState(
    val draft: MailComposeDraft? = null,
    val isLoading: Boolean = false,
    val isSending: Boolean = false,
    val failure: MailboxFailure? = null,
)

/** Coremail 内置文件夹的语义，用于图标和发件箱中的对方字段展示。 */
enum class MailboxFolderKind {
    INBOX,
    TODO,
    DRAFTS,
    SENT,
    DELETED,
    SPAM,
    VIRUS,
    CUSTOM,
}

enum class MailboxFolderSection {
    PRIMARY,
    OTHER,
}

enum class MailboxFailure {
    NETWORK,
    PARSE,
    SESSION_EXPIRED,
}

private const val INBOX_FOLDER_ID = 1
private const val DRAFTS_FOLDER_ID = 2
private const val SENT_FOLDER_ID = 3
private const val TODO_FOLDER_ID = -5
private const val DELETED_FOLDER_ID = 4
private const val SPAM_FOLDER_ID = 5
private const val VIRUS_FOLDER_ID = 6
private const val PAGE_SIZE = 20
private val defaultMailboxFolders = listOf(
    // Coremail 的 ztMail 顺序不是 FID：已发送在网页 hash 中是 fid=3，草稿箱是 fid=2。
    MailboxFolderUi(INBOX_FOLDER_ID, "收件箱", MailboxFolderKind.INBOX),
    MailboxFolderUi(TODO_FOLDER_ID, "待办邮件", MailboxFolderKind.TODO),
    MailboxFolderUi(DRAFTS_FOLDER_ID, "草稿箱", MailboxFolderKind.DRAFTS),
    MailboxFolderUi(SENT_FOLDER_ID, "已发送", MailboxFolderKind.SENT),
    MailboxFolderUi(DELETED_FOLDER_ID, "已删除", MailboxFolderKind.DELETED, MailboxFolderSection.OTHER),
    MailboxFolderUi(SPAM_FOLDER_ID, "垃圾邮件", MailboxFolderKind.SPAM, MailboxFolderSection.OTHER),
    MailboxFolderUi(VIRUS_FOLDER_ID, "病毒邮件", MailboxFolderKind.VIRUS, MailboxFolderSection.OTHER),
)

class MailboxScreenModel(
    private val transport: SchoolHttpTransport,
    private val remote: MailboxRemoteDataSource = SchoolMailboxRemoteDataSource(transport),
) {
    private val mutableState = MutableStateFlow<MailboxUiState>(MailboxUiState.Idle)
    val state: StateFlow<MailboxUiState> = mutableState.asStateFlow()

    private val operationMutex = Mutex()

    suspend fun initialize() {
        if (mutableState.value == MailboxUiState.Idle) refresh()
    }

    suspend fun refresh() {
        operationMutex.withLock {
            val previous = mutableState.value as? MailboxUiState.Ready
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
            if (SchoolWebDomainPolicy.validate(request) != WebPageValidation.Allowed) {
                mutableState.value = MailboxUiState.SessionUnavailable
                return
            }
            val ready = MailboxUiState.Ready(
                request = request,
                folders = previous?.folders ?: defaultMailboxFolders,
                selectedFolderId = previous?.selectedFolderId ?: INBOX_FOLDER_ID,
                isListLoading = true,
            )
            mutableState.value = ready
            loadMessages(ready.selectedFolderId)
        }
    }

    suspend fun selectFolder(folderId: Int) {
        operationMutex.withLock {
            val current = mutableState.value as? MailboxUiState.Ready ?: return
            mutableState.value = current.copy(
                selectedFolderId = folderId,
                messages = emptyList(),
                totalCount = 0,
                selectedMessage = null,
                isListLoading = true,
                isLoadingMore = false,
                hasMoreMessages = false,
                isMessageLoading = false,
                failure = null,
            )
            loadMessages(folderId)
        }
    }

    /** 加载当前文件夹的下一页；列表状态已经串行化，避免快速点击产生重复页。 */
    suspend fun loadMore() {
        operationMutex.withLock {
            val current = mutableState.value as? MailboxUiState.Ready ?: return
            if (
                current.isListLoading ||
                current.isLoadingMore ||
                !current.hasMoreMessages ||
                current.messages.isEmpty()
            ) {
                return
            }
            mutableState.value = current.copy(isLoadingMore = true, failure = null)
            try {
                val page = remote.listMessages(
                    folderId = current.selectedFolderId,
                    start = current.messages.size,
                    limit = PAGE_SIZE,
                    descending = true,
                )
                val latest = mutableState.value as? MailboxUiState.Ready ?: return
                val existingIds = latest.messages.mapTo(mutableSetOf(), MailSummary::id)
                val merged = latest.messages + page.messages.filter { existingIds.add(it.id) }
                mutableState.value = latest.copy(
                    messages = merged,
                    totalCount = maxOf(latest.totalCount, page.totalCount),
                    isLoadingMore = false,
                    hasMoreMessages = merged.size < page.totalCount && page.messages.isNotEmpty(),
                    failure = null,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: MailboxRemoteException) {
                mutableState.value = (mutableState.value as? MailboxUiState.Ready)?.copy(
                    isLoadingMore = false,
                    failure = error.reason.toUiFailure(),
                ) ?: return
            } catch (_: Exception) {
                mutableState.value = (mutableState.value as? MailboxUiState.Ready)?.copy(
                    isLoadingMore = false,
                    failure = MailboxFailure.NETWORK,
                ) ?: return
            }
        }
    }

    suspend fun openMessage(message: MailSummary) {
        operationMutex.withLock {
            val current = mutableState.value as? MailboxUiState.Ready ?: return
            mutableState.value = current.copy(
                selectedMessage = null,
                isMessageLoading = true,
                failure = null,
            )
            try {
                mutableState.value = (mutableState.value as? MailboxUiState.Ready)?.copy(
                    selectedMessage = remote.readMessage(message.id),
                    isMessageLoading = false,
                    failure = null,
                ) ?: return
            } catch (error: CancellationException) {
                throw error
            } catch (error: MailboxRemoteException) {
                mutableState.value = (mutableState.value as? MailboxUiState.Ready)?.copy(
                    isMessageLoading = false,
                    failure = error.reason.toUiFailure(),
                ) ?: return
            } catch (_: Exception) {
                mutableState.value = (mutableState.value as? MailboxUiState.Ready)?.copy(
                    isMessageLoading = false,
                    failure = MailboxFailure.NETWORK,
                ) ?: return
            }
        }
    }

    suspend fun startCompose(replyToMessageId: String? = null) {
        operationMutex.withLock {
            val current = mutableState.value as? MailboxUiState.Ready ?: return
            mutableState.value = current.copy(
                compose = MailboxComposeState(isLoading = true),
            )
            try {
                val draft = remote.beginCompose(replyToMessageId)
                mutableState.value = (mutableState.value as? MailboxUiState.Ready)?.copy(
                    compose = MailboxComposeState(draft = draft),
                ) ?: return
            } catch (error: CancellationException) {
                throw error
            } catch (error: MailboxRemoteException) {
                mutableState.value = (mutableState.value as? MailboxUiState.Ready)?.copy(
                    compose = MailboxComposeState(failure = error.reason.toUiFailure()),
                ) ?: return
            } catch (_: Exception) {
                mutableState.value = (mutableState.value as? MailboxUiState.Ready)?.copy(
                    compose = MailboxComposeState(failure = MailboxFailure.NETWORK),
                ) ?: return
            }
        }
    }

    fun updateCompose(draft: MailComposeDraft) {
        val current = mutableState.value as? MailboxUiState.Ready ?: return
        mutableState.value = current.copy(
            compose = current.compose.copy(draft = draft, failure = null),
        )
    }

    suspend fun sendCompose(): Boolean {
        operationMutex.withLock {
            val current = mutableState.value as? MailboxUiState.Ready ?: return false
            val draft = current.compose.draft ?: return false
            mutableState.value = current.copy(
                compose = current.compose.copy(isSending = true, failure = null),
            )
            return try {
                remote.sendMessage(draft)
                (mutableState.value as? MailboxUiState.Ready)?.let { latest ->
                    mutableState.value = latest.copy(compose = MailboxComposeState())
                }
                true
            } catch (error: CancellationException) {
                throw error
            } catch (error: MailboxRemoteException) {
                (mutableState.value as? MailboxUiState.Ready)?.let { latest ->
                    mutableState.value = latest.copy(
                        compose = MailboxComposeState(
                            draft = draft,
                            failure = error.reason.toUiFailure(),
                        ),
                    )
                }
                false
            } catch (_: Exception) {
                (mutableState.value as? MailboxUiState.Ready)?.let { latest ->
                    mutableState.value = latest.copy(
                        compose = MailboxComposeState(
                            draft = draft,
                            failure = MailboxFailure.NETWORK,
                        ),
                    )
                }
                false
            }
        }
    }

    suspend fun cancelCompose() {
        operationMutex.withLock {
            val current = mutableState.value as? MailboxUiState.Ready ?: return
            val composeId = current.compose.draft?.id
            // 先清掉本地编辑态，系统返回/Activity 销毁时不会短暂把编辑器留在根页；
            // 服务器临时草稿再尽力取消。
            mutableState.value = current.copy(compose = MailboxComposeState())
            if (!composeId.isNullOrBlank()) {
                runCatching { remote.cancelCompose(composeId) }
            }
        }
    }

    fun clearSelectedMessage() {
        val current = mutableState.value as? MailboxUiState.Ready ?: return
        mutableState.value = current.copy(selectedMessage = null, isMessageLoading = false)
    }

    fun dismissFailure() {
        val current = mutableState.value as? MailboxUiState.Ready ?: return
        mutableState.value = current.copy(failure = null)
    }

    private suspend fun loadMessages(folderId: Int) {
        try {
            val page = remote.listMessages(
                folderId = folderId,
                start = 0,
                limit = PAGE_SIZE,
                descending = true,
            )
            mutableState.value = (mutableState.value as? MailboxUiState.Ready)?.copy(
                messages = page.messages,
                totalCount = page.totalCount,
                isListLoading = false,
                isLoadingMore = false,
                hasMoreMessages = page.messages.size < page.totalCount && page.messages.isNotEmpty(),
                failure = null,
            ) ?: return
        } catch (error: CancellationException) {
            throw error
        } catch (error: MailboxRemoteException) {
            mutableState.value = (mutableState.value as? MailboxUiState.Ready)?.copy(
                isListLoading = false,
                failure = error.reason.toUiFailure(),
            ) ?: return
        } catch (_: Exception) {
            mutableState.value = (mutableState.value as? MailboxUiState.Ready)?.copy(
                isListLoading = false,
                failure = MailboxFailure.NETWORK,
            ) ?: return
        }
    }
}

private fun MailboxRemoteFailure.toUiFailure(): MailboxFailure = when (this) {
    MailboxRemoteFailure.NETWORK -> MailboxFailure.NETWORK
    MailboxRemoteFailure.PARSE -> MailboxFailure.PARSE
    MailboxRemoteFailure.SESSION_EXPIRED -> MailboxFailure.SESSION_EXPIRED
}
