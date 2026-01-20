//
//  SettingsView.swift
//  BJTUselfServiceApple
//
//  Created on 2026/1/19.
//

import SwiftUI

struct SettingsView: View {
    @EnvironmentObject private var authViewModel: AuthViewModel
    @ObservedObject private var authService = AuthService.shared
    @State private var showLogoutAlert = false
    
    var body: some View {
        NavigationStack {
            List {
                // 账号信息
                Section("账号信息") {
                    if let student = authService.currentStudent {
                        HStack {
                            Text("姓名")
                            Spacer()
                            if student.name.isEmpty || authService.needsUserInfoRefresh {
                                VStack(alignment: .trailing) {
                                    Text("学号: \(student.studentId)（未获取姓名）")
                                        .foregroundStyle(.secondary)
                                        .font(.caption)
                                    if authViewModel.isRefreshingUserInfo {
                                        ProgressView()
                                            .scaleEffect(0.6)
                                    } else {
                                        Button("刷新用户信息") {
                                            Task { await authViewModel.refreshUserInfo() }
                                        }
                                        .buttonStyle(.bordered)
                                        .controlSize(.small)
                                    }
                                }
                            } else {
                                Text(student.name)
                                    .foregroundStyle(.secondary)
                            }
                        }
                        
                        HStack {
                            Text("学号")
                            Spacer()
                            Text(student.studentId)
                                .foregroundStyle(.secondary)
                        }
                    }
                }
                
                // 通知设置
                Section("通知设置") {
                    Toggle("作业提醒", isOn: .constant(true))
                    Toggle("考试提醒", isOn: .constant(true))
                    Toggle("成绩发布提醒", isOn: .constant(false))
                }
                
                // 数据管理
                Section("数据管理") {
                    Button("清除缓存") {
                        // TODO: 清除缓存
                    }
                    
                    Button("同步数据") {
                        // TODO: 同步数据
                    }
                }
                
                // 关于
                Section("关于") {
                    HStack {
                        Text("版本")
                        Spacer()
                        Text("1.0.0")
                            .foregroundStyle(.secondary)
                    }
                    
                    Link("GitHub", destination: URL(string: "https://github.com")!)
                    
                    Button("用户协议") {
                        // TODO: 显示用户协议
                    }
                }
                
                // 登出
                Section {
                    Button(role: .destructive) {
                        showLogoutAlert = true
                    } label: {
                        HStack {
                            Spacer()
                            Text("退出登录")
                            Spacer()
                        }
                    }
                }
            }
            .navigationTitle("设置")
            .alert("确认退出", isPresented: $showLogoutAlert) {
                Button("取消", role: .cancel) {}
                Button("退出", role: .destructive) {
                    authViewModel.logout()
                }
            } message: {
                Text("退出登录后需要重新输入账号密码")
            }
        }
    }
}

#Preview {
    SettingsView()
        .environmentObject(AuthViewModel())
}
