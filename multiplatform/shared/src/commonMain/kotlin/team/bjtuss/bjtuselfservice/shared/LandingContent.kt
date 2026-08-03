package team.bjtuss.bjtuselfservice.shared

enum class WindowClass(val label: String) {
    Compact("紧凑窗口"),
    Medium("中等窗口"),
    Expanded("宽窗口"),
}

data class PlatformStatus(
    val name: String,
    val state: String,
    val description: String,
    val isCurrent: Boolean,
)

data class LandingContent(
    val platformName: String,
    val windowClass: WindowClass,
    val statuses: List<PlatformStatus>,
)

fun windowClassFor(widthDp: Int): WindowClass = when {
    widthDp < 600 -> WindowClass.Compact
    widthDp < 900 -> WindowClass.Medium
    else -> WindowClass.Expanded
}

fun landingContent(
    platform: PlatformInfo,
    widthDp: Int,
): LandingContent {
    val states = PlatformFamily.entries.map { family ->
        PlatformStatus(
            name = when (family) {
                PlatformFamily.Android -> "Android"
                PlatformFamily.IOS -> "iOS"
                PlatformFamily.MacOS -> "macOS"
            },
            state = if (family == PlatformFamily.Android) "基线保留" else "骨架已连接",
            description = when (family) {
                PlatformFamily.Android -> "官方 v1.7.0 与新调试包并行存在"
                PlatformFamily.IOS -> "SwiftUI 宿主加载共享 Compose 页面"
                PlatformFamily.MacOS -> "桌面窗口复用共享状态与组件"
            },
            isCurrent = family == platform.family,
        )
    }

    return LandingContent(
        platformName = platform.displayName,
        windowClass = windowClassFor(widthDp),
        statuses = states,
    )
}
