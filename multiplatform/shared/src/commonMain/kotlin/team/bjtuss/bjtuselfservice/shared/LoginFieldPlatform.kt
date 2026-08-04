package team.bjtuss.bjtuselfservice.shared

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 学号输入框的键盘选项。各平台在 commonMain 之外给出真正的平台语义：
 * iOS 走原生输入框并标记用户名内容类型，其余平台退回普通数字键盘。
 */
expect fun usernameKeyboardOptions(): KeyboardOptions

/**
 * 密码输入框的键盘选项。iOS 走原生安全输入（禁用中文 IME 联想 + 系统密码自动填充），
 * 其余平台退回普通密码键盘（仅遮罩 + 键盘类型）。
 */
expect fun passwordKeyboardOptions(): KeyboardOptions

/**
 * 平台原生样式的输入框右键菜单。仅 macOS desktop 用 AWT JPopupMenu 覆盖为原生样式
 * （剪切/拷贝/粘贴/全选）；iOS 与 Android 为无操作，直接渲染 [content]。
 */
@Composable
expect fun ProvideNativeTextContextMenu(content: @Composable () -> Unit)

/**
 * 账号和密码必须作为一个平台表单存在：iOS/macOS 使用同时存在的原生字段，
 * 让系统能把用户名与密码识别为同一组凭据；Android 保持 Compose 输入框。
 */
@Composable
expect fun PlatformCredentialFields(
    username: String,
    password: String,
    enabled: Boolean,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onPasswordImeAction: () -> Unit,
    modifier: Modifier = Modifier,
)

/** 收起当前平台正在显示的软键盘；非 Apple Compose 输入框由 FocusManager 同步处理。 */
expect fun dismissPlatformKeyboard()

/**
 * Keeps the login form reachable while the software keyboard is visible. [enabled] must become
 * false as soon as login leaves the editable state, so stale platform keyboard insets cannot keep
 * the loading UI compressed after Password AutoFill or an app foreground transition.
 */
expect fun Modifier.platformLoginKeyboardAvoidance(enabled: Boolean): Modifier

/**
 * 是否在密码框显示「显示/隐藏」切换。iOS 用原生 secure 字段实现密码自动填充，
 * 系统不允许运行中切换遮罩，故不提供该按钮；其余平台用 Compose 遮罩，可提供。
 */
expect val showsPasswordVisibilityToggle: Boolean
