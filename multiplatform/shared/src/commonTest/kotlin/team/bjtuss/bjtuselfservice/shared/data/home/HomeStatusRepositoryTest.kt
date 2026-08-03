package team.bjtuss.bjtuselfservice.shared.data.home

import kotlinx.coroutines.runBlocking
import team.bjtuss.bjtuselfservice.shared.domain.home.HomeStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class HomeStatusRepositoryTest {
    private val cached = HomeStatus("0", "40", "8")
    private val fresh = HomeStatus("2", "35", "7")

    @Test fun successfulRefreshReplacesCache() {
        runBlocking {
            val local = FakeLocal(cached)
            val result = DefaultHomeStatusRepository("account", local, FakeRemote(fresh)).refresh()
            assertEquals(fresh, assertIs<HomeStatusRefreshResult.Success>(result).status)
            assertEquals(fresh, local.value)
        }
    }

    @Test fun remoteFailureKeepsCachedStatus() {
        runBlocking {
            val result = DefaultHomeStatusRepository(
                "account",
                FakeLocal(cached),
                FakeRemote(error = HomeStatusRemoteException(HomeStatusFailure.NETWORK)),
            ).refresh()
            assertEquals(cached, assertIs<HomeStatusRefreshResult.Failure>(result).cached)
        }
    }

    @Test fun cacheWriteFailureDoesNotReportFreshAsStored() {
        runBlocking {
            val result = DefaultHomeStatusRepository(
                "account",
                FakeLocal(cached, failWrite = true),
                FakeRemote(fresh),
            ).refresh()
            val failed = assertIs<HomeStatusRefreshResult.Failure>(result)
            assertEquals(HomeStatusFailure.CACHE, failed.reason)
            assertEquals(cached, failed.cached)
        }
    }
}

private class FakeLocal(
    var value: HomeStatus?,
    private val failWrite: Boolean = false,
) : HomeStatusLocalDataSource {
    override fun load(accountScope: String): HomeStatus? = value
    override fun replace(accountScope: String, status: HomeStatus) {
        if (failWrite) error("disk")
        value = status
    }
}

private class FakeRemote(
    private val status: HomeStatus? = null,
    private val error: HomeStatusRemoteException? = null,
) : HomeStatusRemoteDataSource {
    override suspend fun fetch(): HomeStatus = error?.let { throw it } ?: requireNotNull(status)
}
