package team.bjtuss.bjtuselfservice.shared.feature.home

import kotlin.test.Test
import kotlin.test.assertEquals

class HomeSyncStatusTest {
    @Test
    fun loginTakesPriorityOverSyncInDialogTitle() {
        assertEquals(
            "登录中",
            homeSyncDialogTitle(isLoggingIn = true, isSyncing = true, hasFailures = true),
        )
    }

    @Test
    fun syncTakesPriorityOverFailureWhileOtherModulesAreStillRunning() {
        assertEquals(
            "同步中",
            homeSyncDialogTitle(isLoggingIn = false, isSyncing = true, hasFailures = true),
        )
    }

    @Test
    fun failureIsShownOnlyAfterAllActiveSyncWorkStops() {
        assertEquals(
            "同步失败",
            homeSyncDialogTitle(isLoggingIn = false, isSyncing = false, hasFailures = true),
        )
    }
}
