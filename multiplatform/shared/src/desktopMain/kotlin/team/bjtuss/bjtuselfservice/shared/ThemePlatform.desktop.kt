package team.bjtuss.bjtuselfservice.shared

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF385885),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD7E4F8),
    onPrimaryContainer = Color(0xFF122A48),
    surface = Color(0xFFF9F9FC),
    surfaceVariant = Color(0xFFE8EAF0),
    background = Color(0xFFF4F5F9),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA9C7F2),
    onPrimary = Color(0xFF0B2F55),
    primaryContainer = Color(0xFF25466E),
    onPrimaryContainer = Color(0xFFD7E4F8),
    surface = Color(0xFF17191D),
    surfaceVariant = Color(0xFF2A2D33),
    background = Color(0xFF101216),
)

private val HighContrastLightColors = LightColors.copy(
    primary = Color(0xFF163E72),
    onPrimary = Color.White,
    onBackground = Color.Black,
    onSurface = Color.Black,
    onSurfaceVariant = Color(0xFF20242A),
    outline = Color(0xFF3B424C),
    outlineVariant = Color(0xFF626A76),
)

private val HighContrastDarkColors = DarkColors.copy(
    primary = Color(0xFFC6DCFF),
    onPrimary = Color(0xFF001D3A),
    onBackground = Color.White,
    onSurface = Color.White,
    onSurfaceVariant = Color(0xFFE1E3E8),
    outline = Color(0xFFC6CAD2),
    outlineVariant = Color(0xFF969CA7),
)

@Composable
internal actual fun platformColorScheme(
    darkTheme: Boolean,
    dynamicColorEnabled: Boolean,
    increasedContrast: Boolean,
): ColorScheme {
    // Desktop/macOS 固定品牌色；不提供动态取色。
    return when {
        darkTheme && increasedContrast -> HighContrastDarkColors
        darkTheme -> DarkColors
        increasedContrast -> HighContrastLightColors
        else -> LightColors
    }
}

actual fun platformSupportsDynamicColor(): Boolean = false
