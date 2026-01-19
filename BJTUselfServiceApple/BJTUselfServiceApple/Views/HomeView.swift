//
//  HomeView.swift
//  BJTUselfServiceApple
//
//  Created on 2026/1/19.
//

import SwiftUI

struct HomeView: View {
    @StateObject private var viewModel = HomeViewModel()
    
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 20) {
                    // 用户信息卡片
                    if let student = viewModel.studentInfo {
                        UserInfoCard(student: student)
                    }
                    
                    // 余额卡片
                    BalanceCards(
                        campusCardBalance: viewModel.campusCardBalance,
                        networkBalance: viewModel.networkBalance
                    )
                    
                    // 快捷功能
                    QuickActionsGrid()
                    
                    // 待办事项
                    TodoSection(unreadEmailCount: viewModel.unreadEmailCount)
                }
                .padding()
            }
            .navigationTitle("首页")
            .refreshable {
                await viewModel.refresh()
            }
            .task {
                await viewModel.refresh()
            }
        }
    }
}

// MARK: - 用户信息卡片
struct UserInfoCard: View {
    let student: StudentInfo
    
    var body: some View {
        HStack {
            Image(systemName: "person.circle.fill")
                .resizable()
                .frame(width: 50, height: 50)
                .foregroundStyle(.blue)
            
            VStack(alignment: .leading, spacing: 5) {
                Text(student.name)
                    .font(.headline)
                Text("学号: \(student.studentId)")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            
            Spacer()
        }
        .padding()
        #if os(iOS)
        .background(Color(uiColor: .systemBackground))
        #else
        .background(Color(nsColor: .windowBackgroundColor))
        #endif
        .cornerRadius(12)
        .shadow(color: .black.opacity(0.05), radius: 5)
    }
}

// MARK: - 余额卡片
struct BalanceCards: View {
    let campusCardBalance: String
    let networkBalance: String
    
    var body: some View {
        HStack(spacing: 15) {
            BalanceCard(
                icon: "creditcard.fill",
                title: "校园卡",
                balance: campusCardBalance,
                color: .blue
            )
            
            BalanceCard(
                icon: "wifi",
                title: "校园网",
                balance: networkBalance,
                color: .green
            )
        }
    }
}

struct BalanceCard: View {
    let icon: String
    let title: String
    let balance: String
    let color: Color
    
    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Image(systemName: icon)
                    .foregroundStyle(color)
                Spacer()
            }
            
            Text(title)
                .font(.caption)
                .foregroundStyle(.secondary)
            
            Text("¥\(balance)")
                .font(.title2)
                .fontWeight(.bold)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding()
        #if os(iOS)
        .background(Color(uiColor: .systemBackground))
        #else
        .background(Color(nsColor: .windowBackgroundColor))
        #endif
        .cornerRadius(12)
        .shadow(color: .black.opacity(0.05), radius: 5)
    }
}

// MARK: - 快捷功能网格
struct QuickActionsGrid: View {
    let actions = [
        ("成绩查询", "chart.bar.fill", Color.blue),
        ("课程表", "calendar", Color.green),
        ("考试安排", "doc.text.fill", Color.orange),
        ("作业", "list.bullet.clipboard.fill", Color.purple)
    ]
    
    var body: some View {
        VStack(alignment: .leading, spacing: 15) {
            Text("快捷功能")
                .font(.headline)
            
            LazyVGrid(columns: [
                GridItem(.flexible()),
                GridItem(.flexible()),
                GridItem(.flexible()),
                GridItem(.flexible())
            ], spacing: 15) {
                ForEach(actions, id: \.0) { action in
                    QuickActionButton(
                        title: action.0,
                        icon: action.1,
                        color: action.2
                    )
                }
            }
        }
    }
}

struct QuickActionButton: View {
    let title: String
    let icon: String
    let color: Color
    
    var body: some View {
        Button(action: {
            // TODO: 导航到对应页面
        }) {
            VStack(spacing: 8) {
                Image(systemName: icon)
                    .font(.title2)
                    .foregroundStyle(color)
                
                Text(title)
                    .font(.caption2)
                    .foregroundStyle(.primary)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 15)
            #if os(iOS)
            .background(Color(uiColor: .systemBackground))
            #else
            .background(Color(nsColor: .windowBackgroundColor))
            #endif
            .cornerRadius(10)
            .shadow(color: .black.opacity(0.05), radius: 3)
        }
    }
}

// MARK: - 待办事项
struct TodoSection: View {
    let unreadEmailCount: Int
    
    var body: some View {
        VStack(alignment: .leading, spacing: 15) {
            Text("待办事项")
                .font(.headline)
            
            if unreadEmailCount > 0 {
                HStack {
                    Image(systemName: "envelope.fill")
                        .foregroundStyle(.blue)
                    Text("未读邮件")
                    Spacer()
                    Text("\(unreadEmailCount)")
                        .fontWeight(.bold)
                        .foregroundStyle(.blue)
                    Image(systemName: "chevron.right")
                        .foregroundStyle(.secondary)
                        .font(.caption)
                }
                .padding()
                #if os(iOS)
                .background(Color(uiColor: .systemBackground))
                #else
                .background(Color(nsColor: .windowBackgroundColor))
                #endif
                .cornerRadius(10)
                .shadow(color: .black.opacity(0.05), radius: 3)
            } else {
                Text("暂无待办事项")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .frame(maxWidth: .infinity)
                    .padding()
            }
        }
    }
}

#Preview {
    HomeView()
}
