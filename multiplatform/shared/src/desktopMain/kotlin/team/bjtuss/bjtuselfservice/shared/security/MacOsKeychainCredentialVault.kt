package team.bjtuss.bjtuselfservice.shared.security

import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import com.sun.jna.ptr.PointerByReference
import team.bjtuss.bjtuselfservice.shared.auth.Credentials

class MacOsKeychainCredentialVault(
    private val service: String = "team.bjtuss.bjtuselfservice.kmp.credentials",
    private val account: String = "primary",
) : CredentialVault {
    override suspend fun save(credentials: Credentials) {
        val clearStatus = withQuery { SecurityApi.INSTANCE.SecItemDelete(it) }
        if (clearStatus != ERR_SEC_SUCCESS && clearStatus != ERR_SEC_ITEM_NOT_FOUND) {
            throw vaultError(CredentialVaultOperation.SAVE, clearStatus)
        }

        val status = withQuery(payload = encodeCredentialPayload(credentials)) {
            SecurityApi.INSTANCE.SecItemAdd(it, null)
        }
        if (status != ERR_SEC_SUCCESS) {
            throw vaultError(CredentialVaultOperation.SAVE, status)
        }
    }

    override suspend fun load(): Credentials? {
        val result = PointerByReference()
        val status = withQuery(returnData = true) {
            SecurityApi.INSTANCE.SecItemCopyMatching(it, result)
        }
        if (status == ERR_SEC_ITEM_NOT_FOUND) return null
        if (status != ERR_SEC_SUCCESS) {
            throw vaultError(CredentialVaultOperation.LOAD, status)
        }

        val data = result.value ?: throw vaultError(CredentialVaultOperation.LOAD, status)
        return try {
            val length = CoreFoundationApi.INSTANCE.CFDataGetLength(data)
            val bytes = CoreFoundationApi.INSTANCE.CFDataGetBytePtr(data)
                ?.getByteArray(0, length.toInt())
                ?: byteArrayOf()
            decodeCredentialPayload(bytes)
                ?: throw vaultError(CredentialVaultOperation.LOAD, status)
        } finally {
            CoreFoundationApi.INSTANCE.CFRelease(data)
        }
    }

    override suspend fun clear() {
        val status = withQuery { SecurityApi.INSTANCE.SecItemDelete(it) }
        if (status != ERR_SEC_SUCCESS && status != ERR_SEC_ITEM_NOT_FOUND) {
            throw vaultError(CredentialVaultOperation.CLEAR, status)
        }
    }

    private fun <T> withQuery(
        payload: ByteArray? = null,
        returnData: Boolean = false,
        block: (Pointer) -> T,
    ): T {
        val ownedValues = mutableListOf<Pointer>()
        fun cfString(value: String): Pointer = CoreFoundationApi.INSTANCE.createString(value)
            .also(ownedValues::add)
        fun cfData(value: ByteArray): Pointer = CoreFoundationApi.INSTANCE.createData(value)
            .also(ownedValues::add)

        val entries = mutableListOf(
            SecuritySymbols.kSecClass to SecuritySymbols.kSecClassGenericPassword,
            SecuritySymbols.kSecAttrService to cfString(service),
            SecuritySymbols.kSecAttrAccount to cfString(account),
        )
        if (payload != null) {
            entries += SecuritySymbols.kSecValueData to cfData(payload)
        }
        if (returnData) {
            entries += SecuritySymbols.kSecReturnData to CoreFoundationSymbols.kCFBooleanTrue
            entries += SecuritySymbols.kSecMatchLimit to SecuritySymbols.kSecMatchLimitOne
        }

        val dictionary = CoreFoundationApi.INSTANCE.CFDictionaryCreate(
            null,
            entries.map { it.first }.toTypedArray(),
            entries.map { it.second }.toTypedArray(),
            entries.size.toLong(),
            CoreFoundationSymbols.kCFTypeDictionaryKeyCallBacks,
            CoreFoundationSymbols.kCFTypeDictionaryValueCallBacks,
        ) ?: error("Unable to create macOS Keychain query")

        return try {
            block(dictionary)
        } finally {
            CoreFoundationApi.INSTANCE.CFRelease(dictionary)
            ownedValues.forEach(CoreFoundationApi.INSTANCE::CFRelease)
        }
    }
}

private interface SecurityApi : Library {
    fun SecItemAdd(attributes: Pointer, result: PointerByReference?): Int
    fun SecItemCopyMatching(query: Pointer, result: PointerByReference): Int
    fun SecItemDelete(query: Pointer): Int

    companion object {
        val INSTANCE: SecurityApi = Native.load(SECURITY_FRAMEWORK, SecurityApi::class.java)
    }
}

private interface CoreFoundationApi : Library {
    fun CFStringCreateWithCString(allocator: Pointer?, value: Pointer, encoding: Int): Pointer?
    fun CFDataCreate(allocator: Pointer?, bytes: Pointer, length: Long): Pointer?
    fun CFDataGetLength(data: Pointer): Long
    fun CFDataGetBytePtr(data: Pointer): Pointer?
    fun CFDictionaryCreate(
        allocator: Pointer?,
        keys: Array<Pointer>,
        values: Array<Pointer>,
        count: Long,
        keyCallbacks: Pointer,
        valueCallbacks: Pointer,
    ): Pointer?
    fun CFRelease(value: Pointer)

    fun createString(value: String): Pointer {
        val utf8 = value.encodeToByteArray()
        val memory = Memory(utf8.size.toLong() + 1)
        memory.write(0, utf8, 0, utf8.size)
        memory.setByte(utf8.size.toLong(), 0)
        return CFStringCreateWithCString(null, memory, CF_STRING_ENCODING_UTF8)
            ?: error("Unable to create CFString")
    }

    fun createData(value: ByteArray): Pointer {
        val memory = Memory(value.size.coerceAtLeast(1).toLong())
        if (value.isNotEmpty()) memory.write(0, value, 0, value.size)
        return CFDataCreate(null, memory, value.size.toLong())
            ?: error("Unable to create CFData")
    }

    companion object {
        val INSTANCE: CoreFoundationApi = Native.load(CORE_FOUNDATION_FRAMEWORK, CoreFoundationApi::class.java)
    }
}

private object SecuritySymbols {
    private val library = NativeLibrary.getInstance(SECURITY_FRAMEWORK)
    val kSecClass: Pointer = library.pointerConstant("kSecClass")
    val kSecClassGenericPassword: Pointer = library.pointerConstant("kSecClassGenericPassword")
    val kSecAttrService: Pointer = library.pointerConstant("kSecAttrService")
    val kSecAttrAccount: Pointer = library.pointerConstant("kSecAttrAccount")
    val kSecValueData: Pointer = library.pointerConstant("kSecValueData")
    val kSecReturnData: Pointer = library.pointerConstant("kSecReturnData")
    val kSecMatchLimit: Pointer = library.pointerConstant("kSecMatchLimit")
    val kSecMatchLimitOne: Pointer = library.pointerConstant("kSecMatchLimitOne")
}

private object CoreFoundationSymbols {
    private val library = NativeLibrary.getInstance(CORE_FOUNDATION_FRAMEWORK)
    val kCFBooleanTrue: Pointer = library.pointerConstant("kCFBooleanTrue")
    val kCFTypeDictionaryKeyCallBacks: Pointer =
        library.getGlobalVariableAddress("kCFTypeDictionaryKeyCallBacks")
    val kCFTypeDictionaryValueCallBacks: Pointer =
        library.getGlobalVariableAddress("kCFTypeDictionaryValueCallBacks")
}

private fun NativeLibrary.pointerConstant(name: String): Pointer =
    getGlobalVariableAddress(name).getPointer(0)

private fun vaultError(
    operation: CredentialVaultOperation,
    status: Int,
): CredentialVaultException = CredentialVaultException(operation, platformStatus = status)

private const val SECURITY_FRAMEWORK = "/System/Library/Frameworks/Security.framework/Security"
private const val CORE_FOUNDATION_FRAMEWORK =
    "/System/Library/Frameworks/CoreFoundation.framework/CoreFoundation"
private const val CF_STRING_ENCODING_UTF8 = 0x08000100
private const val ERR_SEC_SUCCESS = 0
private const val ERR_SEC_ITEM_NOT_FOUND = -25300
