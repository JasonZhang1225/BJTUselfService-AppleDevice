package team.bjtuss.bjtuselfservice.shared

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

internal val LocalReduceTransparency = staticCompositionLocalOf { false }

@Composable
internal fun Color.accessibleAlpha(normalAlpha: Float): Color =
    copy(alpha = accessibleAlphaFor(normalAlpha, LocalReduceTransparency.current))

internal fun accessibleAlphaFor(normalAlpha: Float, reduceTransparency: Boolean): Float =
    if (reduceTransparency) 1f else normalAlpha

/**
 * Reduced Motion keeps short non-spatial fades as feedback while removing sheet/dialog travel.
 */
internal object ReducedMotionScheme : MotionScheme {
    override fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> = snap()

    override fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> = tween(durationMillis = 150)
    override fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> = tween(durationMillis = 120)
    override fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> = tween(durationMillis = 180)
}
