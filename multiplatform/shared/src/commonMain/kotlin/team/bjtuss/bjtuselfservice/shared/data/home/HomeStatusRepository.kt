package team.bjtuss.bjtuselfservice.shared.data.home

import kotlinx.coroutines.CancellationException
import team.bjtuss.bjtuselfservice.shared.cache.CacheStore
import team.bjtuss.bjtuselfservice.shared.domain.home.HomeStatus

private const val HOME_STATUS_CACHE_KEY = "home_status_v1"

enum class HomeStatusFailure { NETWORK, SESSION_EXPIRED, PARSE, CACHE }

sealed interface HomeStatusRefreshResult {
    data class Success(val status: HomeStatus) : HomeStatusRefreshResult
    data class Failure(val cached: HomeStatus?, val reason: HomeStatusFailure) : HomeStatusRefreshResult
}

interface HomeStatusLocalDataSource {
    fun load(accountScope: String): HomeStatus?
    fun replace(accountScope: String, status: HomeStatus)
}

class CacheStoreHomeStatusLocalDataSource(private val cacheStore: CacheStore) : HomeStatusLocalDataSource {
    override fun load(accountScope: String): HomeStatus? =
        cacheStore.metadata(accountScope, HOME_STATUS_CACHE_KEY)?.let(::decodeHomeStatus)

    override fun replace(accountScope: String, status: HomeStatus) {
        cacheStore.putMetadata(accountScope, HOME_STATUS_CACHE_KEY, encodeHomeStatus(status))
    }
}

interface HomeStatusRemoteDataSource {
    suspend fun fetch(): HomeStatus
}

interface HomeStatusRepository {
    fun load(): HomeStatus?
    suspend fun refresh(): HomeStatusRefreshResult
}

class DefaultHomeStatusRepository(
    private val accountScope: String,
    private val local: HomeStatusLocalDataSource,
    private val remote: HomeStatusRemoteDataSource,
) : HomeStatusRepository {
    override fun load(): HomeStatus? = local.load(accountScope)

    override suspend fun refresh(): HomeStatusRefreshResult {
        val cached = runCatching(::load).getOrNull()
        val fresh = try {
            remote.fetch()
        } catch (error: CancellationException) {
            throw error
        } catch (error: HomeStatusRemoteException) {
            return HomeStatusRefreshResult.Failure(cached, error.reason)
        } catch (_: Exception) {
            return HomeStatusRefreshResult.Failure(cached, HomeStatusFailure.NETWORK)
        }
        return try {
            local.replace(accountScope, fresh)
            HomeStatusRefreshResult.Success(fresh)
        } catch (_: Exception) {
            HomeStatusRefreshResult.Failure(cached, HomeStatusFailure.CACHE)
        }
    }
}

private fun encodeHomeStatus(status: HomeStatus): String = listOf(
    status.newMailCount,
    status.campusCardBalance,
    status.networkBalance,
).joinToString(separator = "") { value -> "${value.length}:$value" }

private fun decodeHomeStatus(encoded: String): HomeStatus? {
    var index = 0
    fun readPart(): String? {
        val colon = encoded.indexOf(':', index).takeIf { it >= index } ?: return null
        val length = encoded.substring(index, colon).toIntOrNull()?.takeIf { it >= 0 } ?: return null
        val start = colon + 1
        val end = start + length
        if (end > encoded.length) return null
        index = end
        return encoded.substring(start, end)
    }
    val mail = readPart() ?: return null
    val card = readPart() ?: return null
    val network = readPart() ?: return null
    if (index != encoded.length) return null
    return HomeStatus(mail, card, network)
}
