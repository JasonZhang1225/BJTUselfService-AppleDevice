package team.bjtuss.bjtuselfservice.shared.security

import platform.Foundation.NSUserDefaults

fun createIosAccountSecurityStore(): AccountSecurityStore = AccountSecurityStore(
    credentialVault = IosKeychainCredentialVault(),
    preferences = IosAccountPreferences(),
)

private class IosAccountPreferences(
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) : AccountPreferences {
    override suspend fun shouldRememberCredentials(): Boolean =
        defaults.boolForKey(REMEMBER_CREDENTIALS_KEY)

    override suspend fun setShouldRememberCredentials(enabled: Boolean) {
        defaults.setBool(enabled, forKey = REMEMBER_CREDENTIALS_KEY)
    }

    private companion object {
        const val REMEMBER_CREDENTIALS_KEY = "remember_credentials"
    }
}
