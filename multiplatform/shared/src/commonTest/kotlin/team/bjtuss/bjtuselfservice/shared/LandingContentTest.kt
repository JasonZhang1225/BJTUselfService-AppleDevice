package team.bjtuss.bjtuselfservice.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LandingContentTest {
    @Test
    fun classifiesWindowWidthsAtBoundaries() {
        assertEquals(WindowClass.Compact, windowClassFor(599))
        assertEquals(WindowClass.Medium, windowClassFor(600))
        assertEquals(WindowClass.Medium, windowClassFor(899))
        assertEquals(WindowClass.Expanded, windowClassFor(900))
    }

    @Test
    fun accessibilityFontScaleUsesScrollableSingleColumnOnWideWindows() {
        assertEquals(WindowClass.Expanded, adaptiveWindowClassFor(widthDp = 1180, fontScale = 1.49f))
        assertEquals(WindowClass.Medium, adaptiveWindowClassFor(widthDp = 1180, fontScale = 1.50f))
        assertEquals(WindowClass.Medium, adaptiveWindowClassFor(widthDp = 820, fontScale = 3.12f))
    }

    @Test
    fun marksExactlyOneCurrentPlatform() {
        val content = landingContent(
            platform = PlatformInfo(PlatformFamily.IOS, "iOS Simulator"),
            widthDp = 430,
        )

        assertEquals("iOS Simulator", content.platformName)
        assertEquals(3, content.statuses.size)
        assertEquals(1, content.statuses.count { it.isCurrent })
        assertTrue(content.statuses.single { it.isCurrent }.name == "iOS")
    }
}
