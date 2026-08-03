package team.bjtuss.bjtuselfservice.shared

import androidx.compose.animation.core.SnapSpec
import androidx.compose.animation.core.TweenSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AccessibilityPresentationTest {
    @Test
    fun reducedTransparencyMakesLayersOpaque() {
        assertEquals(1f, accessibleAlphaFor(normalAlpha = 0.42f, reduceTransparency = true))
        assertEquals(0.42f, accessibleAlphaFor(normalAlpha = 0.42f, reduceTransparency = false))
    }

    @Test
    fun reducedMotionRemovesSpatialTravel() {
        assertIs<SnapSpec<Float>>(ReducedMotionScheme.defaultSpatialSpec<Float>())
        assertIs<SnapSpec<Float>>(ReducedMotionScheme.fastSpatialSpec<Float>())
        assertIs<SnapSpec<Float>>(ReducedMotionScheme.slowSpatialSpec<Float>())
    }

    @Test
    fun reducedMotionKeepsOnlyShortEffectsFeedback() {
        val default = assertIs<TweenSpec<Float>>(ReducedMotionScheme.defaultEffectsSpec<Float>())
        val fast = assertIs<TweenSpec<Float>>(ReducedMotionScheme.fastEffectsSpec<Float>())
        val slow = assertIs<TweenSpec<Float>>(ReducedMotionScheme.slowEffectsSpec<Float>())

        assertEquals(150, default.durationMillis)
        assertEquals(120, fast.durationMillis)
        assertEquals(180, slow.durationMillis)
    }
}
