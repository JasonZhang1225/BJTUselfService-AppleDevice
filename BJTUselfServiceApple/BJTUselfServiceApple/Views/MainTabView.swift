//
//  MainTabView.swift
//  BJTUselfServiceApple
//
//  Created on 2026/1/19.
//

import SwiftUI

struct MainTabView: View {
    @EnvironmentObject private var authViewModel: AuthViewModel
    
    var body: some View {
        TabView {
            HomeView()
                .tabItem {
                    Label("首页", systemImage: "house.fill")
                }
            
            // 功能页面占位
            NavigationStack {
                Text("成绩查询")
                    .navigationTitle("成绩")
            }
            .tabItem {
                Label("成绩", systemImage: "chart.bar.fill")
            }
            
            NavigationStack {
                Text("课程表")
                    .navigationTitle("课程")
            }
            .tabItem {
                Label("课程", systemImage: "calendar")
            }
            
            SettingsView()
                .tabItem {
                    Label("设置", systemImage: "gearshape.fill")
                }
        }
    }
}

#Preview {
    MainTabView()
        .environmentObject(AuthViewModel())
}
