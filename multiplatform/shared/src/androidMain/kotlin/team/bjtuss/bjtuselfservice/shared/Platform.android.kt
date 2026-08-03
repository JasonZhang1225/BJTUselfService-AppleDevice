package team.bjtuss.bjtuselfservice.shared

import android.os.Build

actual fun currentPlatform(): PlatformInfo = PlatformInfo(
    family = PlatformFamily.Android,
    displayName = "Android ${Build.VERSION.RELEASE}",
)
