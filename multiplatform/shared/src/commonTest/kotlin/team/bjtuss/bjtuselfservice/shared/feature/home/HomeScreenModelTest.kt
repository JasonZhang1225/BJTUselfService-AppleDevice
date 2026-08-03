package team.bjtuss.bjtuselfservice.shared.feature.home

import kotlinx.coroutines.runBlocking
import team.bjtuss.bjtuselfservice.shared.data.home.HomeStatusFailure
import team.bjtuss.bjtuselfservice.shared.data.home.HomeStatusRefreshResult
import team.bjtuss.bjtuselfservice.shared.data.home.HomeStatusRepository
import team.bjtuss.bjtuselfservice.shared.domain.home.HomeStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class HomeScreenModelTest {
    @Test fun initializePublishesFreshStatus() {
        runBlocking {
            val fresh = HomeStatus("4", "50", "6")
            val model = HomeScreenModel(FakeRepository(null, HomeStatusRefreshResult.Success(fresh)))
            model.initialize()
            assertEquals(fresh, model.state.value.status)
            assertFalse(model.state.value.isRefreshing)
        }
    }

    @Test fun failedRefreshKeepsPreviouslyLoadedStatus() {
        runBlocking {
            val cached = HomeStatus("0", "30", "5")
            val model = HomeScreenModel(
                FakeRepository(cached, HomeStatusRefreshResult.Failure(cached, HomeStatusFailure.NETWORK)),
            )
            model.initialize()
            assertEquals(cached, model.state.value.status)
            assertEquals(HomeStatusFailure.NETWORK, model.state.value.failure)
        }
    }
}

private class FakeRepository(
    private val cached: HomeStatus?,
    private val result: HomeStatusRefreshResult,
) : HomeStatusRepository {
    override fun load(): HomeStatus? = cached
    override suspend fun refresh(): HomeStatusRefreshResult = result
}
