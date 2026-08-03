package team.bjtuss.bjtuselfservice.shared.network

import java.nio.charset.Charset

internal actual fun decodeLegacyGb18030OrNull(bytes: ByteArray): String? = runCatching {
    String(bytes, Charset.forName("GB18030"))
}.getOrNull()
