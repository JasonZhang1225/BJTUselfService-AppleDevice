//
//  NetworkClient.swift
//  BJTUselfServiceApple
//
//  Created on 2026/1/19.
//

import Foundation

/// HTTP 方法
enum HTTPMethod: String {
    case get = "GET"
    case post = "POST"
    case put = "PUT"
    case delete = "DELETE"
}

/// 网络响应
struct NetworkResponse {
    let statusCode: Int
    let data: Data
    let headers: [String: String]
    let finalURL: URL?
    
    var isSuccess: Bool {
        return (200..<300).contains(statusCode)
    }
}

/// 网络请求错误
enum NetworkError: Error, LocalizedError {
    case invalidURL
    case invalidResponse
    case httpError(statusCode: Int)
    case noData
    case encodingFailed
    case tooManyRedirects
    
    var errorDescription: String? {
        switch self {
        case .invalidURL:
            return "无效的URL"
        case .invalidResponse:
            return "无效的响应"
        case .httpError(let code):
            return "HTTP错误: \(code)"
        case .noData:
            return "没有数据"
        case .encodingFailed:
            return "编码失败"
        case .tooManyRedirects:
            return "重定向过多"
        }
    }
}

/// 处理重定向，避免 CAS i18n/setlang 死循环
final class RedirectHandler: NSObject, URLSessionTaskDelegate {
    private let maxRedirects = 10
    private var redirectCount: [Int: Int] = [:]

    func urlSession(
        _ session: URLSession,
        task: URLSessionTask,
        willPerformHTTPRedirection response: HTTPURLResponse,
        newRequest request: URLRequest,
        completionHandler: @escaping (URLRequest?) -> Void
    ) {
        let count = (redirectCount[task.taskIdentifier] ?? 0) + 1
        redirectCount[task.taskIdentifier] = count

        if let url = request.url, url.absoluteString.contains("/i18n/setlang/") {
            // 阻断 setlang 循环，直接返回当前响应
            completionHandler(nil)
            return
        }

        if count > maxRedirects {
            completionHandler(nil)
            return
        }

        completionHandler(request)
    }
}

/// 网络客户端
@MainActor
class NetworkClient {
    private let session: URLSession
    private let redirectHandler: RedirectHandler
    private let cookieStore: CookieStore
    
    /// 初始化
    /// - Parameters:
    ///   - session: URLSession实例
    ///   - cookieStore: Cookie存储
    ///   - redirectHandler: 重定向处理器
    nonisolated init(
        session: URLSession,
        cookieStore: CookieStore,
        redirectHandler: RedirectHandler = RedirectHandler()
    ) {
        self.session = session
        self.cookieStore = cookieStore
        self.redirectHandler = redirectHandler
    }
    
    /// 便捷初始化，使用默认的共享实例
    convenience init() {
        let handler = RedirectHandler()
        let session = NetworkClient.makeSession(delegate: handler)
        self.init(session: session, cookieStore: .shared, redirectHandler: handler)
    }

    /// 自定义 session，拦截 setlang 重定向以避免循环
    private static func makeSession(delegate: RedirectHandler) -> URLSession {
        let config = URLSessionConfiguration.default
        config.httpCookieAcceptPolicy = .always
        config.httpShouldSetCookies = true
        return URLSession(configuration: config, delegate: delegate, delegateQueue: nil)
    }
    
    /// 执行网络请求
    /// - Parameters:
    ///   - url: 请求URL
    ///   - method: HTTP方法
    ///   - headers: 请求头
    ///   - body: 请求体
    ///   - timeout: 超时时间（秒）
    /// - Returns: 网络响应
    func request(
        url: URL,
        method: HTTPMethod = .get,
        headers: [String: String]? = nil,
        body: Data? = nil,
        timeout: TimeInterval = 30
    ) async throws -> NetworkResponse {
        var request = URLRequest(url: url, timeoutInterval: timeout)
        request.httpMethod = method.rawValue
        request.httpBody = body
        
        // 设置默认 User-Agent (使用 Android 端验证过的 UA)
        request.setValue(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36 Edg/142.0.0.0",
            forHTTPHeaderField: "User-Agent"
        )
        // 默认语言
        if request.value(forHTTPHeaderField: "Accept-Language") == nil {
            request.setValue("zh-CN,zh;q=0.9", forHTTPHeaderField: "Accept-Language")
        }
        
        // 添加自定义请求头
        headers?.forEach { key, value in
            request.setValue(value, forHTTPHeaderField: key)
        }
        
        // 自动添加 Cookie
        if let cookies = cookieStore.cookies(for: url) {
            let cookieHeader = HTTPCookie.requestHeaderFields(with: cookies)
            cookieHeader.forEach { key, value in
                request.setValue(value, forHTTPHeaderField: key)
            }
        }
        
        // 执行请求
        let (data, response) = try await session.data(for: request)
        
        guard let httpResponse = response as? HTTPURLResponse else {
            throw NetworkError.invalidResponse
        }
        
        // 自动保存响应中的 Cookie
        if let headerFields = httpResponse.allHeaderFields as? [String: String] {
            let cookies = HTTPCookie.cookies(
                withResponseHeaderFields: headerFields,
                for: url
            )
            cookieStore.store(cookies: cookies)
        }
        
        // 提取响应头
        let responseHeaders = httpResponse.allHeaderFields.reduce(into: [String: String]()) { result, element in
            if let key = element.key as? String, let value = element.value as? String {
                result[key] = value
            }
        }
        
        return NetworkResponse(
            statusCode: httpResponse.statusCode,
            data: data,
            headers: responseHeaders,
            finalURL: httpResponse.url
        )
    }
    
    /// GET 请求
    func get(
        url: URL,
        headers: [String: String]? = nil,
        timeout: TimeInterval = 30
    ) async throws -> NetworkResponse {
        try await request(url: url, method: .get, headers: headers, timeout: timeout)
    }
    
    /// POST 请求
    func post(
        url: URL,
        headers: [String: String]? = nil,
        body: Data? = nil,
        timeout: TimeInterval = 30
    ) async throws -> NetworkResponse {
        try await request(url: url, method: .post, headers: headers, body: body, timeout: timeout)
    }
    
    /// POST JSON
    func postJSON<T: Encodable>(
        url: URL,
        body: T,
        headers: [String: String]? = nil,
        timeout: TimeInterval = 30
    ) async throws -> NetworkResponse {
        let encoder = JSONEncoder()
        guard let jsonData = try? encoder.encode(body) else {
            throw NetworkError.encodingFailed
        }
        
        var allHeaders = headers ?? [:]
        allHeaders["Content-Type"] = "application/json"
        
        return try await post(url: url, headers: allHeaders, body: jsonData, timeout: timeout)
    }
    
    /// POST 表单数据
    func postForm(
        url: URL,
        parameters: [String: String],
        headers: [String: String]? = nil,
        timeout: TimeInterval = 30
    ) async throws -> NetworkResponse {
        let formData = parameters
            .map { "\($0.key)=\($0.value.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? "")" }
            .joined(separator: "&")
            .data(using: .utf8)
        
        var allHeaders = headers ?? [:]
        allHeaders["Content-Type"] = "application/x-www-form-urlencoded"
        
        return try await post(url: url, headers: allHeaders, body: formData, timeout: timeout)
    }
}

