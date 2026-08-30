package team.bjtuss.bjtuselfservice.shared

actual fun currentPlatform(): PlatformInfo {
    val isWindows = System.getProperty("os.name").lowercase().contains("win")
    return PlatformInfo(
        family = PlatformFamily.MacOS,
        displayName = if (isWindows) {
            "Windows ${System.getProperty("os.version")}"
        } else {
            "macOS ${System.getProperty("os.version")}"
        },
        isWindows = isWindows,
    )
}
