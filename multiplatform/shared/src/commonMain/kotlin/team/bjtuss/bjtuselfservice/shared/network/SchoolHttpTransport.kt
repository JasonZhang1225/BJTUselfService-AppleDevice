package team.bjtuss.bjtuselfservice.shared.network

enum class SchoolHttpMethod {
    GET,
    POST,
}

data class SchoolHttpRequest(
    val method: SchoolHttpMethod,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val formFields: Map<String, String> = emptyMap(),
    val multipartFiles: List<SchoolMultipartFile> = emptyList(),
) {
    override fun toString(): String =
        "SchoolHttpRequest(method=$method, url=${url.redactedUrl()}, headers=${headers.redactedHeaders()}, " +
            "formFields=${formFields.redactedFormFields()}, multipartFiles=${multipartFiles.redactedMultipartFiles()})"
}

data class SchoolMultipartFile(
    val fieldName: String,
    val fileName: String,
    val contentType: String = "application/octet-stream",
    val bytes: ByteArray,
) {
    init {
        require(fieldName.isNotBlank() && '\r' !in fieldName && '\n' !in fieldName)
        require(fileName.isNotBlank() && '\r' !in fileName && '\n' !in fileName)
        require(contentType.isNotBlank() && '\r' !in contentType && '\n' !in contentType)
    }

    override fun equals(other: Any?): Boolean = other is SchoolMultipartFile &&
        fieldName == other.fieldName &&
        fileName == other.fileName &&
        contentType == other.contentType &&
        bytes.contentEquals(other.bytes)

    override fun hashCode(): Int {
        var result = fieldName.hashCode()
        result = 31 * result + fileName.hashCode()
        result = 31 * result + contentType.hashCode()
        result = 31 * result + bytes.contentHashCode()
        return result
    }

    override fun toString(): String =
        "SchoolMultipartFile(fieldName=$fieldName, fileName=<redacted>, contentType=$contentType, bytes=${bytes.size})"
}

data class SchoolHttpResponse(
    val statusCode: Int,
    val finalUrl: String,
    val headers: Map<String, List<String>> = emptyMap(),
    val body: ByteArray = byteArrayOf(),
) {
    fun bodyText(): String = body.decodeToString()

    /**
     * 智慧平台个别老接口（如已提交附件列表）以 GBK/GB18030 返回中文文件名，
     * 直接 UTF-8 解码会得到乱码（真实观察为西里尔/拉丁扩展字符错位）。
     * 此方法按 GB18030 解码，仅在已确认该接口编码的调用点使用。
     */
    fun bodyTextGbk(): String = decodeLegacyGb18030OrNull(body) ?: body.decodeToString()

    /** HTTP 头部大小写不敏感读取，取首个值。 */
    fun header(name: String): String? = headers.entries
        .firstOrNull { it.key.equals(name, ignoreCase = true) }
        ?.value
        ?.firstOrNull()

    override fun toString(): String =
        "SchoolHttpResponse(statusCode=$statusCode, finalUrl=${finalUrl.redactedUrl()}, headers=${headers.keys}, body=${body.size} bytes)"
}

interface SchoolHttpTransport {
    /**
     * 会话相关请求（CAS / aa / 智慧平台等）。实现应串行化，保护共享 Cookie jar。
     */
    suspend fun execute(request: SchoolHttpRequest): SchoolHttpResponse

    /**
     * 不自动跟随重定向的会话请求，供需要逐跳白名单验证的握手使用。
     * 默认实现回退到 [execute]；Ktor 实现使用共享 Cookie jar 的独立客户端。
     */
    suspend fun executeWithoutRedirects(request: SchoolHttpRequest): SchoolHttpResponse = execute(request)

    /**
     * 不依赖登录会话的公开页请求。
     *
     * 默认回退到 [execute]。[KtorSchoolHttpTransport] 用独立客户端、不占会话请求锁，
     * 避免 bksy 校历等跨域公开页在代理下挂起时把所有 aa 查询堵在队列里
     *（教室占用切周转圈就是这个症状）。
     */
    suspend fun executePublic(request: SchoolHttpRequest): SchoolHttpResponse = execute(request)

    suspend fun sessionCookiesFor(url: String): List<SchoolSessionCookie> = emptyList()
    fun clearSession()
}

/** 只用于把当前内存会话桥接给受信任网页容器；字符串化永不包含值。 */
data class SchoolSessionCookie(
    val name: String,
    val value: String,
    val path: String = "/",
    val secure: Boolean = true,
) {
    override fun toString(): String =
        "SchoolSessionCookie(name=$name, value=<redacted>, path=$path, secure=$secure)"
}

class SchoolNetworkException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * 平台 GB18030 解码入口。Ktor 的通用 charset 注册表在 Kotlin/Native iOS 上没有
 * GB18030，必须由 iOS Foundation/CoreFoundation 实现；JVM 平台使用 JDK Charset。
 */
internal expect fun decodeLegacyGb18030OrNull(bytes: ByteArray): String?

private val sensitiveFormFieldNames = setOf(
    "password",
    "captcha_0",
    "captcha_1",
    "csrfmiddlewaretoken",
    "loginname",
    "content",
    "filelist",
    "sesskey",
    "itemid",
    "ctx_id",
    "client_id",
    "repo_id",
)

private fun Map<String, String>.redactedFormFields(): Map<String, String> = mapValues { (name, value) ->
    if (name.lowercase() in sensitiveFormFieldNames) "<redacted>" else value
}

private fun Map<String, String>.redactedHeaders(): Map<String, String> = mapValues { (name, value) ->
    if (
        name.equals("Cookie", ignoreCase = true) ||
        name.equals("Authorization", ignoreCase = true) ||
        name.equals("sessionid", ignoreCase = true)
    ) {
        "<redacted>"
    } else if (name.equals("Referer", ignoreCase = true)) {
        value.redactedUrl()
    } else {
        value
    }
}

private fun List<SchoolMultipartFile>.redactedMultipartFiles(): List<String> = map { file ->
    "${file.fieldName}:<redacted>(${file.bytes.size} bytes)"
}

private fun String.redactedUrl(): String = if ('?' in this) {
    substringBefore('?') + "?<redacted>"
} else {
    this
}
