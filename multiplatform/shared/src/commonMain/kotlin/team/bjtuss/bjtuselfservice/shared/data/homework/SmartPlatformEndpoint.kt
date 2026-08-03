package team.bjtuss.bjtuselfservice.shared.data.homework

import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.takeFrom
import team.bjtuss.bjtuselfservice.shared.network.SchoolHttpMethod
import team.bjtuss.bjtuselfservice.shared.network.SchoolHttpRequest
import team.bjtuss.bjtuselfservice.shared.network.SchoolHttpResponse

enum class SmartPlatformTransport {
    VERIFIED_HTTPS,
    LEGACY_APPLE_HTTP,
}

/**
 * 智慧教学平台的封闭端点策略。
 *
 * 默认策略只允许已验证的 HTTPS 域名。旧 HTTP 策略仅供 Apple 平台在用户明确授权后注入，
 * 且只能访问旧 Android 1.7.0 已使用的固定 IP、端口和路径前缀；它不会改变应用其余
 * 网络请求的安全策略，也不会把 sessionid 持久化。
 */
class SmartPlatformEndpoint private constructor(
    val transport: SmartPlatformTransport,
    val apiOrigin: String,
    private val calendarOrigin: String,
) {
    val isLegacyInsecure: Boolean
        get() = transport == SmartPlatformTransport.LEGACY_APPLE_HTTP

    fun apiUrl(path: String, query: LinkedHashMap<String, String>): String {
        require(path.startsWith("/ve/")) { "Smart-platform API path must stay under /ve/" }
        require(!path.hasUnsafePathMaterial()) { "Unsafe smart-platform API path" }
        if (query.isEmpty()) return "$apiOrigin$path"
        val suffix = query.entries.joinToString("&") { (name, value) ->
            "${name.smartUrlEncode()}=${value.smartUrlEncode()}"
        }
        return "$apiOrigin$path?$suffix"
    }

    /** 只接受当前策略的精确 origin 和 /ve/ 路径。 */
    fun acceptsApiUrl(url: String): Boolean = acceptsOriginPath(
        url = url,
        origin = apiOrigin,
        requiredPathPrefix = "/ve/",
    )

    /**
     * HTTPS 基线允许精确同源的下载路径；旧明文链路继续限定在原应用使用的 /ve/ 下，
     * 两者都不能换主机、端口或协议。
     */
    fun acceptsResourceUrl(url: String): Boolean = if (isLegacyInsecure) {
        acceptsApiUrl(url)
    } else {
        acceptsOriginPath(url = url, origin = apiOrigin, requiredPathPrefix = "/")
    }

    /**
     * HTTPS 策略只接受同源地址。旧 Apple 策略从 iframe 路径提取旧版所需的末 5 段，
     * 再重建为固定的 1936/kk/rp 地址，绝不沿用 iframe 提供的主机或端口。
     */
    fun resolveTeachingCalendarUrl(rawUrl: String): String? {
        if (!isLegacyInsecure) {
            val resolved = when {
                rawUrl.startsWith('/') -> "$apiOrigin$rawUrl"
                else -> rawUrl
            }
            return resolved.takeIf(::acceptsResourceUrl)
        }

        if (rawUrl.any(Char::isISOControl) || '#' in rawUrl) return null
        val parseTarget = if (rawUrl.startsWith('/')) "https://calendar-path.invalid$rawUrl" else rawUrl
        val parsed = runCatching { Url(parseTarget) }.getOrNull() ?: return null
        if (parsed.protocol.name !in setOf("http", "https")) return null
        val encodedPath = parsed.encodedPath
        if (encodedPath.hasUnsafePathMaterial()) return null
        val segments = encodedPath.split('/').filter(String::isNotEmpty).takeLast(5)
        if (segments.size != 5 || segments.any { it == "." || it == ".." }) return null
        val resolved = "$calendarOrigin/kk/rp/${segments.joinToString("/")}"
        return resolved.takeIf(::acceptsCalendarUrl)
    }

    fun acceptsCalendarUrl(url: String): Boolean = if (isLegacyInsecure) {
        acceptsOriginPath(
            url = url,
            origin = calendarOrigin,
            requiredPathPrefix = "/kk/rp/",
        )
    } else {
        acceptsResourceUrl(url)
    }

    /**
     * 握手跳转（MIS module 28 → 智慧平台第三方登录入口）的封闭校验。
     * 与业务 API 不同，握手允许精确 origin（协议+主机+端口）内的任意路径——
     * 第三方登录入口位于 /oauth/ 而非 /ve/ 下；教学日历 origin（:1936）不在此放行。
     */
    fun acceptsHandshakeUrl(url: String): Boolean = acceptsOriginPath(
        url = url,
        origin = apiOrigin,
        requiredPathPrefix = "/",
    )

    /**
     * 登录态下 MIS module 28 以裸 HTTP 3xx 指向智慧平台入口。Ktor 的 HttpRedirect
     * 出于安全默认拒绝跟随 HTTPS→HTTP 降级，因此 transport 返回未跟随的 3xx。
     * 这里从 Location 头提取跳转目标，并只在目标命中本策略白名单时放行——
     * 降级绝不扩散到白名单之外的任何主机。
     *
     * 返回 null 表示不是可放行的白名单跳转（调用方随后回退到 HTML 表单解析或报错）。
     */
    fun allowedRedirectTarget(statusCode: Int, locationHeader: String?): String? {
        if (statusCode !in 300..399) return null
        val location = locationHeader?.trim().orEmpty()
        if (location.isEmpty()) return null
        return location.takeIf(::acceptsHandshakeUrl)
    }

    companion object {
        val VerifiedHttps = SmartPlatformEndpoint(
            transport = SmartPlatformTransport.VERIFIED_HTTPS,
            apiOrigin = "https://bksycenter.bjtu.edu.cn",
            calendarOrigin = "https://bksycenter.bjtu.edu.cn",
        )

        val AppleLegacyHttp = SmartPlatformEndpoint(
            transport = SmartPlatformTransport.LEGACY_APPLE_HTTP,
            apiOrigin = "http://123.121.147.7:88",
            calendarOrigin = "http://123.121.147.7:1936",
        )
    }
}

private fun acceptsOriginPath(url: String, origin: String, requiredPathPrefix: String): Boolean {
    if (!url.startsWith("$origin$requiredPathPrefix")) return false
    if (url.any(Char::isISOControl) || '#' in url || url.hasUnsafePathMaterial()) return false
    val parsed = runCatching { Url(url) }.getOrNull() ?: return false
    val expected = runCatching { Url(origin) }.getOrNull() ?: return false
    return parsed.protocol == expected.protocol &&
        parsed.host == expected.host &&
        parsed.port == expected.port &&
        parsed.encodedPath.startsWith(requiredPathPrefix)
}

private fun String.hasUnsafePathMaterial(): Boolean {
    val path = substringBefore('?').lowercase()
    if ('\\' in path || "%2f" in path || "%5c" in path || "%00" in path) return true
    return path.split('/').any { segment ->
        val decodedDots = segment.replace("%2e", ".")
        decodedDots == "." || decodedDots == ".." || "%25" in segment
    }
}

internal fun String.smartUrlEncode(): String = buildString {
    encodeToByteArray().forEach { byte ->
        val value = byte.toInt() and 0xff
        val unreserved = value in 'A'.code..'Z'.code ||
            value in 'a'.code..'z'.code ||
            value in '0'.code..'9'.code ||
            value == '-'.code || value == '.'.code || value == '_'.code || value == '~'.code
        if (unreserved) {
            append(value.toChar())
        } else {
            append('%')
            append(SMART_HEX[value ushr 4])
            append(SMART_HEX[value and 0x0f])
        }
    }
}

private const val SMART_HEX = "0123456789ABCDEF"

/** OAuth 握手链允许经过的学校 HTTPS 主机（CAS 单点登录与 MIS）。 */
private val SMART_HTTPS_HANDSHAKE_HOSTS = setOf("cas.bjtu.edu.cn", "mis.bjtu.edu.cn")

/**
 * 登录态下 module 28 的握手是多跳 OAuth 链：
 * module 28 →(302, HTTPS→HTTP 降级) 明文 thirdLogin →(302) CAS authorize
 * →(302, HTTPS→HTTP 降级) 明文 oauth/token/callBack → 建立会话。
 * Ktor 会在每一跳降级处停住并返回未跟随的 3xx，因此必须逐跳手动跟随。
 * 明文跳只允许精确 apiOrigin（见 acceptsHandshakeUrl），HTTPS 跳只允许
 * cas/mis 学校主机；任何其他目标立即停住，降级绝不扩散到白名单之外。
 * Location 中的 ticket 等查询参数只用于真实请求，从不写入日志。
 */
internal suspend fun SmartPlatformEndpoint.followSmartHandshakeRedirects(
    first: SchoolHttpResponse,
    referer: String,
    maxHops: Int = 10,
    execute: suspend (SchoolHttpRequest) -> SchoolHttpResponse,
): SchoolHttpResponse {
    var current = first
    var hops = 0
    while (current.statusCode in 300..399) {
        val location = current.header("Location")?.trim().orEmpty()
        if (location.isEmpty()) break
        val target = resolveSmartRedirectTarget(current.finalUrl, location) ?: break
        val allowed = acceptsHandshakeUrl(target) || isSmartHttpsHandshakeHost(target)
        if (!allowed || ++hops > maxHops) break
        current = execute(
            SchoolHttpRequest(
                method = SchoolHttpMethod.GET,
                url = target,
                headers = mapOf("Referer" to referer),
            ),
        )
    }
    return current
}

private fun isSmartHttpsHandshakeHost(url: String): Boolean {
    if (url.any(Char::isISOControl) || '#' in url) return false
    val parsed = runCatching { Url(url) }.getOrNull() ?: return false
    if (parsed.protocol.name != "https") return false
    if (parsed.port != 443 && parsed.port != 0) return false
    return parsed.host in SMART_HTTPS_HANDSHAKE_HOSTS
}

private fun resolveSmartRedirectTarget(base: String, location: String): String? {
    if (location.any(Char::isISOControl)) return null
    return runCatching {
        URLBuilder(Url(base)).takeFrom(location).buildString()
    }.getOrNull()
}
