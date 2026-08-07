package team.bjtuss.bjtuselfservice.shared.feature.settings

import kotlinx.coroutines.runBlocking
import team.bjtuss.bjtuselfservice.shared.cache.AppPreferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SettingsScreenModelTest {
    @Test
    fun autoSyncOptionsPersistAndUpdateVisiblePreferences() {
        val saved = mutableListOf<AppPreferences>()
        val model = SettingsScreenModel(
            initialPreferences = AppPreferences(),
            persistPreferences = { saved += it; true },
            clearAccountCache = { true },
        )

        model.setAutoSyncGrades(true)
        model.setAutoSyncHomework(true)
        model.setAutoSyncSchedule(true)
        model.setAutoSyncExams(true)

        assertEquals(4, saved.size)
        assertTrue(model.state.value.preferences.autoSyncGrades)
        assertTrue(model.state.value.preferences.autoSyncHomework)
        assertTrue(model.state.value.preferences.autoSyncSchedule)
        assertTrue(model.state.value.preferences.autoSyncExams)
        assertFalse(model.state.value.saveFailed)
    }

    @Test
    fun dynamicColorTogglePersists() {
        val saved = mutableListOf<AppPreferences>()
        val model = SettingsScreenModel(
            initialPreferences = AppPreferences(dynamicColor = true),
            persistPreferences = { saved += it; true },
            clearAccountCache = { true },
        )

        model.setDynamicColor(false)

        assertEquals(1, saved.size)
        assertFalse(model.state.value.preferences.dynamicColor)
        assertFalse(model.state.value.saveFailed)
    }

    @Test
    fun failedAutoSyncSaveKeepsPreviousValueAndReportsFailure() {
        val model = SettingsScreenModel(
            initialPreferences = AppPreferences(),
            persistPreferences = { false },
            clearAccountCache = { true },
        )

        model.setAutoSyncSchedule(false)

        assertTrue(model.state.value.preferences.autoSyncSchedule)
        assertTrue(model.state.value.saveFailed)
    }

    @Test
    fun successfulCacheClearReportsSuccessAndCanBeDismissed() {
        runBlocking {
            var calls = 0
            val model = SettingsScreenModel(
                initialPreferences = AppPreferences(),
                persistPreferences = { true },
                clearAccountCache = {
                    calls += 1
                    true
                },
            )

            model.clearOfflineCache()

            assertEquals(1, calls)
            assertIs<OfflineCacheActionState.Cleared>(model.state.value.cacheAction)
            model.dismissFeedback()
            assertIs<OfflineCacheActionState.Idle>(model.state.value.cacheAction)
        }
    }

    @Test
    fun cacheClearExceptionReportsFailure() {
        runBlocking {
            val model = SettingsScreenModel(
                initialPreferences = AppPreferences(),
                persistPreferences = { true },
                clearAccountCache = { error("database") },
            )

            model.clearOfflineCache()

            assertIs<OfflineCacheActionState.Failed>(model.state.value.cacheAction)
        }
    }
}
