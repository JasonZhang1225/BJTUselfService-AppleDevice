package team.bjtuss.bjtuselfservice.shared

enum class PlatformFamily {
    Android,
    IOS,
    MacOS,
}

data class PlatformInfo(
    val family: PlatformFamily,
    val displayName: String,
    /** Desktop target identity kept separate because Windows currently reuses the MacOS family layout. */
    val isWindows: Boolean = false,
)

expect fun currentPlatform(): PlatformInfo
