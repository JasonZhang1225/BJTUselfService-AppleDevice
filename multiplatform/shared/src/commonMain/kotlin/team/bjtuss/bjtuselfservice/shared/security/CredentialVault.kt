package team.bjtuss.bjtuselfservice.shared.security

import team.bjtuss.bjtuselfservice.shared.auth.Credentials

interface CredentialVault {
    suspend fun save(credentials: Credentials)
    suspend fun load(): Credentials?
    suspend fun clear()
}

class CredentialVaultException(
    val operation: CredentialVaultOperation,
    val platformStatus: Int? = null,
    cause: Throwable? = null,
) : Exception("Credential vault operation failed: $operation", cause)

enum class CredentialVaultOperation {
    SAVE,
    LOAD,
    CLEAR,
}
