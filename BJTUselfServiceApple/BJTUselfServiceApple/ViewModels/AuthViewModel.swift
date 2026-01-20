//
//  AuthViewModel.swift
//  BJTUselfServiceApple
//
//  Created on 2026/1/19.
//

import Foundation
import SwiftUI
import Combine

/// 认证视图模型
@MainActor
class AuthViewModel: ObservableObject {
    @Published var username: String = ""
    @Published var password: String = ""
    @Published var captcha: String = ""
    @Published var captchaImageData: Data?
    @Published var isFetchingCaptcha: Bool = false
    @Published var isLoading: Bool = false
    @Published var errorMessage: String?
    @Published var isAuthenticated: Bool = false
    @Published var isRefreshingUserInfo: Bool = false
    
    private let authService = AuthService.shared
    
    func loadCaptcha() async {
        isFetchingCaptcha = true
        defer { isFetchingCaptcha = false }
        let result = await authService.fetchCaptchaForDisplay()
        switch result {
        case .success((_, let data)):
            await MainActor.run {
                self.captchaImageData = data
                self.errorMessage = nil
            }
        case .failure(let loginResult):
            await MainActor.run {
                self.captchaImageData = nil
                self.errorMessage = loginResult.message
            }
        }
    }
    
    /// 登录
    func login() async {
        guard !username.isEmpty, !password.isEmpty else {
            errorMessage = "请输入学号和密码"
            return
        }
        
        isLoading = true
        errorMessage = nil
        
        let captchaInput = captcha.nonEmpty
        let result = await authService.login(
            username: username,
            password: password,
            captchaText: captchaInput
        )
        
        if result.success {
            isAuthenticated = true
        } else {
            // 保存原始登录失败信息以便在自动刷新验证码时不会被覆盖
            let loginFailureMsg = result.message
            errorMessage = loginFailureMsg

            // 清空当前输入的验证码并自动拉取新验证码以避免复用或过期问题
            captcha = ""
            // 静默刷新验证码：如果拉取失败，不覆盖原始的登录错误信息
            await loadCaptcha()
            if let currentErr = errorMessage, currentErr != loginFailureMsg {
                errorMessage = loginFailureMsg
            }
        }
        
        isLoading = false
    }
    
    /// 登出
    func logout() {
        authService.logout()
        isAuthenticated = false
        username = ""
        password = ""
        captcha = ""
        captchaImageData = nil
    }
    
    /// 检查登录状态
    func checkAuthStatus() async {
        isAuthenticated = await authService.checkAuthStatus()
    }

    /// 刷新用户信息（用于当会话存在但未解析出姓名时的手动刷新）
    func refreshUserInfo() async {
        isRefreshingUserInfo = true
        defer { isRefreshingUserInfo = false }
        let ok = await authService.refreshStudentInfo()
        if !ok {
            errorMessage = "刷新用户信息失败，请稍后重试"
        } else {
            errorMessage = nil
        }
    }
}

private extension String {
    var nonEmpty: String? { isEmpty ? nil : self }
}
