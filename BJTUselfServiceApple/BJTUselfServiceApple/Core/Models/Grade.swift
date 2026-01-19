//
//  Grade.swift
//  BJTUselfServiceApple
//
//  Created on 2026/1/19.
//

import Foundation

/// 成绩信息
struct Grade: Codable, Identifiable {
    let id: String
    let courseName: String
    let courseCode: String
    let credit: Double
    let score: String
    let gradePoint: Double?
    let semester: String
    let courseType: String?
    
    init(id: String = UUID().uuidString,
         courseName: String = "",
         courseCode: String = "",
         credit: Double = 0.0,
         score: String = "",
         gradePoint: Double? = nil,
         semester: String = "",
         courseType: String? = nil) {
        self.id = id
        self.courseName = courseName
        self.courseCode = courseCode
        self.credit = credit
        self.score = score
        self.gradePoint = gradePoint
        self.semester = semester
        self.courseType = courseType
    }
}
