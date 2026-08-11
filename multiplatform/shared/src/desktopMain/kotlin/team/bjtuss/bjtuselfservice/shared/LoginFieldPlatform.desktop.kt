package team.bjtuss.bjtuselfservice.shared

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.focusGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.sun.jna.Callback
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import java.awt.EventQueue
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
    val windowHandle by desktopCredentialWindowHandle
    val bridge = remember(windowHandle) { MacOsCredentialBridge.loadOrNull() }
    val latestUsernameChange = rememberUpdatedState(onUsernameChange)
    val latestPasswordChange = rememberUpdatedState(onPasswordChange)
    val latestPasswordImeAction = rememberUpdatedState(onPasswordImeAction)
    val nativeHost = remember(windowHandle, bridge) {
        if (windowHandle == 0L || bridge == null) {
            null
        } else {
            NativeCredentialFieldsHost.create(
                bridge = bridge,
                windowHandle = windowHandle,
                onEvent = { event, value ->
                    EventQueue.invokeLater {
                        when (event) {
                            NATIVE_EVENT_USERNAME_CHANGED -> latestUsernameChange.value(
                                value.sanitizeDesktopCredentialInput(),
                            )
                            NATIVE_EVENT_PASSWORD_CHANGED -> latestPasswordChange.value(
                                value.sanitizeDesktopCredentialInput(),
                            )
                            NATIVE_EVENT_PASSWORD_SUBMIT -> latestPasswordImeAction.value()
                        }
                    }
                },
            )
        }
    }

    if (nativeHost != null) {
        val density = LocalDensity.current.density
        SideEffect {
            nativeHost.updateValues(username, password, enabled)
        }
        DisposableEffect(nativeHost) {
            onDispose { nativeHost.close() }
        }
        Spacer(
            modifier = modifier
                .height(NATIVE_CREDENTIAL_FIELDS_HEIGHT.dp)
                .onGloballyPositioned { coordinates ->
                    nativeHost.updateFrame(coordinates.boundsInWindow(), density)
                },
        )
    } else {
        val inputSourceGate = remember { MacOsCredentialInputContextGate(bridge) }
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
}

/** 凭据只接受 ASCII 可打印字符；粘贴内容也走同一条限制。 */
internal fun String.sanitizeDesktopCredentialInput(): String = filter { it.code in 0x20..0x7E }

/**
 * Compose Desktop 的 KeyboardType 只是语义提示，不会限制 macOS 当前输入源。
 * 凭据组聚焦时，把当前 AppKit 文本输入上下文限制为 Roman 输入源；离开凭据组或
 * 窗口失焦时恢复原配置，不把这项限制扩散到应用其他文本框或其他应用。
 */
private class MacOsCredentialInputContextGate(
    private val nativeHelper: MacOsCredentialBridge?,
) {
    private val focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
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

private interface MacOsCredentialCallback : Callback {
    fun invoke(event: Int, value: Pointer?)
}

private interface MacOsCredentialBridge : Library {
    fun bjtuSetCredentialInputSourceRestricted(restricted: Int): Int
    fun bjtuCreateCredentialFields(
        windowHandle: Long,
        callback: MacOsCredentialCallback,
    ): Pointer?
    fun bjtuUpdateCredentialFields(
        host: Pointer,
        username: String,
        password: String,
        enabled: Int,
    )
    fun bjtuSetCredentialFieldsFrame(
        host: Pointer,
        x: Double,
        y: Double,
        width: Double,
        height: Double,
        density: Double,
    )
    fun bjtuDestroyCredentialFields(host: Pointer)

    companion object {
        fun loadOrNull(): MacOsCredentialBridge? = runCatching {
            val library = locateInputSourceHelper() ?: return null
            Native.load(library.absolutePath, MacOsCredentialBridge::class.java)
        }.getOrNull()
    }
}

private class NativeCredentialFieldsHost private constructor(
    private val bridge: MacOsCredentialBridge,
    private val callback: MacOsCredentialCallback,
    private val host: Pointer,
) {
    fun updateValues(username: String, password: String, enabled: Boolean) {
        bridge.bjtuUpdateCredentialFields(host, username, password, if (enabled) 1 else 0)
    }

    fun updateFrame(bounds: Rect, density: Float) {
        if (bounds.width <= 0f || bounds.height <= 0f) return
        bridge.bjtuSetCredentialFieldsFrame(
            host = host,
            x = bounds.left.toDouble(),
            y = bounds.top.toDouble(),
            width = bounds.width.toDouble(),
            height = bounds.height.toDouble(),
            density = density.toDouble(),
        )
    }

    fun close() {
        bridge.bjtuDestroyCredentialFields(host)
    }

    companion object {
        fun create(
            bridge: MacOsCredentialBridge,
            windowHandle: Long,
            onEvent: (Int, String) -> Unit,
        ): NativeCredentialFieldsHost? {
            val callback = object : MacOsCredentialCallback {
                override fun invoke(event: Int, value: Pointer?) {
                    onEvent(event, value?.getString(0, Charsets.UTF_8.name()).orEmpty())
                }
            }
            val host = bridge.bjtuCreateCredentialFields(windowHandle, callback) ?: return null
            return NativeCredentialFieldsHost(bridge, callback, host)
        }
    }
}

/** Desktop host 在窗口创建后注册 ComposeWindow 已提供的原生句柄。 */
fun registerDesktopCredentialWindowHandle(windowHandle: Long) {
    desktopCredentialWindowHandle.longValue = windowHandle
}

internal val desktopCredentialWindowHandle = mutableLongStateOf(0L)

internal fun locateInputSourceHelper(): File? {
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
private const val NATIVE_CREDENTIAL_FIELDS_HEIGHT = 128
private const val NATIVE_EVENT_USERNAME_CHANGED = 1
private const val NATIVE_EVENT_PASSWORD_CHANGED = 2
private const val NATIVE_EVENT_PASSWORD_SUBMIT = 3

actual fun dismissPlatformKeyboard() = Unit

actual fun Modifier.platformLoginKeyboardAvoidance(enabled: Boolean): Modifier = this

actual val showsPasswordVisibilityToggle: Boolean
    get() = true
