package team.bjtuss.bjtuselfservice.shared.feature.grade

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GradeScreenNavigationTest {
    @Test
    fun inlineMailboxDetailOwnsTheOnlyBackAction() {
        assertFalse(
            shouldShowMailboxRootBack(
                expanded = false,
                useNativeSecondaryRoutes = false,
                hasSelectedMessage = true,
                isMessageLoading = false,
            ),
        )
        assertFalse(
            shouldShowMailboxRootBack(
                expanded = false,
                useNativeSecondaryRoutes = false,
                hasSelectedMessage = false,
                isMessageLoading = true,
            ),
        )
    }

    @Test
    fun mailboxRootAndNativeDetailKeepThePlatformBackAction() {
        assertTrue(
            shouldShowMailboxRootBack(
                expanded = false,
                useNativeSecondaryRoutes = false,
                hasSelectedMessage = false,
                isMessageLoading = false,
            ),
        )
        assertTrue(
            shouldShowMailboxRootBack(
                expanded = false,
                useNativeSecondaryRoutes = true,
                hasSelectedMessage = true,
                isMessageLoading = false,
            ),
        )
        assertTrue(
            shouldShowMailboxRootBack(
                expanded = true,
                useNativeSecondaryRoutes = false,
                hasSelectedMessage = true,
                isMessageLoading = false,
            ),
        )
    }
}
