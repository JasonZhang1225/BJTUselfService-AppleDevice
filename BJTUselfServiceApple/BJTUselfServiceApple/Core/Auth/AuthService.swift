//
//  AuthService.swift
//  BJTUselfServiceApple
//
//  Created on 2026/1/19.
//

import Foundation
import Combine

/// 验证码挑战结果
enum CaptchaChallengeResult {
    case success(CaptchaChallenge)
    case alreadyLoggedIn(LoginResult)
    case failure(LoginResult)
}

/// 认证服务
@MainActor
class AuthService: ObservableObject {
    static let shared = AuthService()
    
    @Published var isAuthenticated: Bool = false
    @Published var currentStudent: StudentInfo?
    private var cachedChallenge: CaptchaChallenge?
    
    private let networkService = NetworkService.shared
    private let misEntryURL = URL(string: "https://mis.bjtu.edu.cn/auth/sso/?next=/")!
    private let casHost = "cas.bjtu.edu.cn"
    
    private init() {}
    
    /// 拉取验证码挑战（仅返回信息，UI 可选择是否显示图片）
    func fetchCaptchaChallenge() async -> CaptchaChallengeResult {
        // 预设语言 Cookie 避免 CAS setlang 死循环
        if let cookie = CookieStore.createCookie(
            name: "django_language",
            value: "zh-cn",
            domain: "cas.bjtu.edu.cn"
        ) {
            CookieStore.shared.store(cookie: cookie)
        }
        
        do {
            let entryResponse = try await networkService.get(
                url: misEntryURL,
                headers: ["Host": "mis.bjtu.edu.cn"]
            )
            let entryHTML = String(data: entryResponse.data, encoding: .utf8)
            logAuthDebug(prefix: "entry", response: entryResponse, html: entryHTML)
            guard let html = entryHTML else {
                return .failure(LoginResult(success: false, message: "无法解析登录页"))
            }
            
            if isAuthenticatedResponse(entryResponse, html: html) {
                isAuthenticated = true
                currentStudent = currentStudent ?? StudentInfo(studentId: "")
                return .alreadyLoggedIn(LoginResult(success: true, message: "已登录"))
            }
            
            guard let captchaId = parseCaptchaId(html: html) else {
                return .failure(LoginResult(success: false, message: "未找到验证码标识"))
            }
            guard let csrfToken = extract(html: html, pattern: "name=['\"]csrfmiddlewaretoken['\"] value=['\"]([^'\"]+)['\"]") else {
                return .failure(LoginResult(success: false, message: "未找到 CSRF Token"))
            }
            let rawNext = extract(html: html, pattern: "name=['\"]next['\"] value=['\"]([^'\"]+)['\"]")
            let nextPath = rawNext.map(decodeHTMLEntities) ?? "/home/"
            // 优先使用实际发生跳转后的 finalURL，因为其中包含了必要的 query parameters (如 next=...)
            // Android 逻辑: response.request().url().toString()
            let casURL = entryResponse.finalURL ?? resolveCASLoginURL(from: html)
            
            let captchaImageURL = URL(string: "https://\(casHost)/image/\(captchaId)/")!
            let challenge = CaptchaChallenge(
                captchaId: captchaId,
                csrfToken: csrfToken,
                nextPath: nextPath,
                casLoginURL: casURL,
                captchaImageURL: captchaImageURL
            )
            logChallenge(challenge)
            return .success(challenge)
        } catch {
            return .failure(LoginResult(success: false, message: "网络错误：\(error.localizedDescription)"))
        }
    }

    /// 获取验证码图片用于展示，并缓存当前 challenge
    func fetchCaptchaForDisplay() async -> Result<(CaptchaChallenge, Data), LoginResult> {
        let challengeResult = await fetchCaptchaChallenge()
        switch challengeResult {
        case .alreadyLoggedIn(let result):
            return .failure(result)
        case .failure(let result):
            return .failure(result)
        case .success(let challenge):
            do {
                let response = try await networkService.get(
                    url: challenge.captchaImageURL,
                    headers: [
                        "Referer": challenge.casLoginURL.absoluteString
                    ]
                )
                logAuthDebug(prefix: "captcha", response: response, html: nil)
                cachedChallenge = challenge
                return .success((challenge, response.data))
            } catch {
                return .failure(LoginResult(success: false, message: "验证码获取失败：\(error.localizedDescription)"))
            }
        }
    }
    
    /// 登录MIS系统（captchaText 为空时尝试自动识别）
    func login(username: String, password: String, captchaText: String?) async -> LoginResult {
        var challenge: CaptchaChallenge?
        if let cached = cachedChallenge {
            challenge = cached
        } else {
            let challengeResult = await fetchCaptchaChallenge()
            switch challengeResult {
            case .alreadyLoggedIn(let result):
                return result
            case .failure(let result):
                return result
            case .success(let ch):
                challenge = ch
            }
        }
        guard let challenge else {
            return LoginResult(success: false, message: "无法获取验证码，请重试")
        }
        do {
            let captchaToUse: String
            if let manual = captchaText?.nonEmpty {
                captchaToUse = manual
            } else if let auto = await recognizeCaptcha(challenge: challenge) {
                captchaToUse = auto
            } else {
                return LoginResult(success: false, message: "验证码识别失败，请手动输入")
            }

            // Match Android: `next` 仅在 URL 查询中，不放在 form body。
            let params: [String: String] = [
                "csrfmiddlewaretoken": challenge.csrfToken,
                "captcha_0": challenge.captchaId,
                "captcha_1": captchaToUse,
                "loginname": username,
                "password": password
            ]
            logParams(params)

            // Form-url-encode 
            let formData = encodeFormData(params)

            // 构造登录 URL：确保包含 ?next= 参数（与安卓行为一致）
            let loginURL: URL
            if let query = challenge.casLoginURL.query, query.contains("next=") {
                loginURL = challenge.casLoginURL
            } else {
                // 使用 challenge.nextPath 作为 next 参数并进行 URL 编码
                var comps = URLComponents()
                comps.scheme = "https"
                comps.host = casHost
                comps.path = "/auth/login/"
                comps.queryItems = [URLQueryItem(name: "next", value: challenge.nextPath)]
                loginURL = comps.url ?? challenge.casLoginURL
            }

            print("[AuthDebug] Posting to loginURL: \(loginURL.absoluteString)")

            let response = try await networkService.post(
                url: loginURL,
                headers: [
                    "Content-Type": "application/x-www-form-urlencoded",
                    "Referer": challenge.casLoginURL.absoluteString,
                    "Origin": "https://\(casHost)"
                ],
                body: formData
            )

            // 打印最终响应的请求 URL（类似安卓的 response.request().url()）
            if let final = response.finalURL {
                print("[AuthDebug] login response finalURL: \(final.absoluteString)")
            } else {
                print("[AuthDebug] login response finalURL: nil")
            }

            logAuthDebug(
                prefix: "login",
                response: response,
                html: String(data: response.data, encoding: .utf8)
            )
            logCookies()

            // 1. 尝试直接判断响应是否已成功
            if isAuthenticatedResponse(response, html: String(data: response.data, encoding: .utf8)) {
                // 优先尝试直接从响应 HTML 解析学生信息
                let respHTML = String(data: response.data, encoding: .utf8)
                var parsedStudent: StudentInfo? = nil
                if let html = respHTML {
                    parsedStudent = parseStudentInfo(from: html)
                }

                // 若未直接解析到学生信息，且服务器已设置了 MIS 会话 Cookie，则再主动请求 /home/ 以解析
                if parsedStudent == nil && hasAuthCookie() {
                    if let homeURL = URL(string: "https://mis.bjtu.edu.cn/home/") {
                        let homeResponse = try await networkService.get(url: homeURL)
                        let homeHTML = String(data: homeResponse.data, encoding: .utf8)
                        logAuthDebug(prefix: "home_after_post", response: homeResponse, html: homeHTML)
                        if let html = homeHTML {
                            parsedStudent = parseStudentInfo(from: html)
                        }
                    }
                }

                // 只有在解析到学生信息或至少检测到 MIS session cookie 时才视作登录成功
                if parsedStudent != nil || hasAuthCookie() {
                    return finalizeAuthentication(username: username, student: parsedStudent)
                }

                return LoginResult(success: false, message: "登录未生效（未检测到 MIS 会话或用户信息解析失败）")
            }
            
            // 2. 双重检查 (Double Check)
            // 很多时候 CAS 登录虽然成功 (由于重定向链复杂截断、或返回 302 等原因)，
            // 导致直接 Response 判定失败。但此时 Cookie 可能已经种入。
            // 我们主动发起一次对 Home 的请求来确认是否真的登录成功。
            print("[AuthDebug] Primary check failed, attempting double check on Home URL...")
            if let homeURL = URL(string: "https://mis.bjtu.edu.cn/home/") {
                // 不带额外 Header，完全依赖 CookieStore 中的 Cookie
                let homeResponse = try await networkService.get(url: homeURL)
                let homeHTML = String(data: homeResponse.data, encoding: .utf8)
                logAuthDebug(prefix: "home_check", response: homeResponse, html: homeHTML)
                
                if isAuthenticatedResponse(homeResponse, html: homeHTML) {
                    // 从 /home/ 解析学生信息
                    if let html = homeHTML, let parsed = parseStudentInfo(from: html) {
                        return finalizeAuthentication(username: username, student: parsed)
                    }
                    // 如果无法解析到学生信息，但能检测到 MIS 域的会话 Cookie，可保守认为登录成功
                    if hasAuthCookie() {
                        return finalizeAuthentication(username: username, student: nil)
                    }
                }
            }
            
            return LoginResult(success: false, message: "登录失败，可能是验证码或密码错误")
        } catch {
            return LoginResult(success: false, message: "网络错误：\(error.localizedDescription)")
        }
    }
    
    private func finalizeAuthentication(username: String, student: StudentInfo?) -> LoginResult {
        // 要求存在会话相关的 Cookie（防止仅页面跳转但未实际登录的误判）
        if !hasAuthCookie() {
            print("[AuthDebug] finalizeAuthentication: no auth cookie present -> rejecting authentication")
            return LoginResult(success: false, message: "登录未生效（未检测到会话 Cookie）")
        }

        isAuthenticated = true
        if let s = student {
            currentStudent = s
        } else {
            currentStudent = StudentInfo(name: "", studentId: username)
        }
        cachedChallenge = nil
        print("[AuthDebug] finalizeAuthentication: authenticated as \(currentStudent?.name ?? currentStudent?.studentId ?? "<unknown>")")
        return LoginResult(success: true, message: "登录成功")
    }
    
    /// 登出
    func logout() {
        isAuthenticated = false
        currentStudent = nil
        networkService.clearCookies()
    }
    
    /// 检查登录状态（实际请求 Home 页面验证）
    func checkAuthStatus() async -> Bool {
        do {
            let homeURL = URL(string: "https://mis.bjtu.edu.cn/home/")!
            let response = try await networkService.get(url: homeURL)
            let html = String(data: response.data, encoding: .utf8)
            logAuthDebug(prefix: "auth_check", response: response, html: html)
            return isAuthenticatedResponse(response, html: html)
        } catch {
            print("[AuthDebug] checkAuthStatus network error: \(error)")
            return false
        }
    }
    
    private func isAuthenticatedResponse(_ response: NetworkResponse, html: String?) -> Bool {
        // 优先基于最终 URL 进行严格判断（必须是真正的 /home/ 页面）
        if let finalURL = response.finalURL, isHomeURL(finalURL) {
            print("[AuthDebug] isAuthenticatedResponse: matched finalURL -> \(finalURL.absoluteString)")
            return true
        }
        // 如果响应带有 Location 跳转到 /home/ 则也视为成功
        if let location = response.headers["Location"] ?? response.headers["location"],
           let url = URL(string: location, relativeTo: misEntryURL)?.absoluteURL,
           isHomeURL(url) {
            print("[AuthDebug] isAuthenticatedResponse: matched Location header -> \(url.absoluteString)")
            return true
        }

        // 否则分析 HTML：要同时满足“包含登录后特征”且“不包含登录表单”
        if let html {
            let lower = html.lowercased()
            // 简单判断页面是否仍包含登录表单（若包含则肯定未登录）
            let loginFormPresent = lower.contains("name=\'loginname\'") || lower.contains("name=\"loginname\"") || (lower.contains("password") && lower.contains("loginname"))
            if loginFormPresent {
                print("[AuthDebug] isAuthenticatedResponse: login form detected in HTML -> treating as not authenticated")
                return false
            }

            // 登录后典型的特征关键词（按需补充）："退出", "退出登录", "欢迎"
            let loggedInMarkers = ["退出", "退出登录", "欢迎", "我的课程", "校园信息中心"]
            for marker in loggedInMarkers {
                if html.contains(marker) {
                    print("[AuthDebug] isAuthenticatedResponse: matched HTML marker -> \(marker)")
                    return true
                }
            }
        }

        print("[AuthDebug] isAuthenticatedResponse: no positive evidence for authentication found")
        return false
    }
    
    private func recognizeCaptcha(challenge: CaptchaChallenge) async -> String? {
        // 下载验证码图片，使用 CoreML 模型尝试识别
        do {
            let response = try await networkService.get(url: challenge.captchaImageURL)
            let data = response.data
            return try await CaptchaRecognizer.shared.recognize(imageData: data)
        } catch {
            return nil
        }
    }

    private func isHomeURL(_ url: URL) -> Bool {
        guard url.host == "mis.bjtu.edu.cn" else { return false }
        // 接受精确的 /home/ 或以 /home 开头的路由，但避免包含 next= 参数的重定向地址
        if url.path == "/home/" || url.path.hasPrefix("/home") {
            if let q = url.query, q.contains("next=") { return false }
            return true
        }
        return false
    }

    private func hasAuthCookie() -> Bool {
        let cookies = networkService.getCookies()
        return cookies.contains { cookie in
            (cookie.domain.contains("mis.bjtu.edu.cn") || cookie.domain.contains("cas.bjtu.edu.cn")) &&
            (cookie.name.lowercased().contains("session") || cookie.name.lowercased().contains("ticket") || cookie.name.lowercased().contains("cas"))
        }
    }

    /// 从 MIS 首页 HTML 中解析学生信息（name / 学号 / 部门）
    private func parseStudentInfo(from html: String) -> StudentInfo? {
        // 优先匹配 `.name_right > h3 > a`（与 Android Jsoup 选择器一致）
        if let nameRaw = extract(html: html, pattern: "<div[^>]*class=[\"']name_right[\"'][^>]*>[\\s\\S]*?<h3[^>]*>\\s*<a[^>]*>([^<]+)</a>") {
            // 名称通常形如 "张三，..."，取逗号前部分
            let name = nameRaw.split(separator: "，").map(String.init).first ?? nameRaw
            let id = extract(html: html, pattern: "身份：\\s*([^<]+)")?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            let dept = extract(html: html, pattern: "部门：\\s*([^<]+)")?.trimmingCharacters(in: .whitespacesAndNewlines)
            return StudentInfo(name: name, studentId: id, major: nil, college: dept)
        }

        // 兜底：尝试更宽松的匹配（例如直接匹配 h3 > a）
        if let nameRaw = extract(html: html, pattern: "<h3[^>]*>\\s*<a[^>]*>([^<]+)</a>") {
            let name = nameRaw.split(separator: "，").map(String.init).first ?? nameRaw
            let id = extract(html: html, pattern: "身份：\\s*([^<]+)")?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            let dept = extract(html: html, pattern: "部门：\\s*([^<]+)")?.trimmingCharacters(in: .whitespacesAndNewlines)
            return StudentInfo(name: name, studentId: id, major: nil, college: dept)
        }

        return nil
    }

    private func resolveCASLoginURL(from html: String) -> URL {
        if let action = extract(html: html, pattern: "action=['\"]([^'\"]+)['\"]"),
           let baseURL = URL(string: "https://\(casHost)"),
           let url = URL(string: decodeHTMLEntities(action), relativeTo: baseURL)?.absoluteURL {
            return url
        }
        return misEntryURL
    }

    private func encodeFormData(_ params: [String: String]) -> Data? {
        var components = URLComponents()
        // 手动构造符合 application/x-www-form-urlencoded 的 query string
        // 为了确保与 Android (OkHttp) 行为一致（空格转加号等），我们使用自定义编码逻辑或 URLComponents
        // URLComponents 默认使用 percent encoded (space -> %20)
        // 但大部分服务器兼容 %20。若需严格一致，可手动处理。
        
        // 这里采用更严格的手动编码以匹配 standard form encoding
        let allowed = CharacterSet(charactersIn: "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789*-._")
        
        let pairs = params.map { key, value -> String in
            let escapedKey = key.addingPercentEncoding(withAllowedCharacters: allowed) ?? key
            // 手动处理空格为 +
            let escapedValue = value.addingPercentEncoding(withAllowedCharacters: allowed)?.replacingOccurrences(of: "%20", with: "+") ?? ""
            return "\(escapedKey)=\(escapedValue)"
        }
        return pairs.joined(separator: "&").data(using: .utf8)
    }
    
    private func extract(html: String, pattern: String) -> String? {
        guard let regex = try? NSRegularExpression(pattern: pattern, options: []) else { return nil }
        let range = NSRange(location: 0, length: html.utf16.count)
        guard let match = regex.firstMatch(in: html, range: range), match.numberOfRanges > 1 else { return nil }
        let valueRange = match.range(at: 1)
        guard let swiftRange = Range(valueRange, in: html) else { return nil }
        return String(html[swiftRange])
    }
    
    private func parseCaptchaId(html: String) -> String? {
        // 1) 优先匹配隐藏字段 id_captcha_0
        if let id = extract(html: html, pattern: "id=\\\"id_captcha_0\\\" value=\\\"([^\\\"]+)\\\"") {
            return id
        }
        // 2) 匹配图片 src 中的 /image/<id>/
        if let id = extract(html: html, pattern: "src=\\\"[^\\\"]*/image/([^/]+)/\\\"") {
            return id
        }
        // 3) 兜底匹配 `image/<id>/` 字样
        if let id = extract(html: html, pattern: "image/([^/]+)/") {
            return id
        }
        return nil
    }

    private func logChallenge(_ c: CaptchaChallenge) {
        print("[AuthDebug] challenge captchaId=\(c.captchaId) next=\(c.nextPath) casURL=\(c.casLoginURL.absoluteString) csrf.prefix=\(c.csrfToken.prefix(8))...")
    }

    private func logParams(_ params: [String: String]) {
        let joined = params.map { "\($0.key)=\($0.value)" }.joined(separator: " & ")
        print("[AuthDebug] submit params: \(joined)")
    }

    private func decodeHTMLEntities(_ text: String) -> String {
        text.replacingOccurrences(of: "&amp;", with: "&")
    }

    private func logCookies() {
        let cookies = networkService.getCookies().filter { $0.domain.contains("cas.bjtu.edu.cn") || $0.domain.contains("mis.bjtu.edu.cn") }
        let desc = cookies.map { "\($0.name)=\($0.value)@\($0.domain)" }.joined(separator: " | ")
        print("[AuthDebug] cookies: \(desc)")
    }

    /// 简单调试输出，便于排查登录链路问题
    private func logAuthDebug(prefix: String, response: NetworkResponse, html: String?) {
        let location = response.headers["Location"] ?? response.headers["location"] ?? "nil"
        let snippet = html.map { String($0.prefix(400)) } ?? "nil"
        print("[AuthDebug] \(prefix) status=\(response.statusCode) location=\(location) snippet=\(snippet)")
    }
}

private extension String {
    var nonEmpty: String? { isEmpty ? nil : self }
}
