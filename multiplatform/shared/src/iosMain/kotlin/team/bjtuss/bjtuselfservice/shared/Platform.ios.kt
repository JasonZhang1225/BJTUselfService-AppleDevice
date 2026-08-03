package team.bjtuss.bjtuselfservice.shared

import platform.UIKit.UIDevice

actual fun currentPlatform(): PlatformInfo {
    val device = UIDevice.currentDevice
    return PlatformInfo(
        family = PlatformFamily.IOS,
        displayName = "${device.systemName} ${device.systemVersion}",
    )
}
