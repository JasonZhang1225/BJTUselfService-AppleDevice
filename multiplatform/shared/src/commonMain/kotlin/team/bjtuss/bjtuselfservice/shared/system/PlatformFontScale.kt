package team.bjtuss.bjtuselfservice.shared.system

import androidx.compose.runtime.Composable

/**
 * Returns the effective Compose font scale for the current platform.
 *
 * Android and Desktop already expose their scale through [defaultFontScale]. iOS supplies its
 * preferred content-size category through the platform actual so changes made while the app is
 * running can recompose the UI without relaunching it.
 */
@Composable
expect fun rememberPlatformFontScale(defaultFontScale: Float): Float

/** Whether the platform currently requests stronger color contrast. */
@Composable
expect fun rememberPlatformIncreasedContrast(): Boolean

/** Whether the platform requests non-spatial or static transitions. */
@Composable
expect fun rememberPlatformReduceMotion(): Boolean

/** Whether translucent UI should become opaque. */
@Composable
expect fun rememberPlatformReduceTransparency(): Boolean

internal enum class AppleContentSizeCategory {
    ExtraSmall,
    Small,
    Medium,
    Large,
    ExtraLarge,
    ExtraExtraLarge,
    ExtraExtraExtraLarge,
    AccessibilityMedium,
    AccessibilityLarge,
    AccessibilityExtraLarge,
    AccessibilityExtraExtraLarge,
    AccessibilityExtraExtraExtraLarge,
}

internal fun appleFontScaleFor(
    category: AppleContentSizeCategory?,
    defaultFontScale: Float,
): Float = when (category) {
    AppleContentSizeCategory.ExtraSmall -> 0.82f
    AppleContentSizeCategory.Small -> 0.88f
    AppleContentSizeCategory.Medium -> 0.94f
    AppleContentSizeCategory.Large -> 1.00f
    AppleContentSizeCategory.ExtraLarge -> 1.12f
    AppleContentSizeCategory.ExtraExtraLarge -> 1.23f
    AppleContentSizeCategory.ExtraExtraExtraLarge -> 1.35f
    AppleContentSizeCategory.AccessibilityMedium -> 1.64f
    AppleContentSizeCategory.AccessibilityLarge -> 1.95f
    AppleContentSizeCategory.AccessibilityExtraLarge -> 2.35f
    AppleContentSizeCategory.AccessibilityExtraExtraLarge -> 2.76f
    AppleContentSizeCategory.AccessibilityExtraExtraExtraLarge -> 3.12f
    null -> defaultFontScale
}
