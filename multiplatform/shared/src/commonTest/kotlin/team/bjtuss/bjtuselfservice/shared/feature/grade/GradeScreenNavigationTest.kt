package team.bjtuss.bjtuselfservice.shared.feature.grade

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GradeScreenNavigationTest {
    @Test
    fun mailboxAndClassroomRootsPushNativelyFromMore() {
        assertTrue(shouldOpenNativeSectionRoute("MAILBOX", useNativeSecondaryRoutes = true))
        assertTrue(shouldOpenNativeSectionRoute("CLASSROOMS", useNativeSecondaryRoutes = true))
        assertTrue(shouldOpenNativeSectionRoute("CLASSROOM_OCCUPANCY", useNativeSecondaryRoutes = true))
        assertTrue(shouldOpenNativeSectionRoute("EXAMS", useNativeSecondaryRoutes = true))
        assertFalse(shouldOpenNativeSectionRoute("MAILBOX", useNativeSecondaryRoutes = false))
        assertFalse(shouldOpenNativeSectionRoute("EXAMS", useNativeSecondaryRoutes = false))
    }

    @Test
    fun inlineMailboxDetailUsesTheTopBarBackAction() {
        assertEquals(
            MailboxBackTarget.LIST,
            mailboxBackTarget(
                expanded = false,
                useNativeSecondaryRoutes = false,
                hasSelectedMessage = true,
                isMessageLoading = false,
            ),
        )
        assertEquals(
            MailboxBackTarget.LIST,
            mailboxBackTarget(
                expanded = false,
                useNativeSecondaryRoutes = false,
                hasSelectedMessage = false,
                isMessageLoading = true,
            ),
        )
    }

    @Test
    fun mailboxRootAndNativeDetailKeepTheParentBackAction() {
        assertEquals(
            MailboxBackTarget.PARENT,
            mailboxBackTarget(
                expanded = false,
                useNativeSecondaryRoutes = false,
                hasSelectedMessage = false,
                isMessageLoading = false,
            ),
        )
        assertEquals(
            MailboxBackTarget.PARENT,
            mailboxBackTarget(
                expanded = false,
                useNativeSecondaryRoutes = true,
                hasSelectedMessage = true,
                isMessageLoading = false,
            ),
        )
        assertEquals(
            MailboxBackTarget.PARENT,
            mailboxBackTarget(
                expanded = true,
                useNativeSecondaryRoutes = false,
                hasSelectedMessage = true,
                isMessageLoading = false,
            ),
        )
    }
}
