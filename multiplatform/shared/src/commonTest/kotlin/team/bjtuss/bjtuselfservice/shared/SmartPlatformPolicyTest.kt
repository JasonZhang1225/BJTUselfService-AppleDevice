package team.bjtuss.bjtuselfservice.shared

import kotlin.test.Test
import kotlin.test.assertTrue

class SmartPlatformPolicyTest {
    @Test
    fun enablesLegacyTransportOnAllPlatforms() {
        assertTrue(usesLegacySmartTransportFor(PlatformFamily.IOS))
        assertTrue(usesLegacySmartTransportFor(PlatformFamily.MacOS))
        assertTrue(usesLegacySmartTransportFor(PlatformFamily.Android))
    }
}
