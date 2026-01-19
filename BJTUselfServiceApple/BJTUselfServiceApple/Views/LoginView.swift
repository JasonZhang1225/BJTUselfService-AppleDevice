//
//  LoginView.swift
//  BJTUselfServiceApple
//
//  Created on 2026/1/19.
//

import SwiftUI

struct LoginView: View {
    @EnvironmentObject private var authViewModel: AuthViewModel
    
    var body: some View {
        NavigationStack {
            VStack(spacing: 25) {
                // Logo区域
                VStack(spacing: 10) {
                    Image(systemName: "graduationcap.fill")
                        .resizable()
                        .scaledToFit()
                        .frame(width: 80, height: 80)
                        .foregroundStyle(.blue)
                    
                    Text("交大自由行NEO")
                        .font(.title)
                        .fontWeight(.bold)
                    
                    Text("BJTU Self Service")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                .padding(.top, 50)
                
                // 登录表单
                VStack(spacing: 15) {
                    // 学号输入
                    HStack {
                        Image(systemName: "person.fill")
                            .foregroundStyle(.gray)
                            .frame(width: 30)
                        TextField("学号", text: $authViewModel.username)
                            .textContentType(.username)
                            #if os(iOS)
                            .autocapitalization(.none)
                            .keyboardType(.numberPad)
                            #endif
                    }
                    .padding()
                    #if os(iOS)
                    .background(Color(uiColor: .systemGray6))
                    #else
                    .background(Color(nsColor: .controlBackgroundColor))
                    #endif
                    .cornerRadius(10)
                    
                    // 密码输入
                    HStack {
                        Image(systemName: "lock.fill")
                            .foregroundStyle(.gray)
                            .frame(width: 30)
                        SecureField("密码", text: $authViewModel.password)
                            .textContentType(.password)
                    }
                    .padding()
                    #if os(iOS)
                    .background(Color(uiColor: .systemGray6))
                    #else
                    .background(Color(nsColor: .controlBackgroundColor))
                    #endif
                    .cornerRadius(10)
                    
                    // 验证码输入 + 图片
                    HStack(spacing: 10) {
                        Image(systemName: "checkmark.shield.fill")
                            .foregroundStyle(.gray)
                            .frame(width: 30)
                        TextField("验证码", text: $authViewModel.captcha)
                            .textContentType(.oneTimeCode)
                            #if os(iOS)
                            .autocapitalization(.none)
                            #endif
                        
                        ZStack {
                            RoundedRectangle(cornerRadius: 5)
                                #if os(iOS)
                                .fill(Color(uiColor: .systemGray5))
                                #else
                                .fill(Color(nsColor: .controlColor))
                                #endif
                                .frame(width: 100, height: 40)
                            if authViewModel.isFetchingCaptcha {
                                ProgressView()
                            } else if let data = authViewModel.captchaImageData, let uiImage = UIImage(data: data) {
                                Image(uiImage: uiImage)
                                    .resizable()
                                    .scaledToFit()
                                    .frame(width: 100, height: 40)
                            } else {
                                Text("验证码")
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                        }
                        .onTapGesture {
                            Task { await authViewModel.loadCaptcha() }
                        }
                    }
                    .padding()
                    #if os(iOS)
                    .background(Color(uiColor: .systemGray6))
                    #else
                    .background(Color(nsColor: .controlBackgroundColor))
                    #endif
                    .cornerRadius(10)
                }
                .padding(.horizontal, 30)
                
                // 错误提示
                if let errorMessage = authViewModel.errorMessage {
                    Text(errorMessage)
                        .font(.caption)
                        .foregroundStyle(.red)
                        .padding(.horizontal, 30)
                }
                
                // 登录按钮
                Button(action: {
                    Task {
                        await authViewModel.login()
                    }
                }) {
                    if authViewModel.isLoading {
                        ProgressView()
                            .progressViewStyle(CircularProgressViewStyle(tint: .white))
                            .frame(maxWidth: .infinity)
                            .padding()
                    } else {
                        Text("登录")
                            .fontWeight(.semibold)
                            .frame(maxWidth: .infinity)
                            .padding()
                    }
                }
                .background(Color.blue)
                .foregroundStyle(.white)
                .cornerRadius(10)
                .padding(.horizontal, 30)
                .disabled(authViewModel.isLoading)
                
                Spacer()
                
                // 底部提示
                Text("首次登录请使用MIS系统账号密码")
                    .font(.caption2)
                    .foregroundStyle(.secondary)
                    .padding(.bottom, 20)
            }
            .navigationTitle("登录")
            #if os(iOS)
            .navigationBarTitleDisplayMode(.inline)
            #endif
            .task {
                await authViewModel.loadCaptcha()
            }
        }
    }
}

#Preview {
    LoginView()
        .environmentObject(AuthViewModel())
}
