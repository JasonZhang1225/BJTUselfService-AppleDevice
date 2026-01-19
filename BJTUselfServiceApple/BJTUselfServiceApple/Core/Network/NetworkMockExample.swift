//
//  NetworkMockExample.swift
//  BJTUselfServiceApple
//
//  Created on 2026/1/19.
//
//  这是一个可运行的 Mock 示例，演示 NetworkClient 和 CookieStore 的使用

import Foundation

/// Mock 示例：演示登录流程中的 Cookie 自动管理
@MainActor
class NetworkMockExample {
    private let client: NetworkClient
    private let cookieStore: CookieStore
    
    init() {
        self.cookieStore = CookieStore.shared
        self.client = NetworkClient(session: URLSession.shared, cookieStore: cookieStore)
    }
    
    /// 示例1: 模拟登录并自动保存 Cookie
    func exampleLogin() async {
        print("=== 示例1: 登录流程 ===")
        
        do {
            // 清空之前的 Cookie
            cookieStore.clearAll()
            
            let loginURL = URL(string: "https://httpbin.org/cookies/set/session_id/abc123")!
            
            print("1. 发送登录请求...")
            let response = try await client.get(url: loginURL)
            
            print("2. 登录响应状态码: \(response.statusCode)")
            
            // Cookie 会自动保存到 CookieStore
            print("3. 检查保存的 Cookie:")
            cookieStore.printAllCookies()
            
            // 验证 Cookie 是否存在
            if let sessionCookie = cookieStore.cookie(named: "session_id") {
                print("✅ Session Cookie 已保存: \(sessionCookie.value)")
            }
            
        } catch {
            print("❌ 错误: \(error)")
        }
    }
    
    /// 示例2: 使用已保存的 Cookie 发送请求
    func exampleAuthenticatedRequest() async {
        print("\n=== 示例2: 携带 Cookie 的请求 ===")
        
        do {
            // 先手动创建一个 Cookie（模拟已登录状态）
            if let cookie = CookieStore.createCookie(
                name: "auth_token",
                value: "my_secret_token_12345",
                domain: "httpbin.org"
            ) {
                cookieStore.store(cookie: cookie)
                print("1. 手动存储 Cookie: auth_token=my_secret_token_12345")
            }
            
            // 发送请求，Cookie 会自动附加
            let url = URL(string: "https://httpbin.org/cookies")!
            print("2. 发送请求到: \(url)")
            
            let response = try await client.get(url: url)
            
            print("3. 响应状态码: \(response.statusCode)")
            
            // httpbin.org/cookies 会返回它收到的所有 Cookie
            if let json = try? JSONSerialization.jsonObject(with: response.data) as? [String: Any],
               let cookies = json["cookies"] as? [String: String] {
                print("4. 服务器收到的 Cookie:")
                cookies.forEach { key, value in
                    print("   \(key) = \(value)")
                }
            }
            
        } catch {
            print("❌ 错误: \(error)")
        }
    }
    
    /// 示例3: POST 表单数据
    func examplePostForm() async {
        print("\n=== 示例3: POST 表单数据 ===")
        
        do {
            let url = URL(string: "https://httpbin.org/post")!
            
            let formData = [
                "username": "testuser",
                "password": "testpass123"
            ]
            
            print("1. 发送 POST 表单: \(formData)")
            
            let response = try await client.postForm(
                url: url,
                parameters: formData
            )
            
            print("2. 响应状态码: \(response.statusCode)")
            
            if let json = try? JSONSerialization.jsonObject(with: response.data) as? [String: Any],
               let form = json["form"] as? [String: String] {
                print("3. 服务器收到的表单数据:")
                form.forEach { key, value in
                    print("   \(key) = \(value)")
                }
            }
            
        } catch {
            print("❌ 错误: \(error)")
        }
    }
    
    /// 示例4: Cookie 的导出和导入
    func exampleCookiePersistence() {
        print("\n=== 示例4: Cookie 持久化 ===")
        
        // 创建一些测试 Cookie
        let testCookies = [
            CookieStore.createCookie(name: "user_id", value: "12345", domain: "example.com"),
            CookieStore.createCookie(name: "session", value: "abcdef", domain: "example.com"),
            CookieStore.createCookie(name: "token", value: "xyz789", domain: "api.example.com")
        ]
        
        testCookies.compactMap { $0 }.forEach { cookie in
            cookieStore.store(cookie: cookie)
        }
        
        print("1. 创建了 \(testCookies.count) 个 Cookie")
        
        // 导出
        let exported = cookieStore.exportCookies()
        print("2. 导出 Cookie 数据: \(exported.count) 个")
        
        // 模拟持久化（实际应用中可以保存到 UserDefaults 或文件）
        let jsonData = try? JSONSerialization.data(withJSONObject: exported, options: .prettyPrinted)
        if let jsonString = jsonData.flatMap({ String(data: $0, encoding: .utf8) }) {
            print("3. Cookie JSON 数据（可保存到本地）:")
            print(jsonString.prefix(200)) // 只打印前200个字符
        }
        
        // 清空
        cookieStore.clearAll()
        print("4. 清空所有 Cookie，当前数量: \(cookieStore.allCookies().count)")
        
        // 导入
        cookieStore.importCookies(from: exported)
        print("5. 从导出数据恢复，当前数量: \(cookieStore.allCookies().count)")
        
        // 验证
        if let cookie = cookieStore.cookie(named: "user_id") {
            print("✅ Cookie 恢复成功: user_id=\(cookie.value)")
        }
    }
    
    /// 示例5: 多域名 Cookie 管理
    func exampleMultiDomainCookies() {
        print("\n=== 示例5: 多域名 Cookie 管理 ===")
        
        cookieStore.clearAll()
        
        // 创建不同域名的 Cookie
        let domains = ["example.com", "test.com", "api.example.com"]
        domains.forEach { domain in
            if let cookie = CookieStore.createCookie(
                name: "token",
                value: "token_for_\(domain)",
                domain: domain
            ) {
                cookieStore.store(cookie: cookie)
            }
        }
        
        print("1. 创建了 \(domains.count) 个不同域名的 Cookie")
        
        // 按域名查询
        domains.forEach { domain in
            let cookies = cookieStore.cookies(forDomain: domain)
            print("2. 域名 \(domain) 的 Cookie: \(cookies.count) 个")
            cookies.forEach { cookie in
                print("   \(cookie.name) = \(cookie.value)")
            }
        }
        
        // 删除特定域名的 Cookie
        cookieStore.deleteCookies(forDomain: "test.com")
        print("3. 删除 test.com 的 Cookie 后，总数: \(cookieStore.allCookies().count)")
    }
    
    /// 运行所有示例
    func runAllExamples() async {
        await exampleLogin()
        await exampleAuthenticatedRequest()
        await examplePostForm()
        exampleCookiePersistence()
        exampleMultiDomainCookies()
        
        print("\n=== 所有示例运行完成 ===")
    }
}

// MARK: - 使用示例
/*
 在 View 或 ViewModel 中使用:
 
 Task {
     let example = NetworkMockExample()
     await example.runAllExamples()
 }
 
 或者单独运行某个示例:
 
 Task {
     let example = NetworkMockExample()
     await example.exampleLogin()
 }
 */
