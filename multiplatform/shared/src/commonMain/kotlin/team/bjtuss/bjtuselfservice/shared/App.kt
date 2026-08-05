package team.bjtuss.bjtuselfservice.shared

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import team.bjtuss.bjtuselfservice.shared.cache.AppPreferences
import team.bjtuss.bjtuselfservice.shared.cache.CacheStoreHandle
import team.bjtuss.bjtuselfservice.shared.feature.shell.AppCommandBus
import team.bjtuss.bjtuselfservice.shared.security.AccountSecurityStore
import team.bjtuss.bjtuselfservice.shared.files.HomeworkFileGateway
import team.bjtuss.bjtuselfservice.shared.files.UnavailableHomeworkFileGateway
import team.bjtuss.bjtuselfservice.shared.files.CoursewareDirectoryGateway
import team.bjtuss.bjtuselfservice.shared.files.UnavailableCoursewareDirectoryGateway
import team.bjtuss.bjtuselfservice.shared.system.rememberPlatformIncreasedContrast
import team.bjtuss.bjtuselfservice.shared.system.rememberPlatformFontScale
import team.bjtuss.bjtuselfservice.shared.system.rememberPlatformReduceMotion
import team.bjtuss.bjtuselfservice.shared.system.rememberPlatformReduceTransparency
import team.bjtuss.bjtuselfservice.shared.auth.CaptchaRecognizer
import team.bjtuss.bjtuselfservice.shared.auth.UnavailableCaptchaRecognizer

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
fun App(
    accountSecurityStore: AccountSecurityStore,
    cacheStoreHandle: CacheStoreHandle,
    homeworkFileGateway: HomeworkFileGateway = UnavailableHomeworkFileGateway,
    coursewareDirectoryGateway: CoursewareDirectoryGateway = UnavailableCoursewareDirectoryGateway,
    appCommandBus: AppCommandBus? = null,
    captchaRecognizer: CaptchaRecognizer = UnavailableCaptchaRecognizer,
    nativeNavigationEnabled: Boolean = false,
    onOpenNativeRoute: (String) -> Unit = {},
    onAuthenticatedSessionChanged: (AuthenticatedSession?) -> Unit = {},
) {
    val cacheStore = cacheStoreHandle.store
    var appPreferences by remember(cacheStore) {
        mutableStateOf(runCatching(cacheStore::preferences).getOrDefault(AppPreferences()))
    }
    // 整个 App 始终跟随系统深浅色，不再持久化主题偏好。
    val useDarkTheme = isSystemInDarkTheme()
    val onPreferencesChanged: (AppPreferences) -> Boolean = { updated ->
        runCatching { cacheStore.savePreferences(updated) }.isSuccess.also { saved ->
            if (saved) appPreferences = updated
        }
    }

    PlatformAppTheme(useDarkTheme = useDarkTheme) { effectiveFontScale ->
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                LoginRoute(
                    platform = currentPlatform(),
                    windowClass = adaptiveWindowClassFor(
                        widthDp = maxWidth.value.toInt(),
                        fontScale = effectiveFontScale,
                    ),
                    accountSecurityStore = accountSecurityStore,
                    cacheStoreHandle = cacheStoreHandle,
                    homeworkFileGateway = homeworkFileGateway,
                    coursewareDirectoryGateway = coursewareDirectoryGateway,
                    appCommandBus = appCommandBus,
                    appPreferences = appPreferences,
                    onPreferencesChanged = onPreferencesChanged,
                    captchaRecognizer = captchaRecognizer,
                    nativeNavigationEnabled = nativeNavigationEnabled,
                    onOpenNativeRoute = onOpenNativeRoute,
                    onAuthenticatedSessionChanged = onAuthenticatedSessionChanged,
                )
            }
        }
    }
}

/** 平台 Activity/UIViewController 中渲染单个原生导航目的地。 */
@Composable
fun AuthenticatedDestinationApp(
    session: AuthenticatedSession,
    routeId: String,
    homeworkFileGateway: HomeworkFileGateway = session.homeworkFileGateway,
    coursewareDirectoryGateway: CoursewareDirectoryGateway = session.coursewareDirectoryGateway,
    onOpenNativeRoute: (String) -> Unit,
    onCloseNativeRoute: () -> Unit,
) {
    PlatformAppTheme(useDarkTheme = isSystemInDarkTheme()) { effectiveFontScale ->
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                team.bjtuss.bjtuselfservice.shared.feature.grade.AuthenticatedAppShell(
                    session = session,
                    platform = currentPlatform(),
                    windowClass = adaptiveWindowClassFor(
                        widthDp = maxWidth.value.toInt(),
                        fontScale = effectiveFontScale,
                    ),
                    nativeNavigationEnabled = true,
                    onOpenNativeRoute = onOpenNativeRoute,
                    forcedRouteId = routeId,
                    onCloseNativeRoute = onCloseNativeRoute,
                    homeworkFileGatewayOverride = homeworkFileGateway,
                    coursewareDirectoryGatewayOverride = coursewareDirectoryGateway,
                )
            }
        }
    }
}

@Composable
private fun PlatformAppTheme(
    useDarkTheme: Boolean,
    content: @Composable (effectiveFontScale: Float) -> Unit,
) {
    val systemDensity = LocalDensity.current
    val effectiveFontScale = rememberPlatformFontScale(systemDensity.fontScale)
    val increasedContrast = rememberPlatformIncreasedContrast()
    val reduceMotion = rememberPlatformReduceMotion()
    val reduceTransparency = rememberPlatformReduceTransparency()
    val colorScheme = when {
        useDarkTheme && increasedContrast -> HighContrastDarkColors
        useDarkTheme -> DarkColors
        increasedContrast -> HighContrastLightColors
        else -> LightColors
    }
    CompositionLocalProvider(
        LocalDensity provides Density(systemDensity.density, effectiveFontScale),
        LocalReduceMotion provides reduceMotion,
        LocalReduceTransparency provides reduceTransparency,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            motionScheme = if (reduceMotion) ReducedMotionScheme else MotionScheme.standard(),
        ) {
            content(effectiveFontScale)
        }
    }
}

internal fun adaptiveWindowClassFor(widthDp: Int, fontScale: Float): WindowClass {
    val widthClass = windowClassFor(widthDp)
    return if (widthClass == WindowClass.Expanded && fontScale >= 1.5f) {
        WindowClass.Medium
    } else {
        widthClass
    }
}
