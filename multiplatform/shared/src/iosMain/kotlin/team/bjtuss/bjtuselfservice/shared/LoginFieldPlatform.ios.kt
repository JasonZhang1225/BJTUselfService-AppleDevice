package team.bjtuss.bjtuselfservice.shared

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.interop.UIKitView
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PlatformImeOptions
import androidx.compose.ui.unit.dp
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSSelectorFromString
import platform.UIKit.UIApplication
import platform.UIKit.UIColor
import platform.UIKit.UIFont
import platform.UIKit.UIFontTextStyleBody
import platform.UIKit.UIKeyboardTypeASCIICapable
import platform.UIKit.UIReturnKeyType
import platform.UIKit.UITextAutocapitalizationType
import platform.UIKit.UITextAutocorrectionType
import platform.UIKit.UITextBorderStyle
import platform.UIKit.UITextContentTypePassword
import platform.UIKit.UITextContentTypeUsername
import platform.UIKit.UITextField
import platform.UIKit.UITextFieldDelegateProtocol
import platform.UIKit.UITextFieldTextDidChangeNotification
import platform.UIKit.UITextFieldViewMode
import platform.UIKit.UITraitCollection
import platform.UIKit.UIUserInterfaceStyle
import platform.UIKit.UIView

@OptIn(ExperimentalComposeUiApi::class)
actual fun usernameKeyboardOptions(): KeyboardOptions = KeyboardOptions(
    capitalization = KeyboardCapitalization.None,
    autoCorrectEnabled = false,
    keyboardType = KeyboardType.Ascii,
    platformImeOptions = PlatformImeOptions {
        usingNativeTextInput(true)
        textContentType(UITextContentTypeUsername)
    },
)

@OptIn(ExperimentalComposeUiApi::class)
actual fun passwordKeyboardOptions(): KeyboardOptions = KeyboardOptions(
    capitalization = KeyboardCapitalization.None,
    autoCorrectEnabled = false,
    keyboardType = KeyboardType.Password,
    platformImeOptions = PlatformImeOptions {
        usingNativeTextInput(true)
        isSecureTextEntry(true)
        textContentType(UITextContentTypePassword)
    },
)

@Composable
actual fun ProvideNativeTextContextMenu(content: @Composable () -> Unit) {
    content()
}

/**
 * 两个 UITextField 始终作为同一 UIView 的兄弟节点存在。与 Compose 按焦点临时创建
 * 单个原生输入会话相比，Password AutoFill 能同时识别用户名和密码并成组写入。
 */
@OptIn(ExperimentalForeignApi::class)
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
    val latestUsernameChange = rememberUpdatedState(onUsernameChange)
    val latestPasswordChange = rememberUpdatedState(onPasswordChange)
    val latestPasswordImeAction = rememberUpdatedState(onPasswordImeAction)
    UIKitView(
        modifier = modifier.height(128.dp),
        background = Color.Transparent,
        accessibilityEnabled = true,
        factory = {
            NativeCredentialFieldsView(
                onUsernameChange = { latestUsernameChange.value(it) },
                onPasswordChange = { latestPasswordChange.value(it) },
                onPasswordImeAction = { latestPasswordImeAction.value() },
            )
        },
        update = { view ->
            view.updateValues(username, password, enabled)
        },
        onRelease = NativeCredentialFieldsView::dispose,
    )
}

@OptIn(ExperimentalForeignApi::class)
private class NativeCredentialFieldsView(
    private val onUsernameChange: (String) -> Unit,
    private val onPasswordChange: (String) -> Unit,
    private val onPasswordImeAction: () -> Unit,
) : UIView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0)), UITextFieldDelegateProtocol {
    private val usernameField = nativeTextField("学号").apply {
        textContentType = UITextContentTypeUsername
        // 数字键盘没有 Return；ASCII 键盘保留“下一项”，同时仍阻止中文输入法联想。
        keyboardType = UIKeyboardTypeASCIICapable
        returnKeyType = UIReturnKeyType.UIReturnKeyNext
        clearButtonMode = UITextFieldViewMode.UITextFieldViewModeWhileEditing
    }
    private val passwordField = nativeTextField("密码").apply {
        textContentType = UITextContentTypePassword
        keyboardType = UIKeyboardTypeASCIICapable
        returnKeyType = UIReturnKeyType.UIReturnKeyGo
        secureTextEntry = true
    }
    private val notificationCenter = NSNotificationCenter.defaultCenter
    private val usernameObserver = notificationCenter.addObserverForName(
        name = UITextFieldTextDidChangeNotification,
        `object` = usernameField,
        queue = NSOperationQueue.mainQueue,
    ) { onUsernameChange(usernameField.text.orEmpty()) }
    private val passwordObserver = notificationCenter.addObserverForName(
        name = UITextFieldTextDidChangeNotification,
        `object` = passwordField,
        queue = NSOperationQueue.mainQueue,
    ) { onPasswordChange(passwordField.text.orEmpty()) }

    init {
        opaque = false
        backgroundColor = UIColor.clearColor
        usernameField.delegate = this
        passwordField.delegate = this
        addSubview(usernameField)
        addSubview(passwordField)
        refreshDynamicColors()
    }

    override fun layoutSubviews() {
        super.layoutSubviews()
        val spacing = 16.0
        val width = bounds.useContents { size.width }
        val height = bounds.useContents { size.height }
        val fieldHeight = (height - spacing) / 2.0
        usernameField.setFrame(CGRectMake(0.0, 0.0, width, fieldHeight))
        passwordField.setFrame(CGRectMake(0.0, fieldHeight + spacing, width, fieldHeight))
    }

    fun updateValues(username: String, password: String, enabled: Boolean) {
        if (usernameField.text != username) usernameField.text = username
        if (passwordField.text != password) passwordField.text = password
        usernameField.enabled = enabled
        passwordField.enabled = enabled
    }

    override fun textFieldShouldReturn(textField: UITextField): Boolean {
        return when (textField) {
            usernameField -> {
                passwordField.becomeFirstResponder()
                true
            }
            passwordField -> {
                passwordField.resignFirstResponder()
                onPasswordImeAction()
                true
            }
            else -> false
        }
    }

    override fun traitCollectionDidChange(previousTraitCollection: UITraitCollection?) {
        super.traitCollectionDidChange(previousTraitCollection)
        refreshDynamicColors()
    }

    private fun refreshDynamicColors() {
        val dark = traitCollection.userInterfaceStyle == UIUserInterfaceStyle.UIUserInterfaceStyleDark
        val background = if (dark) uiColor(0x23262C) else uiColor(0xF1F3F8)
        val text = if (dark) uiColor(0xF1F1F4) else uiColor(0x1A1B1F)
        val outline = if (dark) uiColor(0x8E939D) else uiColor(0x7A818C)
        val accent = if (dark) uiColor(0xA9C7F2) else uiColor(0x385885)
        listOf(usernameField, passwordField).forEach { field ->
            field.backgroundColor = background
            field.textColor = text
            field.tintColor = accent
            field.layer.borderColor = outline.CGColor
        }
    }

    fun dispose() {
        notificationCenter.removeObserver(usernameObserver)
        notificationCenter.removeObserver(passwordObserver)
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun nativeTextField(placeholder: String): UITextField = UITextField(
    frame = CGRectMake(0.0, 0.0, 0.0, 0.0),
).apply {
    this.placeholder = placeholder
    borderStyle = UITextBorderStyle.UITextBorderStyleNone
    font = UIFont.preferredFontForTextStyle(UIFontTextStyleBody)
    adjustsFontForContentSizeCategory = true
    autocapitalizationType = UITextAutocapitalizationType.UITextAutocapitalizationTypeNone
    autocorrectionType = UITextAutocorrectionType.UITextAutocorrectionTypeNo
    enablesReturnKeyAutomatically = true
    layer.cornerRadius = 14.0
    layer.borderWidth = 1.0
    clipsToBounds = true
    leftView = UIView(frame = CGRectMake(0.0, 0.0, 14.0, 1.0))
    leftViewMode = UITextFieldViewMode.UITextFieldViewModeAlways
}

private fun uiColor(rgb: Int): UIColor = UIColor(
    red = ((rgb shr 16) and 0xFF) / 255.0,
    green = ((rgb shr 8) and 0xFF) / 255.0,
    blue = (rgb and 0xFF) / 255.0,
    alpha = 1.0,
)

@OptIn(ExperimentalForeignApi::class)
actual fun dismissPlatformKeyboard() {
    UIApplication.sharedApplication.sendAction(
        action = NSSelectorFromString("resignFirstResponder"),
        to = null,
        from = null,
        forEvent = null,
    )
}

actual fun Modifier.platformLoginKeyboardAvoidance(): Modifier = this

actual val showsPasswordVisibilityToggle: Boolean
    get() = false
