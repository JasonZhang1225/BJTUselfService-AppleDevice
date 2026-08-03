package team.bjtuss.bjtuselfservice.shared

import team.bjtuss.bjtuselfservice.shared.data.homework.SmartPlatformEndpoint

/**
 * 智慧教学平台的跨平台传输策略。
 *
 * 2026-08-01 用户明确授权 iOS 与 macOS 使用固定旧 HTTP 端点；Android 继续保持
 * HTTPS 基线。策略集中在这里，避免网络注入与常驻风险提示出现平台分叉。
 */
internal fun smartPlatformEndpointFor(family: PlatformFamily): SmartPlatformEndpoint = when (family) {
    PlatformFamily.IOS, PlatformFamily.MacOS -> SmartPlatformEndpoint.AppleLegacyHttp
    PlatformFamily.Android -> SmartPlatformEndpoint.VerifiedHttps
}

internal fun usesLegacySmartTransportFor(family: PlatformFamily): Boolean =
    smartPlatformEndpointFor(family).isLegacyInsecure
