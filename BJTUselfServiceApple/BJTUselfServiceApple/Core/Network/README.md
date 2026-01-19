# NetworkClient 和 CookieStore 使用指南

## 概述

这是一个基于 Swift 原生 URLSession 的网络层实现，包含自动 Cookie 管理功能。

## 核心组件

### 1. NetworkClient

支持 GET/POST/PUT/DELETE 请求，自动处理 Cookie 的发送和接收。

**特性：**
- ✅ 可注入 URLSession（便于单元测试）
- ✅ 自动添加 User-Agent
- ✅ 自动发送和接收 Cookie
- ✅ 返回完整的响应信息（statusCode, data, headers）
- ✅ 支持表单和 JSON 数据提交

**基础用法：**

```swift
let client = NetworkClient()

// GET 请求
let response = try await client.get(
    url: URL(string: "https://api.example.com/data")!
)

// POST 表单
let response = try await client.postForm(
    url: URL(string: "https://api.example.com/login")!,
    parameters: [
        "username": "user123",
        "password": "pass456"
    ]
)

// POST JSON
struct LoginRequest: Codable {
    let username: String
    let password: String
}

let response = try await client.postJSON(
    url: URL(string: "https://api.example.com/login")!,
    body: LoginRequest(username: "user", password: "pass")
)

// 自定义请求头
let response = try await client.get(
    url: url,
    headers: [
        "Authorization": "Bearer token123",
        "Custom-Header": "value"
    ]
)
```

### 2. CookieStore

封装 HTTPCookieStorage，提供便捷的 Cookie 管理接口。

**特性：**
- ✅ 自动保存响应中的 Cookie
- ✅ 自动在请求中附加 Cookie
- ✅ 支持按域名、URL、名称查询
- ✅ 支持导出/导入（用于持久化）
- ✅ 调试友好（printAllCookies）

**基础用法：**

```swift
let cookieStore = CookieStore.shared

// 手动创建和存储 Cookie
if let cookie = CookieStore.createCookie(
    name: "session_id",
    value: "abc123",
    domain: "example.com"
) {
    cookieStore.store(cookie: cookie)
}

// 查询 Cookie
let allCookies = cookieStore.allCookies()
let urlCookies = cookieStore.cookies(for: url)
let cookie = cookieStore.cookie(named: "session_id")

// 删除 Cookie
cookieStore.delete(cookie: cookie)
cookieStore.deleteCookies(forDomain: "example.com")
cookieStore.clearAll()

// 导出和导入（持久化）
let exported = cookieStore.exportCookies()
UserDefaults.standard.set(exported, forKey: "cookies")

let saved = UserDefaults.standard.array(forKey: "cookies") as? [[HTTPCookiePropertyKey: Any]]
cookieStore.importCookies(from: saved ?? [])
```

### 3. NetworkResponse

统一的响应结构：

```swift
struct NetworkResponse {
    let statusCode: Int      // HTTP 状态码
    let data: Data          // 响应数据
    let headers: [String: String]  // 响应头
    var isSuccess: Bool     // 200-299 为成功
}

// 使用示例
let response = try await client.get(url: url)
if response.isSuccess {
    let json = try JSONDecoder().decode(MyModel.self, from: response.data)
    print("成功: \(json)")
}
```

## 完整示例：模拟登录流程

```swift
@MainActor
class LoginManager {
    private let client = NetworkClient()
    private let cookieStore = CookieStore.shared
    
    func login(username: String, password: String) async throws -> Bool {
        // 1. 清除旧的登录状态
        cookieStore.clearAll()
        
        // 2. 发送登录请求
        let loginURL = URL(string: "https://example.com/api/login")!
        let response = try await client.postForm(
            url: loginURL,
            parameters: [
                "username": username,
                "password": password
            ]
        )
        
        // 3. Cookie 已自动保存，检查登录状态
        if response.isSuccess {
            // 4. 验证是否有 session cookie
            if let sessionCookie = cookieStore.cookie(named: "JSESSIONID") {
                print("登录成功，Session: \(sessionCookie.value)")
                return true
            }
        }
        
        return false
    }
    
    func fetchUserData() async throws -> Data {
        // Cookie 会自动附加到请求中
        let url = URL(string: "https://example.com/api/user")!
        let response = try await client.get(url: url)
        return response.data
    }
    
    func logout() {
        cookieStore.clearAll()
    }
}
```

## 单元测试

使用 Mock URLProtocol 进行测试：

```swift
@MainActor
final class NetworkTests: XCTestCase {
    var client: NetworkClient!
    var mockSession: URLSession!
    
    override func setUp() async throws {
        // 创建使用 MockURLProtocol 的 URLSession
        let config = URLSessionConfiguration.ephemeral
        config.protocolClasses = [MockURLProtocol.self]
        mockSession = URLSession(configuration: config)
        
        client = NetworkClient(session: mockSession)
    }
    
    func testCookieAutoSend() async throws {
        // 见 NetworkClientTests.swift 完整示例
    }
}
```

## 运行 Mock 示例

在任何 View 或 ViewModel 中：

```swift
Task {
    let example = NetworkMockExample()
    await example.runAllExamples()
}
```

查看控制台输出，可以看到：
- ✅ Cookie 自动保存
- ✅ Cookie 自动发送
- ✅ 表单提交
- ✅ Cookie 持久化
- ✅ 多域名管理

## 实际应用：MIS 系统登录

```swift
@MainActor
class MISAuthService {
    private let client = NetworkClient()
    private let cookieStore = CookieStore.shared
    private let baseURL = "https://mis.bjtu.edu.cn"
    
    func login(username: String, password: String, captcha: String) async throws -> Bool {
        // 1. 获取登录页面（获取初始 Cookie）
        let loginPageURL = URL(string: "\(baseURL)/login")!
        _ = try await client.get(url: loginPageURL)
        
        // 2. 提交登录表单
        let response = try await client.postForm(
            url: loginPageURL,
            parameters: [
                "username": username,
                "password": password,
                "captcha": captcha,
                "submit": "登录"
            ]
        )
        
        // 3. 检查是否有认证 Cookie
        if let authCookie = cookieStore.cookie(named: "JSESSIONID") {
            print("✅ 登录成功，Session: \(authCookie.value)")
            return true
        }
        
        return false
    }
    
    func fetchGrades() async throws -> [Grade] {
        let url = URL(string: "\(baseURL)/api/grades")!
        let response = try await client.get(url: url)
        
        // Cookie 会自动附加，保持登录状态
        let grades = try JSONDecoder().decode([Grade].self, from: response.data)
        return grades
    }
}
```

## 调试技巧

```swift
// 打印所有 Cookie
CookieStore.shared.printAllCookies()

// 打印特定域名的 Cookie
let cookies = CookieStore.shared.cookies(forDomain: "example.com")
cookies.forEach { cookie in
    print("\(cookie.name) = \(cookie.value)")
}

// 检查请求是否携带 Cookie
let url = URL(string: "https://httpbin.org/cookies")!
let response = try await NetworkClient().get(url: url)
print(String(data: response.data, encoding: .utf8) ?? "")
```

## 最佳实践

1. **单例模式**：在应用中使用 `CookieStore.shared` 和 `NetworkClient.shared`
2. **依赖注入**：测试时注入 mock URLSession
3. **Cookie 持久化**：App 启动时导入，退出时导出
4. **安全性**：敏感 Cookie 使用 Keychain 存储
5. **调试**：开发时使用 `printAllCookies()` 检查状态

## 性能优化

- ✅ 使用单例避免重复创建 URLSession
- ✅ 复用同一个 HTTPCookieStorage
- ✅ 避免频繁清空 Cookie
- ✅ 大文件上传使用流式传输（待实现）
