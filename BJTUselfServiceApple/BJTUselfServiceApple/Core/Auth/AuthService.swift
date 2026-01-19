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

            let response = try await networkService.post(
                url: challenge.casLoginURL,
                headers: [
                    "Content-Type": "application/x-www-form-urlencoded",
                    "Referer": challenge.casLoginURL.absoluteString,
                    "Origin": "https://\(casHost)"
                ],
                body: formData
            )
            logAuthDebug(
                prefix: "login",
                response: response,
                html: String(data: response.data, encoding: .utf8)
            )
            logCookies()
            
            // 1. 尝试直接判断响应是否已成功
            if isAuthenticatedResponse(response, html: String(data: response.data, encoding: .utf8)) {
                return markAsAuthenticated(username: username)
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
                     return markAsAuthenticated(username: username)
                }
            }
            
            return LoginResult(success: false, message: "登录失败，可能是验证码或密码错误")
        } catch {
            return LoginResult(success: false, message: "网络错误：\(error.localizedDescription)")
        }
    }
    
    private func markAsAuthenticated(username: String) -> LoginResult {
        isAuthenticated = true
        currentStudent = StudentInfo(
            name: "", // 后续可以从 home 页面解析名字
            studentId: username
        )
        cachedChallenge = nil
        return LoginResult(success: true, message: "登录成功")
    }
    
    /// 登出
    func logout() {
        isAuthenticated = false
        currentStudent = nil
        networkService.clearCookies()
    }
    
    /// 检查登录状态
    func checkAuthStatus() async -> Bool {
        // TODO: 实现检查登录状态的逻辑
        return isAuthenticated
    }
    
    private func isAuthenticatedResponse(_ response: NetworkResponse, html: String?) -> Bool {
        if let finalURL = response.finalURL, isHomeURL(finalURL) {
            return true
        }
        if let location = response.headers["Location"] ?? response.headers["location"],
           let url = URL(string: location, relativeTo: misEntryURL)?.absoluteURL,
           isHomeURL(url) {
            return true
        }
        if let html, html.contains("/home/") {
            return true
        }
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
        url.host == "mis.bjtu.edu.cn" && url.path.contains("/home/")
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
