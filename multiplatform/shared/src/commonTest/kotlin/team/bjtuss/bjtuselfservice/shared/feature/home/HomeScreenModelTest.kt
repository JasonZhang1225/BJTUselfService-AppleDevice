package team.bjtuss.bjtuselfservice.shared.feature.home

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import team.bjtuss.bjtuselfservice.shared.data.home.HomeStatusFailure
import team.bjtuss.bjtuselfservice.shared.data.home.HomeStatusRefreshResult
import team.bjtuss.bjtuselfservice.shared.data.home.HomeStatusRepository
import team.bjtuss.bjtuselfservice.shared.domain.home.HomeStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomeScreenModelTest {
    @Test
    fun initializePublishesFreshStatus() {
        runBlocking {
            val fresh = HomeStatus("4", "50", "6")
            val model = HomeScreenModel(FakeRepository(null, HomeStatusRefreshResult.Success(fresh)))
            model.initialize()
            assertEquals(fresh, model.state.value.status)
            assertFalse(model.state.value.isRefreshing)
        }
    }

    @Test
    fun failedRefreshKeepsPreviouslyLoadedStatus() {
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

    @Test
    fun cancelledRefreshClearsIsRefreshingSoLaterRefreshCanRun() = runBlocking {
        val hangGate = CompletableDeferred<Unit>()
        val repo = ControllableRepository(
            cached = HomeStatus("1", "2", "3"),
            hangGate = hangGate,
            result = HomeStatusRefreshResult.Success(HomeStatus("1", "2", "3")),
        )
        val model = HomeScreenModel(repo)

        // initialize 会触发一次 refresh：先放行让它结束。
        hangGate.complete(Unit)
        model.initialize()
        assertFalse(model.state.value.isRefreshing)

        // 第二次 refresh 挂起在仓库里，模拟切后台后协程被取消。
        val secondGate = CompletableDeferred<Unit>()
        repo.hangGate = secondGate
        val job = async { model.refresh() }
        while (!model.state.value.isRefreshing) yield()
        assertTrue(model.state.value.isRefreshing)
        job.cancel()
        job.join()

        assertFalse(model.state.value.isRefreshing, "取消后不能卡在同步中")

        // 旧实现用 isRefreshing 当互斥会永久卡死；这里必须还能再刷新成功。
        repo.hangGate = null
        repo.result = HomeStatusRefreshResult.Success(HomeStatus("9", "8", "7"))
        model.refresh()
        assertEquals("9", model.state.value.status?.newMailCount)
        assertFalse(model.state.value.isRefreshing)
    }
}

private class FakeRepository(
    private val cached: HomeStatus?,
    private val result: HomeStatusRefreshResult,
) : HomeStatusRepository {
    override fun load(): HomeStatus? = cached
    override suspend fun refresh(): HomeStatusRefreshResult = result
}

private class ControllableRepository(
    private val cached: HomeStatus?,
    var hangGate: CompletableDeferred<Unit>?,
    var result: HomeStatusRefreshResult,
) : HomeStatusRepository {
    override fun load(): HomeStatus? = cached
    override suspend fun refresh(): HomeStatusRefreshResult {
        hangGate?.await()
        return result
    }
}
