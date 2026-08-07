package team.bjtuss.bjtuselfservice.shared.data.classroomoccupancy

import kotlinx.coroutines.CancellationException
import team.bjtuss.bjtuselfservice.shared.domain.classroomoccupancy.ClassroomOccupancy
import team.bjtuss.bjtuselfservice.shared.domain.classroomoccupancy.OccupancySemester
import team.bjtuss.bjtuselfservice.shared.domain.classroomoccupancy.OccupancyWeekDate

enum class ClassroomOccupancySyncFailure {
    NETWORK,
    SESSION_EXPIRED,
    MALFORMED_RESPONSE,
}

sealed interface ClassroomOccupancyResult {
    /**
     * @param semesterOptions 同页 zxjxjhh 下拉（可能为 null）；成功占用查询时顺带带回，
     * 避免单独预取学期失败后弹层只剩「当前学期」。
     */
    data class Success(
        val rooms: List<ClassroomOccupancy>,
        val semesterOptions: SemesterOptions? = null,
    ) : ClassroomOccupancyResult
    data class Failure(val reason: ClassroomOccupancySyncFailure) : ClassroomOccupancyResult
}

/** 教室占用无本地缓存：按筛选条件即查即弃，不落盘。 */
interface ClassroomOccupancyRepository {
    suspend fun fetchOccupancy(week: Int, buildingId: String, semesterId: String?): ClassroomOccupancyResult

    /** 学期下拉；失败静默返回空清单（弹层只剩“当前学期”一项）。 */
    suspend fun fetchSemesters(): SemesterOptions

    /** 校历周日期；失败静默返回空 Map（弹层不显示日期，仅此而已）。 */
    suspend fun fetchWeekDates(): Map<String, List<OccupancyWeekDate>>
}

class DefaultClassroomOccupancyRepository(
    private val remote: ClassroomOccupancyRemoteDataSource,
) : ClassroomOccupancyRepository {

    override suspend fun fetchOccupancy(
        week: Int,
        buildingId: String,
        semesterId: String?,
    ): ClassroomOccupancyResult = try {
        val payload = remote.fetchOccupancy(week, buildingId, semesterId)
        ClassroomOccupancyResult.Success(
            rooms = payload.rooms,
            semesterOptions = payload.semesterOptions,
        )
    } catch (error: CancellationException) {
        throw error
    } catch (error: ClassroomOccupancyRemoteException) {
        ClassroomOccupancyResult.Failure(error.reason.toSyncFailure())
    } catch (_: Exception) {
        ClassroomOccupancyResult.Failure(ClassroomOccupancySyncFailure.NETWORK)
    }

    override suspend fun fetchSemesters(): SemesterOptions = try {
        remote.fetchSemesters()
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        SemesterOptions(selected = null, all = emptyList())
    }

    override suspend fun fetchWeekDates(): Map<String, List<OccupancyWeekDate>> = try {
        remote.fetchWeekDates()
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        emptyMap()
    }
}

private fun ClassroomOccupancyRemoteFailure.toSyncFailure(): ClassroomOccupancySyncFailure = when (this) {
    ClassroomOccupancyRemoteFailure.NETWORK -> ClassroomOccupancySyncFailure.NETWORK
    ClassroomOccupancyRemoteFailure.SESSION_EXPIRED -> ClassroomOccupancySyncFailure.SESSION_EXPIRED
    ClassroomOccupancyRemoteFailure.MALFORMED_RESPONSE -> ClassroomOccupancySyncFailure.MALFORMED_RESPONSE
}
