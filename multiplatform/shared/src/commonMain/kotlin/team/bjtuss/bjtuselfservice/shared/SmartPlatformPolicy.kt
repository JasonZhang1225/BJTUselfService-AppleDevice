package team.bjtuss.bjtuselfservice.shared

import team.bjtuss.bjtuselfservice.shared.data.homework.SmartPlatformEndpoint

/**
 * 智慧教学平台的跨平台传输策略。
 *
 * 2026-08-01 用户明确授权 iOS 与 macOS 使用固定旧 HTTP 端点；2026-08-04 用户进一步
 * 授权 Android 使用同一旧 HTTP 端点，三端行为一致。策略集中在这里，避免网络注入
 * 与常驻风险提示出现平台分叉。Android 端明文流量由 networkSecurityConfig 限定到
 * 该固定 IP，不全局放开。
 */
internal fun smartPlatformEndpointFor(family: PlatformFamily): SmartPlatformEndpoint = when (family) {
    PlatformFamily.Android, PlatformFamily.IOS, PlatformFamily.MacOS -> SmartPlatformEndpoint.LegacyHttp
}

internal fun usesLegacySmartTransportFor(family: PlatformFamily): Boolean =
    smartPlatformEndpointFor(family).isLegacyInsecure
