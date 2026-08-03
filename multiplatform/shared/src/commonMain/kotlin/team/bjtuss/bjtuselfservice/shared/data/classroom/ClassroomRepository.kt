package team.bjtuss.bjtuselfservice.shared.data.classroom

import kotlinx.coroutines.CancellationException
import team.bjtuss.bjtuselfservice.shared.domain.classroom.ClassroomBuildingInfo

enum class ClassroomFetchFailure {
    NETWORK,
    PARSE,
    SECURE_CHANNEL_UNAVAILABLE,
}

sealed interface ClassroomFetchResult {
    data class Success(val info: ClassroomBuildingInfo) : ClassroomFetchResult
    data class Failure(val reason: ClassroomFetchFailure) : ClassroomFetchResult
}

interface ClassroomRepository {
    suspend fun fetchBuildingInfo(buildingName: String): ClassroomFetchResult
}

/**
 * 教室人数评估仓库。接口是公开第三方快照，不做本地缓存：
 * 数据是服务器最近一次轮询窗口的实时人数，缓存旧快照没有意义，
 * 与原 App 每次进入页面直接请求的行为一致。
 */
class DefaultClassroomRepository(
    private val remote: ClassroomRemoteDataSource,
) : ClassroomRepository {

    override suspend fun fetchBuildingInfo(buildingName: String): ClassroomFetchResult = try {
        ClassroomFetchResult.Success(remote.fetchBuildingInfo(buildingName))
    } catch (error: CancellationException) {
        throw error
    } catch (error: ClassroomRemoteException) {
        ClassroomFetchResult.Failure(error.reason.toFetchFailure())
    } catch (_: Exception) {
        ClassroomFetchResult.Failure(ClassroomFetchFailure.NETWORK)
    }
}

private fun ClassroomRemoteFailure.toFetchFailure(): ClassroomFetchFailure = when (this) {
    ClassroomRemoteFailure.NETWORK -> ClassroomFetchFailure.NETWORK
    ClassroomRemoteFailure.PARSE -> ClassroomFetchFailure.PARSE
    ClassroomRemoteFailure.SECURE_CHANNEL_UNAVAILABLE ->
        ClassroomFetchFailure.SECURE_CHANNEL_UNAVAILABLE
}
