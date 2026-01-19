//
//  ExamSchedule.swift
//  BJTUselfServiceApple
//
//  Created on 2026/1/19.
//

import Foundation

/// 考试日程
struct ExamSchedule: Codable, Identifiable {
    let id: String
    let courseName: String
    let courseCode: String
    let examTime: Date
    let examLocation: String
    let seatNumber: String?
    let examType: String  // 期末考试、期中考试等
    
    init(id: String = UUID().uuidString,
         courseName: String = "",
         courseCode: String = "",
         examTime: Date = Date(),
         examLocation: String = "",
         seatNumber: String? = nil,
         examType: String = "") {
        self.id = id
        self.courseName = courseName
        self.courseCode = courseCode
        self.examTime = examTime
        self.examLocation = examLocation
        self.seatNumber = seatNumber
        self.examType = examType
    }
}
