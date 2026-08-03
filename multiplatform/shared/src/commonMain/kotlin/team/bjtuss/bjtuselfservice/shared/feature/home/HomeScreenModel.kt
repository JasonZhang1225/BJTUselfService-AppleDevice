package team.bjtuss.bjtuselfservice.shared.feature.home

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import team.bjtuss.bjtuselfservice.shared.data.home.HomeStatusFailure
import team.bjtuss.bjtuselfservice.shared.data.home.HomeStatusRefreshResult
import team.bjtuss.bjtuselfservice.shared.data.home.HomeStatusRepository
import team.bjtuss.bjtuselfservice.shared.domain.home.HomeStatus

data class HomeUiState(
    val status: HomeStatus? = null,
    val isRefreshing: Boolean = false,
    val failure: HomeStatusFailure? = null,
)

class HomeScreenModel(private val repository: HomeStatusRepository) {
    private val mutableState = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = mutableState.asStateFlow()

    suspend fun initialize() {
        if (mutableState.value.status == null) {
            mutableState.value = mutableState.value.copy(status = runCatching(repository::load).getOrNull())
            refresh()
        }
    }

    suspend fun refresh() {
        if (mutableState.value.isRefreshing) return
        mutableState.value = mutableState.value.copy(isRefreshing = true, failure = null)
        mutableState.value = when (val result = repository.refresh()) {
            is HomeStatusRefreshResult.Success -> HomeUiState(status = result.status)
            is HomeStatusRefreshResult.Failure -> HomeUiState(
                status = result.cached ?: mutableState.value.status,
                failure = result.reason,
            )
        }
    }
}
