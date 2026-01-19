//
//  CookieStore.swift
//  BJTUselfServiceApple
//
//  Created on 2026/1/19.
//

import Foundation

/// Cookie 存储管理
@MainActor
class CookieStore {
    static let shared = CookieStore()
    
    private let storage: HTTPCookieStorage
    
    /// 初始化
    /// - Parameter storage: HTTPCookieStorage实例，默认使用共享存储
    init(storage: HTTPCookieStorage = .shared) {
        self.storage = storage
        self.storage.cookieAcceptPolicy = .always
    }
    
    /// 存储 Cookie
    /// - Parameter cookies: 要存储的Cookie数组
    func store(cookies: [HTTPCookie]) {
        cookies.forEach { cookie in
            storage.setCookie(cookie)
        }
    }
    
    /// 存储单个 Cookie
    /// - Parameter cookie: 要存储的Cookie
    func store(cookie: HTTPCookie) {
        storage.setCookie(cookie)
    }
    
    /// 获取所有 Cookie
    /// - Returns: 所有存储的Cookie
    func allCookies() -> [HTTPCookie] {
        return storage.cookies ?? []
    }
    
    /// 获取指定 URL 的 Cookie
    /// - Parameter url: 目标URL
    /// - Returns: 适用于该URL的Cookie数组
    func cookies(for url: URL) -> [HTTPCookie]? {
        return storage.cookies(for: url)
    }
    
    /// 获取指定域名的 Cookie
    /// - Parameter domain: 域名
    /// - Returns: 该域名下的所有Cookie
    func cookies(forDomain domain: String) -> [HTTPCookie] {
        return allCookies().filter { cookie in
            cookie.domain == domain || cookie.domain.hasSuffix(".\(domain)")
        }
    }
    
    /// 获取指定名称的 Cookie
    /// - Parameters:
    ///   - name: Cookie名称
    ///   - url: 目标URL（可选）
    /// - Returns: 找到的Cookie
    func cookie(named name: String, for url: URL? = nil) -> HTTPCookie? {
        if let url = url {
            return cookies(for: url)?.first { $0.name == name }
        } else {
            return allCookies().first { $0.name == name }
        }
    }
    
    /// 删除指定 Cookie
    /// - Parameter cookie: 要删除的Cookie
    func delete(cookie: HTTPCookie) {
        storage.deleteCookie(cookie)
    }
    
    /// 删除指定 URL 的所有 Cookie
    /// - Parameter url: 目标URL
    func deleteCookies(for url: URL) {
        cookies(for: url)?.forEach { cookie in
            storage.deleteCookie(cookie)
        }
    }
    
    /// 删除指定域名的所有 Cookie
    /// - Parameter domain: 域名
    func deleteCookies(forDomain domain: String) {
        cookies(forDomain: domain).forEach { cookie in
            storage.deleteCookie(cookie)
        }
    }
    
    /// 清空所有 Cookie
    func clearAll() {
        allCookies().forEach { cookie in
            storage.deleteCookie(cookie)
        }
    }
    
    /// 打印所有 Cookie（调试用）
    func printAllCookies() {
        let cookies = allCookies()
        print("=== 总共 \(cookies.count) 个 Cookie ===")
        cookies.forEach { cookie in
            print("Name: \(cookie.name)")
            print("Value: \(cookie.value)")
            print("Domain: \(cookie.domain)")
            print("Path: \(cookie.path)")
            print("Expires: \(cookie.expiresDate?.description ?? "Session")")
            print("---")
        }
    }
    
    /// 导出 Cookie 为字典（用于持久化）
    /// - Returns: Cookie字典数组
    func exportCookies() -> [[HTTPCookiePropertyKey: Any]] {
        return allCookies().compactMap { $0.properties }
    }
    
    /// 从字典导入 Cookie
    /// - Parameter cookiesData: Cookie字典数组
    func importCookies(from cookiesData: [[HTTPCookiePropertyKey: Any]]) {
        cookiesData.forEach { properties in
            if let cookie = HTTPCookie(properties: properties) {
                store(cookie: cookie)
            }
        }
    }
}

// MARK: - Cookie 构建器
extension CookieStore {
    /// 创建一个新的 Cookie
    /// - Parameters:
    ///   - name: Cookie名称
    ///   - value: Cookie值
    ///   - domain: 域名
    ///   - path: 路径，默认为 "/"
    ///   - expiresDate: 过期时间，nil表示会话Cookie
    /// - Returns: 创建的Cookie
    static func createCookie(
        name: String,
        value: String,
        domain: String,
        path: String = "/",
        expiresDate: Date? = nil
    ) -> HTTPCookie? {
        var properties: [HTTPCookiePropertyKey: Any] = [
            .name: name,
            .value: value,
            .domain: domain,
            .path: path
        ]
        
        if let expiresDate = expiresDate {
            properties[.expires] = expiresDate
        }
        
        return HTTPCookie(properties: properties)
    }
}
