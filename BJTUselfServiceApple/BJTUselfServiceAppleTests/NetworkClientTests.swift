//
//  NetworkClientTests.swift
//  BJTUselfServiceAppleTests
//
//  Created on 2026/1/19.
//

import XCTest
@testable import BJTUselfServiceApple

/// Mock URLProtocol 用于测试
class MockURLProtocol: URLProtocol {
    static var mockResponses: [URL: (Data, HTTPURLResponse)] = [:]
    static var requestHeaders: [URL: [String: String]] = [:]
    
    override class func canInit(with request: URLRequest) -> Bool {
        return true
    }
    
    override class func canonicalRequest(for request: URLRequest) -> URLRequest {
        return request
    }
    
    override func startLoading() {
        guard let url = request.url,
              let (data, response) = MockURLProtocol.mockResponses[url] else {
            client?.urlProtocol(self, didFailWithError: URLError(.badURL))
            return
        }
        
        // 记录请求头（包括Cookie）
        if let headers = request.allHTTPHeaderFields {
            MockURLProtocol.requestHeaders[url] = headers
        }
        
        client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
        client?.urlProtocol(self, didLoad: data)
        client?.urlProtocolDidFinishLoading(self)
    }
    
    override func stopLoading() {}
}

@MainActor
final class NetworkClientTests: XCTestCase {
    var client: NetworkClient!
    var cookieStore: CookieStore!
    var mockSession: URLSession!
    
    override func setUp() async throws {
        // 创建使用 MockURLProtocol 的 URLSession
        let config = URLSessionConfiguration.ephemeral
        config.protocolClasses = [MockURLProtocol.self]
        config.httpCookieStorage = HTTPCookieStorage()
        mockSession = URLSession(configuration: config)
        
        // 创建独立的 CookieStore
        cookieStore = CookieStore(storage: config.httpCookieStorage!)
        
        // 创建 NetworkClient
        client = NetworkClient(session: mockSession, cookieStore: cookieStore)
        
        // 清空 mock 数据
        MockURLProtocol.mockResponses.removeAll()
        MockURLProtocol.requestHeaders.removeAll()
    }
    
    override func tearDown() async throws {
        cookieStore.clearAll()
        client = nil
        mockSession = nil
        cookieStore = nil
    }
    
    // MARK: - 基础请求测试
    
    func testGETRequest() async throws {
        let url = URL(string: "https://example.com/api/test")!
        let mockData = "Hello World".data(using: .utf8)!
        let mockResponse = HTTPURLResponse(
            url: url,
            statusCode: 200,
            httpVersion: nil,
            headerFields: ["Content-Type": "text/plain"]
        )!
        
        MockURLProtocol.mockResponses[url] = (mockData, mockResponse)
        
        let response = try await client.get(url: url)
        
        XCTAssertEqual(response.statusCode, 200)
        XCTAssertEqual(response.data, mockData)
        XCTAssertTrue(response.isSuccess)
        XCTAssertEqual(response.headers["Content-Type"], "text/plain")
    }
    
    func testPOSTRequest() async throws {
        let url = URL(string: "https://example.com/api/login")!
        let requestBody = "username=test&password=123".data(using: .utf8)!
        let mockData = "{\"success\": true}".data(using: .utf8)!
        let mockResponse = HTTPURLResponse(
            url: url,
            statusCode: 200,
            httpVersion: nil,
            headerFields: ["Content-Type": "application/json"]
        )!
        
        MockURLProtocol.mockResponses[url] = (mockData, mockResponse)
        
        let response = try await client.post(
            url: url,
            headers: ["Content-Type": "application/x-www-form-urlencoded"],
            body: requestBody
        )
        
        XCTAssertEqual(response.statusCode, 200)
        XCTAssertTrue(response.isSuccess)
    }
    
    // MARK: - Cookie 自动管理测试
    
    func testCookieAutoStorage() async throws {
        let url = URL(string: "https://example.com/api/login")!
        let mockData = "OK".data(using: .utf8)!
        
        // 模拟服务器返回 Set-Cookie
        let mockResponse = HTTPURLResponse(
            url: url,
            statusCode: 200,
            httpVersion: nil,
            headerFields: [
                "Set-Cookie": "session_id=abc123; Path=/; Domain=example.com"
            ]
        )!
        
        MockURLProtocol.mockResponses[url] = (mockData, mockResponse)
        
        // 执行请求
        _ = try await client.get(url: url)
        
        // 验证 Cookie 已被存储
        let cookies = cookieStore.cookies(for: url)
        XCTAssertNotNil(cookies)
        XCTAssertEqual(cookies?.count, 1)
        XCTAssertEqual(cookies?.first?.name, "session_id")
        XCTAssertEqual(cookies?.first?.value, "abc123")
    }
    
    func testCookieAutoSend() async throws {
        let url = URL(string: "https://example.com/api/data")!
        
        // 先手动存储一个 Cookie
        if let cookie = CookieStore.createCookie(
            name: "auth_token",
            value: "xyz789",
            domain: "example.com"
        ) {
            cookieStore.store(cookie: cookie)
        }
        
        let mockData = "Data".data(using: .utf8)!
        let mockResponse = HTTPURLResponse(
            url: url,
            statusCode: 200,
            httpVersion: nil,
            headerFields: [:]
        )!
        
        MockURLProtocol.mockResponses[url] = (mockData, mockResponse)
        
        // 执行请求
        _ = try await client.get(url: url)
        
        // 验证请求头中包含 Cookie
        let headers = MockURLProtocol.requestHeaders[url]
        XCTAssertNotNil(headers?["Cookie"])
        XCTAssertTrue(headers?["Cookie"]?.contains("auth_token=xyz789") ?? false)
    }
    
    // MARK: - CookieStore 功能测试
    
    func testCookieStoreOperations() {
        let url = URL(string: "https://test.com")!
        
        // 创建并存储 Cookie
        if let cookie1 = CookieStore.createCookie(
            name: "cookie1",
            value: "value1",
            domain: "test.com"
        ) {
            cookieStore.store(cookie: cookie1)
        }
        
        if let cookie2 = CookieStore.createCookie(
            name: "cookie2",
            value: "value2",
            domain: "test.com"
        ) {
            cookieStore.store(cookie: cookie2)
        }
        
        // 测试获取所有 Cookie
        let allCookies = cookieStore.allCookies()
        XCTAssertEqual(allCookies.count, 2)
        
        // 测试按名称查找
        let cookie = cookieStore.cookie(named: "cookie1", for: url)
        XCTAssertNotNil(cookie)
        XCTAssertEqual(cookie?.value, "value1")
        
        // 测试删除
        if let cookie = cookie {
            cookieStore.delete(cookie: cookie)
        }
        XCTAssertEqual(cookieStore.allCookies().count, 1)
        
        // 测试清空
        cookieStore.clearAll()
        XCTAssertEqual(cookieStore.allCookies().count, 0)
    }
    
    func testCookieExportImport() {
        // 创建并存储 Cookie
        if let cookie = CookieStore.createCookie(
            name: "test",
            value: "data",
            domain: "example.com"
        ) {
            cookieStore.store(cookie: cookie)
        }
        
        // 导出
        let exported = cookieStore.exportCookies()
        XCTAssertEqual(exported.count, 1)
        
        // 清空
        cookieStore.clearAll()
        XCTAssertEqual(cookieStore.allCookies().count, 0)
        
        // 导入
        cookieStore.importCookies(from: exported)
        XCTAssertEqual(cookieStore.allCookies().count, 1)
        
        let cookie = cookieStore.cookie(named: "test")
        XCTAssertEqual(cookie?.value, "data")
    }
}
