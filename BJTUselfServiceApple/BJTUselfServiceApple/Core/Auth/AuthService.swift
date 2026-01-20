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
            // 打印完整响应头以便诊断
            print("[AuthDebug] login response headers: \(response.headers)")
            logCookies()

            // 保存本次登录响应 HTML 以便在失败时分析错误类型（验证码 vs 密码）
            let loginResponseHTML = String(data: response.data, encoding: .utf8)
            // 准备一个函数级变量以保存后续 Home 页面检查的 HTML（避免作用域问题）
            var lastCheckedHomeHTML: String? = nil

            // 某些情况下服务器会有短暂延迟才写入 MIS session cookie（导致我们立刻检查时未检到），
            // 在判断失败之前做一次短轮询：最多重试 3 次，每次等待 500ms
            var cookieAppearedAfterRetry = false
            if !hasAuthCookie() {
                for attempt in 1...3 {
                    try await Task.sleep(nanoseconds: 500_000_000) // 500ms
                    logCookies()
                    if hasAuthCookie() {
                        cookieAppearedAfterRetry = true
                        print("[AuthDebug] Cookie detected after \(attempt) retry")
                        break
                    }
                }
            }

            // 1. 尝试直接判断响应是否已成功
            if isAuthenticatedResponse(response, html: String(data: response.data, encoding: .utf8)) {
                // 优先尝试直接从响应 HTML 解析学生信息
                let respHTML = String(data: response.data, encoding: .utf8)
                var parsedStudent: StudentInfo? = nil
                if let html = respHTML {
                    parsedStudent = parseStudentInfo(from: html)
                }

                // 若未直接解析到学生信息，且服务器已设置了 CAS 会话 Cookie，则尝试触发 authorize -> callback 流程以促成 MIS 会话
                if parsedStudent == nil && hasAuthCookie() {
                    // 1) 尝试访问 next (authorize) 链接以触发回调
                    if let authorizeURL = URL(string: challenge.nextPath, relativeTo: URL(string: "https://\(casHost)")) {
                        print("[AuthDebug] Attempting authorize GET to: \(authorizeURL.absoluteString)")
                        let authResp = try await networkService.get(url: authorizeURL)
                        let authHTML = String(data: authResp.data, encoding: .utf8)
                        logAuthDebug(prefix: "authorize_call", response: authResp, html: authHTML)

                        // 如果 authorize 请求最终落在 mis 回调或首页，尝试解析用户信息
                        if isAuthenticatedResponse(authResp, html: authHTML) {
                            if let html = authHTML {
                                parsedStudent = parseStudentInfo(from: html)
                            }
                        }
                    }

                    // 2) 若仍未解析到用户信息，则最后退回到直接请求 /home/ 作为兜底
                    if parsedStudent == nil {
                        if let homeURL = URL(string: "https://mis.bjtu.edu.cn/home/") {
                            let homeResponse = try await networkService.get(url: homeURL)
                            let homeHTML = String(data: homeResponse.data, encoding: .utf8)
                            logAuthDebug(prefix: "home_after_post", response: homeResponse, html: homeHTML)
                            if let html = homeHTML {
                                parsedStudent = parseStudentInfo(from: html)
                            }
                        }

                        // 回退：若 /home/ 无法解析出有效姓名，尝试拉取 module/10 页面作为备用来源（与 Android 的流程一致）
                        if parsedStudent == nil {
                            if let moduleURL = URL(string: "https://mis.bjtu.edu.cn/module/module/10/") {
                                print("[AuthDebug] Attempting to fetch module/10 as fallback -> \(moduleURL.absoluteString)")
                                let moduleResp = try await networkService.get(url: moduleURL)
                                let moduleHTML = String(data: moduleResp.data, encoding: .utf8)
                                logAuthDebug(prefix: "module10", response: moduleResp, html: moduleHTML)
                                if let html = moduleHTML {
                                    parsedStudent = parseStudentInfo(from: html)
                                }
                            }
                        }
                    }
                }

                // 只有在解析到学生信息或至少检测到 MIS session cookie 时才视作登录成功
                if parsedStudent != nil || hasAuthCookie() {
                    return finalizeAuthentication(username: username, student: parsedStudent)
                }

                return LoginResult(success: false, message: "登录未生效（未检测到 MIS 会话或用户信息解析失败）")
            } else {
                // 如果响应仍为 CAS 登录页，但已经拿到 CAS session cookie（或短轮询后出现），
                // 也要主动触发 authorize/home 流程以尽量完成回调并建立 MIS 会话。
                if hasAuthCookie() || cookieAppearedAfterRetry {
                    print("[AuthDebug] Detected CAS session cookie despite login page; attempting multi-step authorize/home retries to complete flow")
                    var parsedStudent: StudentInfo? = nil

                    var authorizeURL: URL? = nil
                    if let aURL = URL(string: challenge.nextPath, relativeTo: URL(string: "https://\(casHost)")) {
                        authorizeURL = aURL
                    }

                    // 借助重试 helper 完成回调并解析学生信息
                    parsedStudent = await completeCallbackAndParseStudent(authorizeURL: authorizeURL)

                    if parsedStudent != nil || hasAuthCookie() {
                        return finalizeAuthentication(username: username, student: parsedStudent)
                    }
                }

                // 根据登录页/回调页面 HTML 尝试识别失败原因并返回更精确的提示
                if let msg = detectLoginFailureMessage(from: [loginResponseHTML]) {
                    return LoginResult(success: false, message: msg)
                }
                return LoginResult(success: false, message: "登录失败，可能是验证码或密码错误")
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
                    // 记录 homeHTML 以便后续错误原因分析使用（避免作用域错误）
                    lastCheckedHomeHTML = homeHTML
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
            
            // 在双重检查失败后，也尝试从 home 页面或之前的登录响应中识别失败原因
            let finalErrMsg = detectLoginFailureMessage(from: [loginResponseHTML, lastCheckedHomeHTML]) ?? "登录失败，可能是验证码或密码错误"
            return LoginResult(success: false, message: finalErrMsg)
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

        // 合并解析结果并确保 studentId 是账号或一个数字ID（优先选择登录名为准，当登录名为数字学号时覆盖页面学号）
        var finalStudent: StudentInfo
        // 登录名是否看起来像学号（数字串）
        let usernameIsNumeric = username.range(of: "[0-9]{5,12}", options: .regularExpression) != nil
        if let s = student {
            // 优先策略：如果登录名本身就是一个数字学号，则以登录名为权威学号（覆盖页面解析的学号），以避免页面解析到其他账号的学号导致混淆
            if usernameIsNumeric {
                // 若登录名为数字学号，则使用登录名作为权威学号；但不要用登录名填充“姓名”字段，保持 name 为空以免与真实姓名混淆
                if !s.studentId.isEmpty && s.studentId != username {
                    print("[AuthDebug] finalizeAuthentication: overriding parsed studentId '\(s.studentId)' with username '\(username)'")
                }
                if s.name.isEmpty {
                    print("[AuthDebug] finalizeAuthentication: name not found on page; leaving name empty and using studentId for display")
                }
                finalStudent = StudentInfo(name: s.name, studentId: username, major: s.major, college: s.college)
            } else {
                // 旧逻辑：若页面解析出的 studentId 不是数字（例如“本科生”标签），则回退使用用户名作为学号并把原始身份放到 major
                let numericMatch = s.studentId.range(of: "[0-9]{5,12}", options: .regularExpression) != nil
                if !numericMatch {
                    let computedMajor: String?
                    if !s.studentId.isEmpty && (s.major == nil || s.major?.isEmpty == true) {
                        computedMajor = s.studentId
                    } else {
                        computedMajor = s.major
                    }
                    // 保持 name 不被登录名覆盖
                    if s.name.isEmpty {
                        print("[AuthDebug] finalizeAuthentication: name not found on page; leaving name empty and using studentId for display")
                    }
                    finalStudent = StudentInfo(name: s.name, studentId: username, major: computedMajor, college: s.college)
                } else {
                    // 保持 name 不被登录名覆盖
                    finalStudent = StudentInfo(name: s.name, studentId: s.studentId, major: s.major, college: s.college)
                }
            }
        } else {
            // 无解析结果时，仅使用用户名作为学号，姓名保持为空
            print("[AuthDebug] finalizeAuthentication: no student info parsed; using username as studentId and leaving name empty")
            finalStudent = StudentInfo(name: "", studentId: username)
        }

        isAuthenticated = true
        currentStudent = finalStudent
        cachedChallenge = nil
        print("[AuthDebug] finalizeAuthentication: authenticated as \(currentStudent?.name ?? currentStudent?.studentId ?? "<unknown>") (studentId=\(currentStudent?.studentId ?? "<none>"), major=\(currentStudent?.major ?? "<none>"))")
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

    // 判断姓名是否看起来是站点标签或导航（黑名单），类级私有函数
    private func isBlacklistedName(_ name: String) -> Bool {
        let blacklist = ["主页", "首页", "校园", "信息", "交大", "北京交通大学", "登录", "校园信息", "交大主页"]
        return blacklist.contains { token in name.contains(token) }
    }

    /// 从 MIS 首页 HTML 中解析学生信息（name / 学号 / 部门）
    /// 提取页面中可能的学号：优先匹配“学号: xxxx”，否则查找 6-12 位数字
    private func extractStudentId(from html: String) -> String? {
        if let id = extract(html: html, pattern: "学号[:：]\\s*([0-9]{5,12})") {
            return id.trimmingCharacters(in: .whitespacesAndNewlines)
        }
        // 全文搜索 6-12 位数字，优先返回第一个匹配项
        if let regex = try? NSRegularExpression(pattern: "([0-9]{6,12})", options: []) {
            let range = NSRange(location: 0, length: html.utf16.count)
            if let match = regex.firstMatch(in: html, range: range), match.numberOfRanges > 1 {
                let mr = match.range(at: 1)
                if let r = Range(mr, in: html) {
                    return String(html[r])
                }
            }
        }
        return nil
    }

    private func parseStudentInfo(from html: String) -> StudentInfo? {
        // 优先匹配 `.name_right > h3 > a`（与 Android Jsoup 选择器一致）
        if let nameRaw = extract(html: html, pattern: "<div[^>]*class=[\"']name_right[\"'][^>]*>[\\s\\S]*?<h3[^>]*>\\s*<a[^>]*>([^<]+)</a>") {
            // 名称通常形如 "张三，..."，取逗号前部分
            let name = nameRaw.split(separator: "，").map(String.init).first ?? nameRaw
            let identity = extract(html: html, pattern: "身份：\\s*([^<]+)")?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            let dept = extract(html: html, pattern: "部门：\\s*([^<]+)")?.trimmingCharacters(in: .whitespacesAndNewlines)

            // 尝试提取学号（优先）
            let sid = extractStudentId(from: html)
            if let sid = sid {
                print("[AuthDebug] parseStudentInfo: got studentId from page -> \(sid)")
                return StudentInfo(name: name, studentId: sid, major: dept, college: nil)
            }

            // 若没有学号，则把 identity（如 本科生）放到 major 字段，并保留空 studentId
            return StudentInfo(name: name, studentId: "", major: identity, college: dept)
        }

        // 兜底：尝试更宽松的匹配（例如直接匹配 h3 > a）
        if let nameRaw = extract(html: html, pattern: "<h3[^>]*>\\s*<a[^>]*>([^<]+)</a>") {
            let name = nameRaw.split(separator: "，").map(String.init).first ?? nameRaw
            let identity = extract(html: html, pattern: "身份：\\s*([^<]+)")?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            let dept = extract(html: html, pattern: "部门：\\s*([^<]+)")?.trimmingCharacters(in: .whitespacesAndNewlines)
            
            if let sid = extractStudentId(from: html) {
                print("[AuthDebug] parseStudentInfo: got studentId from page -> \(sid)")
                return StudentInfo(name: name, studentId: sid, major: dept, college: nil)
            }
            if isBlacklistedName(name) {
                print("[AuthDebug] parseStudentInfo: matched h3 a name '\(name)' rejected by blacklist")
                // fallthrough to other matchers
            } else {
                return StudentInfo(name: name, studentId: "", major: identity, college: dept)
            }
        }

        // 再次尝试：匹配欢迎关键字，例如 "欢迎 张三"
        if let welcome = extract(html: html, pattern: #"欢迎[，,：:\s]*([^<，,]{2,10})"#) {
            let name = welcome.trimmingCharacters(in: .whitespacesAndNewlines)
            let identity = extract(html: html, pattern: "身份：\\s*([^<]+)")?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            if let sid = extractStudentId(from: html) {
                print("[AuthDebug] parseStudentInfo: got studentId from page -> \(sid)")
                return StudentInfo(name: name, studentId: sid, major: nil, college: nil)
            }
            if isBlacklistedName(name) {
                print("[AuthDebug] parseStudentInfo: matched welcome name '\(name)' rejected by blacklist")
            } else {
                return StudentInfo(name: name, studentId: "", major: identity, college: nil)
            }
        }

        // 额外：优先匹配显式标签或嵌入的用户信息（避免被站点导航文本误捕获）
        // 1) 匹配 '姓名：张三' 或 '学生姓名' 这种显式标签
        if let labeled = extract(html: html, pattern: #"姓名[:：]\s*([^<，,\s]{2,10})"#) {
            let name = labeled.trimmingCharacters(in: .whitespacesAndNewlines)
            print("[AuthDebug] parseStudentInfo: matched explicit '姓名' label -> \(name)")
            if isBlacklistedName(name) {
                print("[AuthDebug] parseStudentInfo: explicit name '\(name)' rejected by blacklist")
            } else {
                if let sid = extractStudentId(from: html) {
                    print("[AuthDebug] parseStudentInfo: got studentId from page -> \(sid)")
                    return StudentInfo(name: name, studentId: sid, major: nil, college: nil)
                }
                return StudentInfo(name: name, studentId: "", major: nil, college: nil)
            }
        }

        // 2) 匹配表格形式：<th>姓名</th><td>张三</td>
        if let tableName = extract(html: html, pattern: #"(?:<th[^>]*>\s*姓名\s*</th>|<td[^>]*>\s*姓名\s*</td>)[\s\S]*?<td[^>]*>\s*([^<]+)\s*</td>"#) {
            let name = tableName.trimmingCharacters(in: .whitespacesAndNewlines)
            print("[AuthDebug] parseStudentInfo: matched table name -> \(name)")
            if !isBlacklistedName(name) {
                if let sid = extractStudentId(from: html) {
                    print("[AuthDebug] parseStudentInfo: got studentId from page -> \(sid)")
                    return StudentInfo(name: name, studentId: sid, major: nil, college: nil)
                }
                return StudentInfo(name: name, studentId: "", major: nil, college: nil)
            } else {
                print("[AuthDebug] parseStudentInfo: table name '\(name)' rejected by blacklist")
            }
        }

        // 3) 匹配 meta/author
        if let metaAuthor = extract(html: html, pattern: #"<meta[^>]*name=['"]author['"][^>]*content=['"]([^'"]+)['"]"#) {
            let name = metaAuthor.trimmingCharacters(in: .whitespacesAndNewlines)
            print("[AuthDebug] parseStudentInfo: matched meta author -> \(name)")
            if !isBlacklistedName(name) {
                if let sid = extractStudentId(from: html) {
                    print("[AuthDebug] parseStudentInfo: got studentId from page -> \(sid)")
                    return StudentInfo(name: name, studentId: sid, major: nil, college: nil)
                }
                return StudentInfo(name: name, studentId: "", major: nil, college: nil)
            }
        }

        // 4) 匹配脚本内的 JSON 字段（常见键名 name / realName / realname / xm）
        if let jsName = extract(html: html, pattern: #"(?:['"]realname['"]|['"]realName['"]|['"]name['"]|['"]xm['"])\s*:\s*['"]([^'"]{2,20})['"]"#) {
            let name = jsName.trimmingCharacters(in: .whitespacesAndNewlines)
            print("[AuthDebug] parseStudentInfo: matched script JSON name -> \(name)")
            if !isBlacklistedName(name) {
                if let sid = extractStudentId(from: html) {
                    print("[AuthDebug] parseStudentInfo: got studentId from page -> \(sid)")
                    return StudentInfo(name: name, studentId: sid, major: nil, college: nil)
                }
                return StudentInfo(name: name, studentId: "", major: nil, college: nil)
            }
        }

        // 5) header 区域优先（避免匹配页脚等导航）
        if let headerName = extract(html: html, pattern: #"<header[\s\S]*?<a[^>]*>([\p{Han}]{2,10})</a>"#) {
            let name = headerName.trimmingCharacters(in: .whitespacesAndNewlines)
            print("[AuthDebug] parseStudentInfo: matched header anchor -> \(name)")
            if !isBlacklistedName(name) {
                if let sid = extractStudentId(from: html) {
                    print("[AuthDebug] parseStudentInfo: got studentId from page -> \(sid)")
                    return StudentInfo(name: name, studentId: sid, major: nil, college: nil)
                }
                return StudentInfo(name: name, studentId: "", major: nil, college: nil)
            }
        }

        // 宽松匹配任意 h3 内的 a 文本或其他可能位置的用户名
        if let generic = extract(html: html, pattern: #"<a[^>]*>([\p{Han}]{2,10})</a>"#) {
            let name = generic.trimmingCharacters(in: .whitespacesAndNewlines)
            if isBlacklistedName(name) {
                print("[AuthDebug] parseStudentInfo: generic match '\(name)' rejected by blacklist")
                return nil
            }
            if let sid = extractStudentId(from: html) {
                print("[AuthDebug] parseStudentInfo: got studentId from page -> \(sid)")
                return StudentInfo(name: name, studentId: sid, major: nil, college: nil)
            }
            return StudentInfo(name: name, studentId: "", major: nil, college: nil)
        }

        return nil
    }

    // 在 /home 或 module 页面无法解析姓名时，尝试发现 profile/个人信息 链接并请求以获取用户信息
    private func attemptProfileFallback(basedOn html: String?) async -> StudentInfo? {
        guard let html = html else { return nil }
        var candidateURLs: [URL] = []
        let host = URL(string: "https://mis.bjtu.edu.cn")!

        // 从页面中寻找可能的 profile 链接（包含关键字 profile / user / 个人 / 账户 / student）
        if let regex = try? NSRegularExpression(pattern: "href=[\"']([^\"']+)[\"']", options: []) {
            let range = NSRange(location: 0, length: html.utf16.count)
            regex.enumerateMatches(in: html, options: [], range: range) { match, _, _ in
                guard let m = match, m.numberOfRanges > 1 else { return }
                let r = m.range(at: 1)
                if let swiftRange = Range(r, in: html) {
                    let href = String(html[swiftRange])
                    if href.contains("profile") || href.contains("个人") || href.contains("user") || href.contains("account") || href.contains("student") || href.contains("个人中心") {
                        if let u = URL(string: href, relativeTo: host)?.absoluteURL {
                            candidateURLs.append(u)
                        }
                    }
                }
            }
        }

        // 还尝试一些常见的可能端点作为兜底
        let guesses = ["/user/profile/", "/user/info/", "/accounts/profile/", "/student/profile/", "/student/index/", "/profile/", "/api/user/"]
        for g in guesses {
            if let u = URL(string: g, relativeTo: host)?.absoluteURL {
                candidateURLs.append(u)
            }
        }

        // 去重并尝试每个 URL
        var seen = Set<String>()
        candidateURLs = candidateURLs.filter { url in
            if seen.contains(url.absoluteString) { return false }
            seen.insert(url.absoluteString); return true
        }

        for url in candidateURLs {
            do {
                print("[AuthDebug] Attempting profile fallback -> \(url.absoluteString)")
                let resp = try await networkService.get(url: url)
                let pageHTML = String(data: resp.data, encoding: .utf8)
                logAuthDebug(prefix: "profile_fallback", response: resp, html: pageHTML)
                if let pg = pageHTML, let parsed = parseStudentInfo(from: pg) {
                    return parsed
                }
            } catch {
                print("[AuthDebug] profile fallback error for \(url): \(error)")
            }
        }

        // 未从显式 profile 页面获取到信息，尝试从页面内嵌脚本/JS 发起的 API 端点发现用户信息
        do {
            if let fromScripts = try await discoverUserFromScripts(basedOn: html) {
                return fromScripts
            }
        } catch {
            print("[AuthDebug] discoverUserFromScripts error: \(error)")
        }

        return nil
    }

    // 尝试从页面内嵌脚本中发现 API 端点（fetch/axios/url/"/api/..." 等），并请求这些端点以获取 JSON 中可能的姓名/学号字段
    private func discoverUserFromScripts(basedOn html: String) async throws -> StudentInfo? {
        let host = URL(string: "https://mis.bjtu.edu.cn")!
        var endpoints = Set<String>()

        // 1) fetch('...')
        if let regex = try? NSRegularExpression(pattern: #"fetch\(['\"]([^'\"]+)['\"]"#, options: []) {
            let range = NSRange(location: 0, length: html.utf16.count)
            regex.enumerateMatches(in: html, options: [], range: range) { m, _, _ in
                guard let m = m, m.numberOfRanges > 1 else { return }
                let r = m.range(at: 1)
                if let rr = Range(r, in: html) { endpoints.insert(String(html[rr])) }
            }
        }

        // 2) axios.get('...') 或 axios.post
        if let regex = try? NSRegularExpression(pattern: #"axios\.(?:get|post)\(['\"]([^'\"]+)['\"]"#, options: []) {
            let range = NSRange(location: 0, length: html.utf16.count)
            regex.enumerateMatches(in: html, options: [], range: range) { m, _, _ in
                guard let m = m, m.numberOfRanges > 1 else { return }
                let r = m.range(at: 1)
                if let rr = Range(r, in: html) { endpoints.insert(String(html[rr])) }
            }
        }

        // 3) 查找形如 "/api/..." 或 "/user/..." 的字符串
        if let regex = try? NSRegularExpression(pattern: #"(/(?:api|user|accounts|student|profile)[^'"\s\)\]\}]+)"#, options: []) {
            let range = NSRange(location: 0, length: html.utf16.count)
            regex.enumerateMatches(in: html, options: [], range: range) { m, _, _ in
                guard let m = m, m.numberOfRanges > 1 else { return }
                let r = m.range(at: 1)
                if let rr = Range(r, in: html) { endpoints.insert(String(html[rr])) }
            }
        }

        // 4) JSON 内的 url: '...' 或 "url":"..."
        if let regex = try? NSRegularExpression(pattern: #"["']url["']\s*[:=]\s*["']([^'"\s]+)["']"#, options: []) {
            let range = NSRange(location: 0, length: html.utf16.count)
            regex.enumerateMatches(in: html, options: [], range: range) { m, _, _ in
                guard let m = m, m.numberOfRanges > 1 else { return }
                let r = m.range(at: 1)
                if let rr = Range(r, in: html) { endpoints.insert(String(html[rr])) }
            }
        }

        // 两个常见的兜底 API
        let guesses = ["/api/v1/user/", "/api/me/", "/api/profile/me/", "/api/profile/", "/auth/userinfo/", "/user/info/", "/accounts/profile/", "/users/me/"]
        for g in guesses { endpoints.insert(g) }

        // 构造完整 URL 列表并去重
        var urls: [URL] = []
        for e in endpoints {
            if e.hasPrefix("http://") || e.hasPrefix("https://") {
                if let u = URL(string: e) { urls.append(u) }
            } else if e.hasPrefix("//") {
                if let u = URL(string: "https:" + e) { urls.append(u) }
            } else {
                if let u = URL(string: e, relativeTo: host)?.absoluteURL { urls.append(u) }
            }
        }

        var tried = Set<String>()
        for url in urls {
            if tried.contains(url.absoluteString) { continue }
            tried.insert(url.absoluteString)
            do {
                print("[AuthDebug] Attempting script-discovered endpoint -> \(url.absoluteString)")
                let resp = try await networkService.get(url: url)
                logAuthDebug(prefix: "script_endpoint", response: resp, html: String(data: resp.data, encoding: .utf8))

                // 若返回 JSON，解析并查找姓名/学号字段
                let ct = resp.headers["Content-Type"] ?? resp.headers["content-type"] ?? ""
                if ct.lowercased().contains("application/json") || String(data: resp.data, encoding: .utf8)?.trimmingCharacters(in: .whitespacesAndNewlines).first == "{" {
                    if let obj = try? JSONSerialization.jsonObject(with: resp.data, options: []) {
                        if let name = findNameInJSON(obj) {
                            let sid = findStudentIdInJSON(obj)
                            print("[AuthDebug] discoverUserFromScripts: found name \(name) from \(url.absoluteString)")
                            return StudentInfo(name: name, studentId: sid ?? "")
                        }
                    }
                }

                // 若返回 HTML，则尝试从 HTML 中直接提取姓名
                if let htmlResp = String(data: resp.data, encoding: .utf8) {
                    if let lbl = extract(html: htmlResp, pattern: #"姓名[:：]\s*([^<，,\s]{2,10})"#) {
                        let name = lbl.trimmingCharacters(in: .whitespacesAndNewlines)
                        if !isBlacklistedName(name) { print("[AuthDebug] discoverUserFromScripts: found name \(name) in HTML at \(url.absoluteString)"); return StudentInfo(name: name, studentId: findStudentIdInHTML(htmlResp) ?? "") }
                    }
                    // 继续尝试 parseStudentInfo 作为兜底（但不要无限递归）
                    if let parsed = parseStudentInfo(from: htmlResp) { return parsed }
                }
            } catch {
                print("[AuthDebug] script endpoint request failed for \(url): \(error)")
            }
        }

        return nil
    }

    // 在 JSON 中递归查找姓名字段
    private func findNameInJSON(_ obj: Any) -> String? {
        if let dict = obj as? [String: Any] {
            let nameKeys = ["realname", "realName", "name", "xm", "displayName", "nick", "nickname", "username", "姓名"]
            for k in nameKeys {
                if let v = dict[k] as? String, v.range(of: #"[\u{4E00}-\u{9FA5}]{2,20}"#, options: .regularExpression) != nil {
                    return v
                }
            }
            for (_, v) in dict {
                if let found = findNameInJSON(v) { return found }
            }
        } else if let arr = obj as? [Any] {
            for v in arr { if let found = findNameInJSON(v) { return found } }
        }
        return nil
    }

    // 在 JSON 中查找学号字段
    private func findStudentIdInJSON(_ obj: Any) -> String? {
        if let dict = obj as? [String: Any] {
            let idKeys = ["studentId", "student_id", "学号", "id", "sid", "userId"]
            for k in idKeys {
                if let v = dict[k] as? String { return v }
                if let v = dict[k] as? Int { return String(v) }
            }
            for (_, v) in dict { if let found = findStudentIdInJSON(v) { return found } }
        } else if let arr = obj as? [Any] {
            for v in arr { if let found = findStudentIdInJSON(v) { return found } }
        }
        return nil
    }

    private func findStudentIdInHTML(_ html: String) -> String? {
        if let id = extract(html: html, pattern: "学号[:：]\\s*([0-9]{5,12})") { return id }
        if let regex = try? NSRegularExpression(pattern: "([0-9]{6,12})", options: []) {
            let range = NSRange(location: 0, length: html.utf16.count)
            if let match = regex.firstMatch(in: html, range: range), match.numberOfRanges > 1 {
                let mr = match.range(at: 1)
                if let r = Range(mr, in: html) { return String(html[r]) }
            }
        }
        return nil
    }

    // 多次尝试完成 CAS->MIS 回调并解析学生信息（带重试与退避），用于应对回调异步完成导致的间歇性姓名缺失问题
    private func completeCallbackAndParseStudent(authorizeURL: URL?) async -> StudentInfo? {
        let homeURL = URL(string: "https://mis.bjtu.edu.cn/home/")!
        let moduleURL = URL(string: "https://mis.bjtu.edu.cn/module/module/10/")!
        let maxAttempts = 4

        for attempt in 1...maxAttempts {
            print("[AuthDebug] completeCallbackAndParseStudent: attempt \(attempt)/\(maxAttempts)")

            // 1) try authorize URL if present
            if let aURL = authorizeURL {
                do {
                    let authResp = try await networkService.get(url: aURL)
                    let authHTML = String(data: authResp.data, encoding: .utf8)
                    logAuthDebug(prefix: "authorize_attempt_\(attempt)", response: authResp, html: authHTML)

                    // 如果 authorize 返回的是 /auth/sso 重定向（302 -> /auth/sso/?next=/）
                    if let loc = authResp.headers["Location"] ?? authResp.headers["location"], loc.contains("/auth/sso") {
                        if let ssoURL = URL(string: loc, relativeTo: URL(string: "https://\(casHost)")) {
                            print("[AuthDebug] authorize attempt returned sso redirect; requesting SSO URL: \(ssoURL.absoluteString)")
                            let ssoResp = try await networkService.get(url: ssoURL)
                            let ssoHTML = String(data: ssoResp.data, encoding: .utf8)
                            logAuthDebug(prefix: "sso_follow_\(attempt)", response: ssoResp, html: ssoHTML)

                            // 如果 SSO 又重定向到 MIS callback（Location 包含 mis.bjtu.edu.cn），跟随之
                            if let ssoLoc = ssoResp.headers["Location"] ?? ssoResp.headers["location"], ssoLoc.contains("mis.bjtu.edu.cn") {
                                if let callbackURL = URL(string: ssoLoc) {
                                    print("[AuthDebug] SSO redirected to MIS callback: \(callbackURL.absoluteString); requesting callback")
                                    let cbResp = try await networkService.get(url: callbackURL)
                                    let cbHTML = String(data: cbResp.data, encoding: .utf8)
                                    logAuthDebug(prefix: "callback_follow_\(attempt)", response: cbResp, html: cbHTML)
                                    if let html = cbHTML, let parsed = parseStudentInfo(from: html) {
                                        return parsed
                                    }
                                }
                            }

                            // 也尝试直接从 SSO 返回的 HTML 解析
                            if let html = ssoHTML, let parsed = parseStudentInfo(from: html) {
                                return parsed
                            }
                        }
                    }

                    // 常规路径：若 authorize 返回了可直接包含用户信息的页面，解析之
                }
            }

            // 2) request home
            do {
                let homeResp = try await networkService.get(url: homeURL)
                let homeHTML = String(data: homeResp.data, encoding: .utf8)
                logAuthDebug(prefix: "home_attempt_\(attempt)", response: homeResp, html: homeHTML)
                if let html = homeHTML, let parsed = parseStudentInfo(from: html) {
                    return parsed
                }
            } catch {
                print("[AuthDebug] home attempt \(attempt) failed: \(error)")
            }

            // 3) try module/10
            var moduleHTML: String? = nil
            do {
                let moduleResp = try await networkService.get(url: moduleURL)
                moduleHTML = String(data: moduleResp.data, encoding: .utf8)
                logAuthDebug(prefix: "module_attempt_\(attempt)", response: moduleResp, html: moduleHTML)
                if let html = moduleHTML, let parsed = parseStudentInfo(from: html) {
                    return parsed
                }
                // attempt profile fallback on module html
                if let html = moduleHTML {
                    if let parsed = try await attemptProfileFallback(basedOn: html) {
                        return parsed
                    }
                }
            } catch {
                print("[AuthDebug] module attempt \(attempt) failed: \(error)")
            }

            // 4) attempt to discover script endpoints from the last module/home HTML
            do {
                if let html = moduleHTML {
                    if let parsed = try await discoverUserFromScripts(basedOn: html) {
                        return parsed
                    }
                }
            } catch {
                print("[AuthDebug] script discovery attempt \(attempt) failed: \(error)")
            }

            // 等待再试（指数退避或固定等待）
            if attempt < maxAttempts {
                try? await Task.sleep(nanoseconds: 700_000_000) // 700ms
            }
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

    // 根据登录相关页面的 HTML 内容推断失败原因，并返回适合展示的本地化消息（优先识别验证码错误）
    private func detectLoginFailureMessage(from htmls: [String?]) -> String? {
        // 关键词（包含中文/英文常见变体）
        let captchaKeywords = ["认证码错误", "验证码错误", "验证码不正确", "验证码有误", "invalid captcha", "incorrect captcha"]
        let passwordKeywords = ["用户密码不正确", "用户名或密码错误", "用户名或密码不正确", "密码错误", "incorrect username or password", "invalid login"]

        for html in htmls.compactMap({ $0?.lowercased() }) {
            for k in captchaKeywords {
                if html.contains(k.lowercased()) {
                    return "验证码错误，请重新输入验证码"
                }
            }
            for k in passwordKeywords {
                if html.contains(k.lowercased()) {
                    return "用户名或密码不正确，请检查后重试"
                }
            }
        }
        return nil
    }
}


private extension String {
    var nonEmpty: String? { isEmpty ? nil : self }
}
