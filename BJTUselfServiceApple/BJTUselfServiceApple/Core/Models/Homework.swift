//
//  Homework.swift
//  BJTUselfServiceApple
//
//  Created on 2026/1/19.
//

import Foundation

/// 作业信息
struct Homework: Codable, Identifiable {
    let id: String
    let title: String
    let courseName: String
    let dueDate: Date
    let status: HomeworkStatus
    let description: String?
    let submittedDate: Date?
    
    init(id: String = UUID().uuidString,
         title: String = "",
         courseName: String = "",
         dueDate: Date = Date(),
         status: HomeworkStatus = .pending,
         description: String? = nil,
         submittedDate: Date? = nil) {
        self.id = id
        self.title = title
        self.courseName = courseName
        self.dueDate = dueDate
        self.status = status
        self.description = description
        self.submittedDate = submittedDate
    }
}

enum HomeworkStatus: String, Codable {
    case pending = "待提交"
    case submitted = "已提交"
    case graded = "已批改"
    case overdue = "已逾期"
}
