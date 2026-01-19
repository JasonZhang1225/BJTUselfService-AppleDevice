//
//  Item.swift
//  BJTUselfServiceApple
//
//  Created by 张竞戈 on 2026/1/19.
//

import Foundation
import SwiftData

@Model
final class Item {
    var timestamp: Date
    
    init(timestamp: Date) {
        self.timestamp = timestamp
    }
}
