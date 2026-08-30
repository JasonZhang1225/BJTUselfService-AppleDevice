package team.bjtuss.bjtuselfservice.shared.feature.scroll

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import team.bjtuss.bjtuselfservice.shared.PlatformFamily
import team.bjtuss.bjtuselfservice.shared.PlatformInfo

class DesktopTouchScrollTest {
    @Test
    fun onlyWindowsCanEnableTheDesktopTouchLayer() {
        assertTrue(
            shouldEnableDesktopTouchScroll(
                platform = PlatformInfo(PlatformFamily.MacOS, "Windows", isWindows = true),
                requested = true,
            ),
        )
        assertFalse(
            shouldEnableDesktopTouchScroll(
                platform = PlatformInfo(PlatformFamily.MacOS, "macOS"),
                requested = true,
            ),
        )
        assertFalse(
            shouldEnableDesktopTouchScroll(
                platform = PlatformInfo(PlatformFamily.Android, "Android"),
                requested = true,
            ),
        )
        assertFalse(
            shouldEnableDesktopTouchScroll(
                platform = PlatformInfo(PlatformFamily.MacOS, "Windows", isWindows = true),
                requested = false,
            ),
        )
    }
}
