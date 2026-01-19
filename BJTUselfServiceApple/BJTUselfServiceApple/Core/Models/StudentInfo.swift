//
//  StudentInfo.swift
//  BJTUselfServiceApple
//
//  Created on 2026/1/19.
//

import Foundation

/// 学生基本信息
struct StudentInfo: Codable, Identifiable {
    let id: String
    let name: String
    let studentId: String
    let major: String?
    let college: String?
    
    init(id: String = UUID().uuidString,
         name: String = "",
         studentId: String = "",
         major: String? = nil,
         college: String? = nil) {
        self.id = id
        self.name = name
        self.studentId = studentId
        self.major = major
        self.college = college
    }
}
