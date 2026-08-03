package team.bjtuss.bjtuselfservice.shared

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.focusGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.KeyboardType
import com.sun.jna.Library
import com.sun.jna.Native
import java.awt.KeyboardFocusManager
import java.awt.Window
import java.beans.PropertyChangeListener
import java.io.File

actual fun usernameKeyboardOptions(): KeyboardOptions = KeyboardOptions(
    keyboardType = KeyboardType.Number,
)

actual fun passwordKeyboardOptions(): KeyboardOptions = KeyboardOptions(
    keyboardType = KeyboardType.Password,
)

/** 保持 Compose 输入框稳定可见；不再安装旧的 Swing JPopupMenu 覆盖菜单。 */
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
    val inputSourceGate = remember { MacOsCredentialInputContextGate() }
    DisposableEffect(inputSourceGate) {
        onDispose { inputSourceGate.setRestricted(false) }
    }

    ComposeCredentialFields(
        username = username,
        password = password,
        enabled = enabled,
        onUsernameChange = { raw -> onUsernameChange(raw.sanitizeDesktopCredentialInput()) },
        onPasswordChange = { raw -> onPasswordChange(raw.sanitizeDesktopCredentialInput()) },
        onPasswordImeAction = onPasswordImeAction,
        modifier = modifier
            .onFocusChanged { inputSourceGate.setRestricted(it.hasFocus) }
            .focusGroup(),
    )
}

/** 凭据只接受 ASCII 可打印字符；粘贴内容也走同一条限制。 */
internal fun String.sanitizeDesktopCredentialInput(): String = filter { it.code in 0x20..0x7E }

/**
 * Compose Desktop 的 KeyboardType 只是语义提示，不会限制 macOS 当前输入源。
 * 凭据组聚焦时，把当前 AppKit 文本输入上下文限制为 Roman 输入源；离开凭据组或
 * 窗口失焦时恢复原配置，不把这项限制扩散到应用其他文本框或其他应用。
 */
private class MacOsCredentialInputContextGate {
    private val focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
    private val nativeHelper = MacOsCredentialInputSourceNative.loadOrNull()
    private var restricted = false
    private var ownerWindow: Window? = null
    private var nativeRestrictionApplied = false
    private val activeWindowListener = PropertyChangeListener { updateNativeRestriction() }

    fun setRestricted(value: Boolean) {
        if (restricted == value) return
        restricted = value
        if (value) {
            ownerWindow = focusManager.activeWindow
            focusManager.addPropertyChangeListener("activeWindow", activeWindowListener)
        } else {
            focusManager.removePropertyChangeListener("activeWindow", activeWindowListener)
        }
        updateNativeRestriction()
        if (!value) ownerWindow = null
    }

    private fun updateNativeRestriction() {
        val shouldApply = restricted && focusManager.activeWindow === ownerWindow
        if (shouldApply == nativeRestrictionApplied) return
        if (nativeHelper?.bjtuSetCredentialInputSourceRestricted(if (shouldApply) 1 else 0) == 0) {
            nativeRestrictionApplied = shouldApply
        }
    }
}

private interface MacOsCredentialInputSourceNative : Library {
    fun bjtuSetCredentialInputSourceRestricted(restricted: Int): Int

    companion object {
        fun loadOrNull(): MacOsCredentialInputSourceNative? = runCatching {
            val library = locateInputSourceHelper() ?: return null
            Native.load(library.absolutePath, MacOsCredentialInputSourceNative::class.java)
        }.getOrNull()
    }
}

private fun locateInputSourceHelper(): File? {
    System.getProperty(INPUT_SOURCE_HELPER_PROPERTY)?.let(::File)?.takeIf(File::isFile)?.let {
        return it
    }
    val executable = ProcessHandle.current().info().command().orElse(null)?.let(::File)
        ?: return null
    val contents = executable.parentFile?.parentFile ?: return null
    return File(contents, "Resources/InputSource/libBJTUInputSourceHelper.dylib")
        .takeIf(File::isFile)
}

private const val INPUT_SOURCE_HELPER_PROPERTY = "bjtu.input-source.helper"

actual fun dismissPlatformKeyboard() = Unit

actual fun Modifier.platformLoginKeyboardAvoidance(): Modifier = this

actual val showsPasswordVisibilityToggle: Boolean
    get() = true
