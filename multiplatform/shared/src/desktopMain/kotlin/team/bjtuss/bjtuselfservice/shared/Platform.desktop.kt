package team.bjtuss.bjtuselfservice.shared

actual fun currentPlatform(): PlatformInfo = PlatformInfo(
    family = PlatformFamily.MacOS,
    displayName = "macOS ${System.getProperty("os.version")}",
)
