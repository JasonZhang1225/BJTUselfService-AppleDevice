package team.bjtuss.bjtuselfservice.shared.security

import java.util.prefs.Preferences

fun createDesktopAccountSecurityStore(): AccountSecurityStore = AccountSecurityStore(
    // Keychain 是 macOS 专属。Windows 首版不把密码降级存入明文 Preferences；
    // 后续接入 Credential Manager/DPAPI 前，界面会隐藏“记住密码”能力。
    credentialVault = if (System.getProperty("os.name").startsWith("Mac", ignoreCase = true)) {
        MacOsKeychainCredentialVault()
    } else {
        null
    },
    preferences = DesktopAccountPreferences(),
)

private class DesktopAccountPreferences : AccountPreferences {
    private val preferences = Preferences.userRoot().node(
        "/team/bjtuss/bjtuselfservice/kmp/account",
    )

    override suspend fun shouldRememberCredentials(): Boolean =
        preferences.getBoolean(REMEMBER_CREDENTIALS_KEY, false)

    override suspend fun setShouldRememberCredentials(enabled: Boolean) {
        preferences.putBoolean(REMEMBER_CREDENTIALS_KEY, enabled)
        preferences.flush()
    }

    private companion object {
        const val REMEMBER_CREDENTIALS_KEY = "remember_credentials"
    }
}
