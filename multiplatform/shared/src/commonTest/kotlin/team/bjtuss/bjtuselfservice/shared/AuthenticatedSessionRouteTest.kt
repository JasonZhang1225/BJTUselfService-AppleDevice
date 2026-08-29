package team.bjtuss.bjtuselfservice.shared

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AuthenticatedSessionRouteTest {
    @Test
    fun mailboxDetailIsAPlatformNativeRoute() {
        assertTrue(isNativeDetailRoute("MAILBOX_DETAIL"))
        assertTrue(isNativeDetailRoute("MAILBOX"))
        assertFalse(isNativeDetailRoute("UNKNOWN_ROUTE"))
    }
}
