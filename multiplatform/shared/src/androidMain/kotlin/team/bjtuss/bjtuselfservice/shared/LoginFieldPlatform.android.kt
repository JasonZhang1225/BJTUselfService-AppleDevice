package team.bjtuss.bjtuselfservice.shared

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.imePadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType

/**
 * Android 学号框：维持现有数字键盘。Android 侧键盘默认就不允许密码/用户名框
 * 切换中文 IME，无需额外平台语义。
 */
actual fun usernameKeyboardOptions(): KeyboardOptions = KeyboardOptions(
    keyboardType = KeyboardType.Number,
)

/**
 * Android 密码框：维持现有密码键盘（遮罩 + 密码键盘类型），与改动前一致。
 */
actual fun passwordKeyboardOptions(): KeyboardOptions = KeyboardOptions(
    keyboardType = KeyboardType.Password,
)

/** Android 输入框长按菜单用系统默认，无需额外覆盖。 */
@Composable
actual fun ProvideNativeTextContextMenu(content: @Composable () -> Unit) {
    content()
}

@Composable
actual fun PlatformCredentialFields(
    username: String,
    password: String,
    enabled: Boolean,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onPasswordImeAction: () -> Unit,
    modifier: Modifier,
) {
    ComposeCredentialFields(
        username = username,
        password = password,
        enabled = enabled,
        onUsernameChange = onUsernameChange,
        onPasswordChange = onPasswordChange,
        onPasswordImeAction = onPasswordImeAction,
        modifier = modifier,
    )
}

actual fun dismissPlatformKeyboard() = Unit

actual fun Modifier.platformLoginKeyboardAvoidance(): Modifier = imePadding()

/** Android 用 Compose 遮罩，可提供显示/隐藏切换。 */
actual val showsPasswordVisibilityToggle: Boolean
    get() = true
