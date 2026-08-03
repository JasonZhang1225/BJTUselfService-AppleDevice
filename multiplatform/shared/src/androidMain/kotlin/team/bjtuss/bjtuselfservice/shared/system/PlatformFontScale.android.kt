package team.bjtuss.bjtuselfservice.shared.system

import androidx.compose.runtime.Composable

@Composable
actual fun rememberPlatformFontScale(defaultFontScale: Float): Float = defaultFontScale

@Composable
actual fun rememberPlatformIncreasedContrast(): Boolean = false

@Composable
actual fun rememberPlatformReduceMotion(): Boolean = false

@Composable
actual fun rememberPlatformReduceTransparency(): Boolean = false
