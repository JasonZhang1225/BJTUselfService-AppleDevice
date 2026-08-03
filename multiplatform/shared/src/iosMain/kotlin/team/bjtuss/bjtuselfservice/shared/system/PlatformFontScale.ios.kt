package team.bjtuss.bjtuselfservice.shared.system

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplication
import platform.UIKit.UIAccessibilityDarkerSystemColorsEnabled
import platform.UIKit.UIAccessibilityDarkerSystemColorsStatusDidChangeNotification
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled
import platform.UIKit.UIAccessibilityIsReduceTransparencyEnabled
import platform.UIKit.UIAccessibilityReduceMotionStatusDidChangeNotification
import platform.UIKit.UIAccessibilityReduceTransparencyStatusDidChangeNotification
import platform.UIKit.UIContentSizeCategoryAccessibilityExtraExtraExtraLarge
import platform.UIKit.UIContentSizeCategoryAccessibilityExtraExtraLarge
import platform.UIKit.UIContentSizeCategoryAccessibilityExtraLarge
import platform.UIKit.UIContentSizeCategoryAccessibilityLarge
import platform.UIKit.UIContentSizeCategoryAccessibilityMedium
import platform.UIKit.UIContentSizeCategoryDidChangeNotification
import platform.UIKit.UIContentSizeCategoryExtraExtraExtraLarge
import platform.UIKit.UIContentSizeCategoryExtraExtraLarge
import platform.UIKit.UIContentSizeCategoryExtraLarge
import platform.UIKit.UIContentSizeCategoryExtraSmall
import platform.UIKit.UIContentSizeCategoryLarge
import platform.UIKit.UIContentSizeCategoryMedium
import platform.UIKit.UIContentSizeCategorySmall

@Composable
@OptIn(ExperimentalForeignApi::class)
actual fun rememberPlatformFontScale(defaultFontScale: Float): Float {
    var category by remember {
        mutableStateOf(UIApplication.sharedApplication.preferredContentSizeCategory)
    }

    DisposableEffect(Unit) {
        val center = NSNotificationCenter.defaultCenter
        val observer = center.addObserverForName(
            name = UIContentSizeCategoryDidChangeNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) {
            category = UIApplication.sharedApplication.preferredContentSizeCategory
        }
        onDispose { center.removeObserver(observer) }
    }

    return appleFontScaleFor(category.toAppleContentSizeCategory(), defaultFontScale)
}

@Composable
@OptIn(ExperimentalForeignApi::class)
actual fun rememberPlatformIncreasedContrast(): Boolean {
    var increasedContrast by remember { mutableStateOf(UIAccessibilityDarkerSystemColorsEnabled()) }

    DisposableEffect(Unit) {
        val center = NSNotificationCenter.defaultCenter
        val observer = center.addObserverForName(
            name = UIAccessibilityDarkerSystemColorsStatusDidChangeNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) {
            increasedContrast = UIAccessibilityDarkerSystemColorsEnabled()
        }
        onDispose { center.removeObserver(observer) }
    }

    return increasedContrast
}

@Composable
@OptIn(ExperimentalForeignApi::class)
actual fun rememberPlatformReduceMotion(): Boolean {
    var reduceMotion by remember { mutableStateOf(UIAccessibilityIsReduceMotionEnabled()) }

    DisposableEffect(Unit) {
        val center = NSNotificationCenter.defaultCenter
        val observer = center.addObserverForName(
            name = UIAccessibilityReduceMotionStatusDidChangeNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) {
            reduceMotion = UIAccessibilityIsReduceMotionEnabled()
        }
        onDispose { center.removeObserver(observer) }
    }

    return reduceMotion
}

@Composable
@OptIn(ExperimentalForeignApi::class)
actual fun rememberPlatformReduceTransparency(): Boolean {
    var reduceTransparency by remember { mutableStateOf(UIAccessibilityIsReduceTransparencyEnabled()) }

    DisposableEffect(Unit) {
        val center = NSNotificationCenter.defaultCenter
        val observer = center.addObserverForName(
            name = UIAccessibilityReduceTransparencyStatusDidChangeNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) {
            reduceTransparency = UIAccessibilityIsReduceTransparencyEnabled()
        }
        onDispose { center.removeObserver(observer) }
    }

    return reduceTransparency
}

@OptIn(ExperimentalForeignApi::class)
private fun String?.toAppleContentSizeCategory(): AppleContentSizeCategory? = when (this) {
    UIContentSizeCategoryExtraSmall -> AppleContentSizeCategory.ExtraSmall
    UIContentSizeCategorySmall -> AppleContentSizeCategory.Small
    UIContentSizeCategoryMedium -> AppleContentSizeCategory.Medium
    UIContentSizeCategoryLarge -> AppleContentSizeCategory.Large
    UIContentSizeCategoryExtraLarge -> AppleContentSizeCategory.ExtraLarge
    UIContentSizeCategoryExtraExtraLarge -> AppleContentSizeCategory.ExtraExtraLarge
    UIContentSizeCategoryExtraExtraExtraLarge -> AppleContentSizeCategory.ExtraExtraExtraLarge
    UIContentSizeCategoryAccessibilityMedium -> AppleContentSizeCategory.AccessibilityMedium
    UIContentSizeCategoryAccessibilityLarge -> AppleContentSizeCategory.AccessibilityLarge
    UIContentSizeCategoryAccessibilityExtraLarge -> AppleContentSizeCategory.AccessibilityExtraLarge
    UIContentSizeCategoryAccessibilityExtraExtraLarge -> AppleContentSizeCategory.AccessibilityExtraExtraLarge
    UIContentSizeCategoryAccessibilityExtraExtraExtraLarge -> AppleContentSizeCategory.AccessibilityExtraExtraExtraLarge
    else -> null
}
