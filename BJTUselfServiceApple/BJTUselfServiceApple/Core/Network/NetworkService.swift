//
//  NetworkService.swift
//  BJTUselfServiceApple
//
//  Created on 2026/1/19.
//

import Foundation
import Combine

/// 网络请求服务（高层封装，使用 NetworkClient）
@MainActor
class NetworkService: ObservableObject {
    static let shared = NetworkService()
    
    private let client: NetworkClient
    private let cookieStore: CookieStore
    
    private init() {
        self.cookieStore = CookieStore.shared
        // 使用默认构造器以启用 RedirectHandler
        self.client = NetworkClient()
    }
    
    /// 执行 GET 请求
    func get(url: URL, headers: [String: String]? = nil) async throws -> NetworkResponse {
        try await client.get(url: url, headers: headers)
    }
    
    /// 执行 POST 请求
    func post(url: URL, headers: [String: String]? = nil, body: Data? = nil) async throws -> NetworkResponse {
        try await client.post(url: url, headers: headers, body: body)
    }
    
    /// POST 表单数据
    func postForm(url: URL, parameters: [String: String], headers: [String: String]? = nil) async throws -> NetworkResponse {
        try await client.postForm(url: url, parameters: parameters, headers: headers)
    }
    
    /// 获取所有 Cookie
    func getCookies() -> [HTTPCookie] {
        cookieStore.allCookies()
    }
    
    /// 获取指定 URL 的 Cookie
    func getCookies(for url: URL) -> [HTTPCookie]? {
        cookieStore.cookies(for: url)
    }
    
    /// 清除所有 Cookie
    func clearCookies() {
        cookieStore.clearAll()
    }
    
    /// 打印所有 Cookie（调试用）
    func printCookies() {
        cookieStore.printAllCookies()
    }
}
