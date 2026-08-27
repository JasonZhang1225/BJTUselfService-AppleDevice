package team.bjtuss.bjtuselfservice.shared.data.phyvlab

import kotlinx.coroutines.CancellationException
import team.bjtuss.bjtuselfservice.shared.domain.phyvlab.PhyVlabActivity
import team.bjtuss.bjtuselfservice.shared.domain.phyvlab.PhyVlabAssignmentDetail
import team.bjtuss.bjtuselfservice.shared.domain.phyvlab.PhyVlabCourse
import team.bjtuss.bjtuselfservice.shared.domain.phyvlab.PhyVlabEvent
import team.bjtuss.bjtuselfservice.shared.domain.homework.HomeworkFileContent

enum class PhyVlabSyncFailure {
    NETWORK,
    PARSE,
    SESSION_EXPIRED,
}

sealed interface PhyVlabCoursesResult {
    data class Success(val courses: List<PhyVlabCourse>) : PhyVlabCoursesResult
    data class Failure(val reason: PhyVlabSyncFailure) : PhyVlabCoursesResult
}

sealed interface PhyVlabActivitiesResult {
    data class Success(val activities: List<PhyVlabActivity>) : PhyVlabActivitiesResult
    data class Failure(val reason: PhyVlabSyncFailure) : PhyVlabActivitiesResult
}

sealed interface PhyVlabEventsResult {
    data class Success(val events: List<PhyVlabEvent>) : PhyVlabEventsResult
    data class Failure(val reason: PhyVlabSyncFailure) : PhyVlabEventsResult
}

sealed interface PhyVlabAssignmentDetailResult {
    data class Success(val detail: PhyVlabAssignmentDetail) : PhyVlabAssignmentDetailResult
    data class Failure(val reason: PhyVlabSyncFailure) : PhyVlabAssignmentDetailResult
}

sealed interface PhyVlabSubmissionResult {
    data object Success : PhyVlabSubmissionResult
    data class Failure(val reason: PhyVlabSyncFailure) : PhyVlabSubmissionResult
}

interface PhyVlabRepository {
    suspend fun fetchCourses(): PhyVlabCoursesResult
    suspend fun fetchCourseActivities(course: PhyVlabCourse): PhyVlabActivitiesResult
    suspend fun fetchEvents(monthTimestampSeconds: Long): PhyVlabEventsResult
    suspend fun fetchAssignmentDetail(activity: PhyVlabActivity): PhyVlabAssignmentDetailResult
    suspend fun submitAssignment(
        activity: PhyVlabActivity,
        files: List<HomeworkFileContent>,
    ): PhyVlabSubmissionResult
}

class DefaultPhyVlabRepository(
    private val remote: PhyVlabRemoteDataSource,
) : PhyVlabRepository {
    override suspend fun fetchCourses(): PhyVlabCoursesResult = try {
        PhyVlabCoursesResult.Success(remote.fetchCourses())
    } catch (error: CancellationException) {
        throw error
    } catch (error: PhyVlabRemoteException) {
        PhyVlabCoursesResult.Failure(error.reason.toSyncFailure())
    } catch (_: Exception) {
        PhyVlabCoursesResult.Failure(PhyVlabSyncFailure.NETWORK)
    }

    override suspend fun fetchCourseActivities(course: PhyVlabCourse): PhyVlabActivitiesResult = try {
        PhyVlabActivitiesResult.Success(remote.fetchCourseActivities(course))
    } catch (error: CancellationException) {
        throw error
    } catch (error: PhyVlabRemoteException) {
        PhyVlabActivitiesResult.Failure(error.reason.toSyncFailure())
    } catch (_: Exception) {
        PhyVlabActivitiesResult.Failure(PhyVlabSyncFailure.NETWORK)
    }

    override suspend fun fetchEvents(monthTimestampSeconds: Long): PhyVlabEventsResult = try {
        PhyVlabEventsResult.Success(remote.fetchEvents(monthTimestampSeconds))
    } catch (error: CancellationException) {
        throw error
    } catch (error: PhyVlabRemoteException) {
        PhyVlabEventsResult.Failure(error.reason.toSyncFailure())
    } catch (_: Exception) {
        PhyVlabEventsResult.Failure(PhyVlabSyncFailure.NETWORK)
    }

    override suspend fun fetchAssignmentDetail(activity: PhyVlabActivity): PhyVlabAssignmentDetailResult = try {
        PhyVlabAssignmentDetailResult.Success(remote.fetchAssignmentDetail(activity))
    } catch (error: CancellationException) {
        throw error
    } catch (error: PhyVlabRemoteException) {
        PhyVlabAssignmentDetailResult.Failure(error.reason.toSyncFailure())
    } catch (_: Exception) {
        PhyVlabAssignmentDetailResult.Failure(PhyVlabSyncFailure.NETWORK)
    }

    override suspend fun submitAssignment(
        activity: PhyVlabActivity,
        files: List<HomeworkFileContent>,
    ): PhyVlabSubmissionResult = try {
        remote.submitAssignment(activity, files)
        PhyVlabSubmissionResult.Success
    } catch (error: CancellationException) {
        throw error
    } catch (error: PhyVlabRemoteException) {
        PhyVlabSubmissionResult.Failure(error.reason.toSyncFailure())
    } catch (_: Exception) {
        PhyVlabSubmissionResult.Failure(PhyVlabSyncFailure.NETWORK)
    }
}

private fun PhyVlabRemoteFailure.toSyncFailure(): PhyVlabSyncFailure = when (this) {
    PhyVlabRemoteFailure.NETWORK -> PhyVlabSyncFailure.NETWORK
    PhyVlabRemoteFailure.PARSE -> PhyVlabSyncFailure.PARSE
    PhyVlabRemoteFailure.SESSION_EXPIRED -> PhyVlabSyncFailure.SESSION_EXPIRED
}
