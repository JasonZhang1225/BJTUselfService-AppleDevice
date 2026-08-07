package team.bjtuss.bjtuselfservice.shared

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

/**
 * 平台主题色板。
 * Android 在 [dynamicColorEnabled] 且系统支持时用 Material You 动态取色；
 * iOS/macOS 固定品牌色，忽略动态取色开关（设置页也不展示该开关）。
 */
@Composable
internal expect fun platformColorScheme(
    darkTheme: Boolean,
    dynamicColorEnabled: Boolean,
    increasedContrast: Boolean,
): ColorScheme

/** 当前平台是否提供「动态取色」设置项（仅 Android）。 */
expect fun platformSupportsDynamicColor(): Boolean
