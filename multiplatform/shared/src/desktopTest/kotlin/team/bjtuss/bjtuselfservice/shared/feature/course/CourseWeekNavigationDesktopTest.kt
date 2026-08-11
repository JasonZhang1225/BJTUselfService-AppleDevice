package team.bjtuss.bjtuselfservice.shared.feature.course

import kotlin.test.Test
import kotlin.test.assertEquals

class CourseWeekNavigationDesktopTest {
    @Test
    fun appKitContentDeltaUsesOppositePageDirection() {
        assertEquals(CourseWeekScrollDirection.PREVIOUS, nativeTrackpadDirection(1))
        assertEquals(CourseWeekScrollDirection.NEXT, nativeTrackpadDirection(-1))
    }
}
