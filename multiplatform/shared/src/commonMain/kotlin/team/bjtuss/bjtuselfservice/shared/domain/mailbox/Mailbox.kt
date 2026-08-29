package team.bjtuss.bjtuselfservice.shared.domain.mailbox

/** 邮箱文件夹的只读摘要。 */
data class MailboxFolder(
    val id: Int,
    val name: String,
    val unreadCount: Int? = null,
)

/** 收件箱列表中的一封邮件，不包含正文。 */
data class MailSummary(
    val id: String,
    val folderId: Int,
    val sender: String,
    val subject: String,
    val preview: String,
    val sentAt: String,
    val receivedAt: String,
    val sizeBytes: Int,
    val isRead: Boolean,
    val hasAttachments: Boolean,
    val recipients: List<String> = emptyList(),
)

data class MailboxPage(
    val totalCount: Int,
    val messages: List<MailSummary>,
)

/** 详情中的附件元数据；首个只读切片不下载附件本体。 */
data class MailAttachment(
    val id: String?,
    val name: String,
    val sizeBytes: Int?,
    val contentType: String?,
)

/** 邮件详情。正文保留为受控 HTML 字符串，UI 首版只转为纯文本显示。 */
data class MailMessage(
    val id: String,
    val folderId: Int,
    val from: List<String>,
    val to: List<String>,
    val cc: List<String>,
    val bcc: List<String>,
    val subject: String,
    val bodyHtml: String,
    val sentAt: String,
    val attachments: List<MailAttachment>,
)
