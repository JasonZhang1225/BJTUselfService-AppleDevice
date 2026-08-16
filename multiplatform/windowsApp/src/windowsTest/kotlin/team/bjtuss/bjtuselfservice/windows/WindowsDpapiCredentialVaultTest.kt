package team.bjtuss.bjtuselfservice.windows

import kotlinx.coroutines.runBlocking
import team.bjtuss.bjtuselfservice.shared.auth.Credentials
import team.bjtuss.bjtuselfservice.shared.security.CredentialVaultException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WindowsDpapiCredentialVaultTest {

    @Test
    fun `dpapi save load round trip`() = runBlocking<Unit> {
        val vault = WindowsDpapiCredentialVault(
            service = "test.service.${System.nanoTime()}",
            account = "roundtrip",
        )
        val credentials = Credentials("21370000", "test-password-123")
        vault.save(credentials)
        assertEquals(credentials, vault.load())
        vault.clear()
        assertNull(vault.load())
    }

    @Test
    fun `dpapi clear removes payload`() = runBlocking<Unit> {
        val vault = WindowsDpapiCredentialVault(
            service = "test.service.${System.nanoTime()}",
            account = "clear",
        )
        vault.save(Credentials("21370001", "pw"))
        vault.clear()
        assertNull(vault.load())
    }

    @Test
    fun `dpapi protects against ciphertext tampering`() = runBlocking<Unit> {
        val service = "test.service.${System.nanoTime()}"
        val vault = WindowsDpapiCredentialVault(
            service = service,
            account = "tamper",
        )
        vault.save(Credentials("21370002", "pw"))
        // DPAPI 完整性校验：篡改 base64 后解密必须失败（抛异常而非返回错误数据）
        assertFailsWith<CredentialVaultException> {
            val node = java.util.prefs.Preferences.userRoot()
                .node("/team/bjtuss/bjtuselfservice/kmp/credentials")
                .node("$service/tamper")
            val payload = node.get("credential_payload", "")
            if (payload.isNotEmpty()) {
                // 把第一个非 'A' 字符替换为 'A'，确保密文确实被改动
                val index = payload.indexOfFirst { it != 'A' }
                val tampered = if (index >= 0) {
                    payload.substring(0, index) + "A" + payload.substring(index + 1)
                } else {
                    "B" + payload.substring(1)
                }
                node.put("credential_payload", tampered)
                node.flush()
            }
            vault.load()
        }
    }
}
