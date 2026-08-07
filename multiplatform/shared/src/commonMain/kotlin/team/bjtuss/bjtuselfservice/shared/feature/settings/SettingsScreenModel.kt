package team.bjtuss.bjtuselfservice.shared.feature.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import team.bjtuss.bjtuselfservice.shared.cache.AppPreferences

sealed interface OfflineCacheActionState {
    data object Idle : OfflineCacheActionState
    data object Clearing : OfflineCacheActionState
    data object Cleared : OfflineCacheActionState
    data object Failed : OfflineCacheActionState
}

data class SettingsUiState(
    val preferences: AppPreferences,
    val cacheAction: OfflineCacheActionState = OfflineCacheActionState.Idle,
    val saveFailed: Boolean = false,
)

/**
 * 设置状态只保存普通偏好；凭据与 Cookie 仍由现有安全退出路径管理。
 * 浅深色始终跟随系统；[AppPreferences.dynamicColor] 仅 Android 设置页暴露开关。
 */
class SettingsScreenModel(
    initialPreferences: AppPreferences,
    private val persistPreferences: (AppPreferences) -> Boolean,
    private val clearAccountCache: () -> Boolean,
) {
    private val mutableState = MutableStateFlow(SettingsUiState(initialPreferences))
    val state: StateFlow<SettingsUiState> = mutableState.asStateFlow()

    fun setAutoSyncGrades(enabled: Boolean) = updatePreferences {
        copy(autoSyncGrades = enabled)
    }

    fun setAutoSyncHomework(enabled: Boolean) = updatePreferences {
        copy(autoSyncHomework = enabled)
    }

    fun setAutoSyncSchedule(enabled: Boolean) = updatePreferences {
        copy(autoSyncSchedule = enabled)
    }

    fun setAutoSyncExams(enabled: Boolean) = updatePreferences {
        copy(autoSyncExams = enabled)
    }

    /** Android Material You 动态取色；其它平台设置页不展示，即使写入也无视觉效果。 */
    fun setDynamicColor(enabled: Boolean) = updatePreferences {
        copy(dynamicColor = enabled)
    }

    suspend fun clearOfflineCache() {
        if (mutableState.value.cacheAction == OfflineCacheActionState.Clearing) return
        mutableState.value = mutableState.value.copy(cacheAction = OfflineCacheActionState.Clearing)
        mutableState.value = mutableState.value.copy(
            cacheAction = if (runCatching(clearAccountCache).getOrDefault(false)) {
                OfflineCacheActionState.Cleared
            } else {
                OfflineCacheActionState.Failed
            },
        )
    }

    fun dismissFeedback() {
        mutableState.value = mutableState.value.copy(
            cacheAction = OfflineCacheActionState.Idle,
            saveFailed = false,
        )
    }

    private fun updatePreferences(transform: AppPreferences.() -> AppPreferences) {
        val updated = mutableState.value.preferences.transform()
        val saved = runCatching { persistPreferences(updated) }.getOrDefault(false)
        mutableState.value = mutableState.value.copy(
            preferences = if (saved) updated else mutableState.value.preferences,
            saveFailed = !saved,
        )
    }
}
