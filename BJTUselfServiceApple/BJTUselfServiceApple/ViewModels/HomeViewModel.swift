//
//  HomeViewModel.swift
//  BJTUselfServiceApple
//
//  Created on 2026/1/19.
//

import Foundation
import SwiftUI
import Combine

/// 首页视图模型
@MainActor
class HomeViewModel: ObservableObject {
    @Published var studentInfo: StudentInfo?
    @Published var campusCardBalance: String = "0.00"
    @Published var networkBalance: String = "0.00"
    @Published var unreadEmailCount: Int = 0
    @Published var isRefreshing: Bool = false
    
    private let authService = AuthService.shared
    
    init() {
        self.studentInfo = authService.currentStudent
    }
    
    /// 刷新首页数据
    func refresh() async {
        isRefreshing = true
        
        // TODO: 实现数据刷新逻辑
        // 1. 获取校园卡余额
        // 2. 获取校园网余额
        // 3. 获取未读邮件数
        
        // 模拟网络请求
        try? await Task.sleep(nanoseconds: 1_000_000_000)
        
        // 模拟数据
        campusCardBalance = "128.50"
        networkBalance = "45.20"
        unreadEmailCount = 3
        
        isRefreshing = false
    }
    
    /// 获取校园卡余额
    func fetchCampusCardBalance() async {
        // TODO: 实现获取校园卡余额
    }
    
    /// 获取校园网余额
    func fetchNetworkBalance() async {
        // TODO: 实现获取校园网余额
    }
    
    /// 获取未读邮件数
    func fetchUnreadEmailCount() async {
        // TODO: 实现获取未读邮件数
    }
}
