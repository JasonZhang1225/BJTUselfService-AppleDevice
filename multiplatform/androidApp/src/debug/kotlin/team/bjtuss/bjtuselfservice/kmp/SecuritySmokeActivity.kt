package team.bjtuss.bjtuselfservice.kmp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import team.bjtuss.bjtuselfservice.shared.auth.Credentials
import team.bjtuss.bjtuselfservice.shared.security.AndroidKeystoreCredentialVault

class SecuritySmokeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var result by remember { mutableStateOf("SECURITY_SMOKE_RUNNING") }
            LaunchedEffect(Unit) {
                result = runCatching {
                    val vault = AndroidKeystoreCredentialVault(applicationContext)
                    val fixture = Credentials("fixture-student", "fixture-password-安全")
                    vault.clear()
                    vault.save(fixture)
                    check(vault.load() == fixture)
                    vault.clear()
                    check(vault.load() == null)
                    "SECURITY_SMOKE_PASS"
                }.getOrElse {
                    "SECURITY_SMOKE_FAIL"
                }
            }
            MaterialTheme { Text(result) }
        }
    }
}
