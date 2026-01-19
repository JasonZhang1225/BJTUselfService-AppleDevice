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
            errorMessage = result.message
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
}

private extension String {
    var nonEmpty: String? { isEmpty ? nil : self }
}
