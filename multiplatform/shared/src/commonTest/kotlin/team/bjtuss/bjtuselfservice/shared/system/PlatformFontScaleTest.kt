package team.bjtuss.bjtuselfservice.shared.system

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlatformFontScaleTest {
    @Test
    fun appleCategoriesIncreaseMonotonically() {
        val scales = AppleContentSizeCategory.entries.map {
            appleFontScaleFor(it, defaultFontScale = 1f)
        }

        assertEquals(0.82f, scales.first())
        assertEquals(3.12f, scales.last())
        assertTrue(scales.zipWithNext().all { (current, next) -> next > current })
    }

    @Test
    fun unknownAppleCategoryPreservesComposeDefault() {
        assertEquals(1.37f, appleFontScaleFor(category = null, defaultFontScale = 1.37f))
    }
}
