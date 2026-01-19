//
//  Course.swift
//  BJTUselfServiceApple
//
//  Created on 2026/1/19.
//

import Foundation

/// 课程信息
struct Course: Codable, Identifiable {
    let id: String
    let courseName: String
    let courseCode: String
    let teacher: String
    let classroom: String
    let weekday: Int  // 1-7 代表周一到周日
    let startWeek: Int
    let endWeek: Int
    let startTime: Int  // 第几节课开始
    let endTime: Int    // 第几节课结束
    
    init(id: String = UUID().uuidString,
         courseName: String = "",
         courseCode: String = "",
         teacher: String = "",
         classroom: String = "",
         weekday: Int = 1,
         startWeek: Int = 1,
         endWeek: Int = 18,
         startTime: Int = 1,
         endTime: Int = 2) {
        self.id = id
        self.courseName = courseName
        self.courseCode = courseCode
        self.teacher = teacher
        self.classroom = classroom
        self.weekday = weekday
        self.startWeek = startWeek
        self.endWeek = endWeek
        self.startTime = startTime
        self.endTime = endTime
    }
}
