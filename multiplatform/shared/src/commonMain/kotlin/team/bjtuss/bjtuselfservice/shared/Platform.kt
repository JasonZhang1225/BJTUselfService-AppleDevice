package team.bjtuss.bjtuselfservice.shared

enum class PlatformFamily {
    Android,
    IOS,
    MacOS,
}

data class PlatformInfo(
    val family: PlatformFamily,
    val displayName: String,
)

expect fun currentPlatform(): PlatformInfo
