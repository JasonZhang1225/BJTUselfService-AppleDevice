//
//  LoginModels.swift
//  BJTUselfServiceApple
//
//  Created on 2026/1/19.
//

import Foundation

struct LoginResult: Error, LocalizedError {
    let success: Bool
    let message: String

    // Expose message as localizedDescription for Error compliance
    var errorDescription: String? { message }
}

struct CaptchaChallenge {
    let captchaId: String
    let csrfToken: String
    let nextPath: String
    let casLoginURL: URL
    let captchaImageURL: URL
}
