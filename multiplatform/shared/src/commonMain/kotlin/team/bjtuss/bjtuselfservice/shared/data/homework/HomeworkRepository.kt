package team.bjtuss.bjtuselfservice.shared.data.homework

import kotlinx.coroutines.CancellationException
import team.bjtuss.bjtuselfservice.shared.cache.CacheStore
import team.bjtuss.bjtuselfservice.shared.domain.homework.Homework
import team.bjtuss.bjtuselfservice.shared.domain.homework.HomeworkAttachment
import team.bjtuss.bjtuselfservice.shared.domain.homework.HomeworkDetail
import team.bjtuss.bjtuselfservice.shared.domain.homework.HomeworkFileContent
import team.bjtuss.bjtuselfservice.shared.domain.homework.SubmittedHomeworkAttachment

data class HomeworkSnapshot(val homework: List<Homework>)

enum class HomeworkSyncFailure {
    NETWORK,
    SESSION_EXPIRED,
    MALFORMED_RESPONSE,
    SECURE_CHANNEL_UNAVAILABLE,
    CACHE,
}

sealed interface HomeworkRefreshResult {
    data class Success(val snapshot: HomeworkSnapshot) : HomeworkRefreshResult
    data class Failure(
        val snapshot: HomeworkSnapshot,
        val reason: HomeworkSyncFailure,
    ) : HomeworkRefreshResult
}

sealed interface HomeworkDetailResult {
    data class Success(val detail: HomeworkDetail) : HomeworkDetailResult
    data class Failure(val reason: HomeworkSyncFailure) : HomeworkDetailResult
}

sealed interface HomeworkOperationResult<out T> {
    data class Success<T>(val value: T) : HomeworkOperationResult<T>
    data class Failure(val reason: HomeworkSyncFailure) : HomeworkOperationResult<Nothing>
}

interface HomeworkLocalDataSource {
    fun load(accountScope: String): HomeworkSnapshot
    fun replace(accountScope: String, homework: List<Homework>)
}

class CacheStoreHomeworkLocalDataSource(
    private val cacheStore: CacheStore,
) : HomeworkLocalDataSource {
    override fun load(accountScope: String): HomeworkSnapshot =
        HomeworkSnapshot(cacheStore.homework(accountScope))

    override fun replace(accountScope: String, homework: List<Homework>) {
        cacheStore.replaceHomework(accountScope, homework)
    }
}

interface HomeworkRepository {
    fun load(): HomeworkSnapshot
    suspend fun refresh(): HomeworkRefreshResult
    suspend fun loadDetail(homework: Homework): HomeworkDetailResult
    suspend fun loadSubmittedAttachments(
        homework: Homework,
    ): HomeworkOperationResult<List<SubmittedHomeworkAttachment>>
    suspend fun downloadTeacherAttachment(
        homeworkId: Int,
        attachment: HomeworkAttachment,
    ): HomeworkOperationResult<HomeworkFileContent>
    suspend fun downloadSubmittedAttachment(
        attachment: SubmittedHomeworkAttachment,
    ): HomeworkOperationResult<HomeworkFileContent>
    suspend fun submitHomework(
        homework: Homework,
        content: String,
        files: List<HomeworkFileContent>,
    ): HomeworkOperationResult<Unit>
    fun attachmentDownloadUrl(homeworkId: Int, attachmentId: Int): String
}

class DefaultHomeworkRepository(
    accountScope: String,
    private val local: HomeworkLocalDataSource,
    private val remote: HomeworkRemoteDataSource,
) : HomeworkRepository {
    private val accountScope = accountScope.trim().also {
        require(it.isNotEmpty()) { "accountScope cannot be blank" }
    }

    override fun load(): HomeworkSnapshot = local.load(accountScope)

    override suspend fun refresh(): HomeworkRefreshResult {
        val fallback = runCatching(::load).getOrElse { HomeworkSnapshot(emptyList()) }
        val remoteHomework = try {
            remote.fetchHomework()
        } catch (error: CancellationException) {
            throw error
        } catch (error: HomeworkRemoteException) {
            return HomeworkRefreshResult.Failure(fallback, error.reason.toSyncFailure())
        } catch (_: Exception) {
            return HomeworkRefreshResult.Failure(fallback, HomeworkSyncFailure.NETWORK)
        }
        return try {
            local.replace(accountScope, remoteHomework)
            HomeworkRefreshResult.Success(local.load(accountScope))
        } catch (_: Exception) {
            HomeworkRefreshResult.Failure(
                snapshot = runCatching(::load).getOrElse { fallback },
                reason = HomeworkSyncFailure.CACHE,
            )
        }
    }

    override suspend fun loadDetail(homework: Homework): HomeworkDetailResult = try {
        HomeworkDetailResult.Success(remote.fetchDetail(homework))
    } catch (error: CancellationException) {
        throw error
    } catch (error: HomeworkRemoteException) {
        HomeworkDetailResult.Failure(error.reason.toSyncFailure())
    } catch (_: Exception) {
        HomeworkDetailResult.Failure(HomeworkSyncFailure.NETWORK)
    }

    override suspend fun loadSubmittedAttachments(
        homework: Homework,
    ): HomeworkOperationResult<List<SubmittedHomeworkAttachment>> = remoteOperation {
        remote.fetchSubmittedAttachments(homework)
    }

    override suspend fun downloadTeacherAttachment(
        homeworkId: Int,
        attachment: HomeworkAttachment,
    ): HomeworkOperationResult<HomeworkFileContent> = remoteOperation {
        remote.downloadTeacherAttachment(homeworkId, attachment)
    }

    override suspend fun downloadSubmittedAttachment(
        attachment: SubmittedHomeworkAttachment,
    ): HomeworkOperationResult<HomeworkFileContent> = remoteOperation {
        remote.downloadSubmittedAttachment(attachment)
    }

    override suspend fun submitHomework(
        homework: Homework,
        content: String,
        files: List<HomeworkFileContent>,
    ): HomeworkOperationResult<Unit> {
        if (files.isEmpty()) return HomeworkOperationResult.Failure(HomeworkSyncFailure.MALFORMED_RESPONSE)
        return remoteOperation { remote.submitHomework(homework, content, files) }
    }

    override fun attachmentDownloadUrl(homeworkId: Int, attachmentId: Int): String =
        remote.attachmentDownloadUrl(homeworkId, attachmentId)

    private suspend fun <T> remoteOperation(block: suspend () -> T): HomeworkOperationResult<T> = try {
        HomeworkOperationResult.Success(block())
    } catch (error: CancellationException) {
        throw error
    } catch (error: HomeworkRemoteException) {
        HomeworkOperationResult.Failure(error.reason.toSyncFailure())
    } catch (_: Exception) {
        HomeworkOperationResult.Failure(HomeworkSyncFailure.NETWORK)
    }
}

private fun HomeworkRemoteFailure.toSyncFailure(): HomeworkSyncFailure = when (this) {
    HomeworkRemoteFailure.NETWORK -> HomeworkSyncFailure.NETWORK
    HomeworkRemoteFailure.SESSION_EXPIRED -> HomeworkSyncFailure.SESSION_EXPIRED
    HomeworkRemoteFailure.MALFORMED_RESPONSE -> HomeworkSyncFailure.MALFORMED_RESPONSE
    HomeworkRemoteFailure.SECURE_CHANNEL_UNAVAILABLE -> HomeworkSyncFailure.SECURE_CHANNEL_UNAVAILABLE
}
