package team.bjtuss.bjtuselfservice.shared

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SmartPlatformPolicyTest {
    @Test
    fun enablesOnlyAppleLegacyTransport() {
        assertTrue(usesLegacySmartTransportFor(PlatformFamily.IOS))
        assertTrue(usesLegacySmartTransportFor(PlatformFamily.MacOS))
        assertFalse(usesLegacySmartTransportFor(PlatformFamily.Android))
    }
}
