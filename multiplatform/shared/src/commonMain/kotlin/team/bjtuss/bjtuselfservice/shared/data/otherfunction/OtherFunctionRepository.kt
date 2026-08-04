package team.bjtuss.bjtuselfservice.shared.data.otherfunction

import kotlinx.coroutines.CancellationException
import team.bjtuss.bjtuselfservice.shared.domain.homework.HomeworkFileContent
import team.bjtuss.bjtuselfservice.shared.domain.otherfunction.ReportCardLanguage

enum class OtherFunctionSyncFailure {
    NETWORK,
    PARSE,
    SESSION_EXPIRED,
}

sealed interface OtherFunctionDownloadResult {
    data class Success(val file: HomeworkFileContent) : OtherFunctionDownloadResult
    data class Failure(val reason: OtherFunctionSyncFailure) : OtherFunctionDownloadResult
}

interface OtherFunctionRepository {
    suspend fun downloadCalendar(): OtherFunctionDownloadResult
    suspend fun downloadReportCard(language: ReportCardLanguage): OtherFunctionDownloadResult
    /** 校历页解析出的最新文件名；失败返回 null，UI 静默降级。 */
    suspend fun fetchCalendarFileName(): String?
}

class DefaultOtherFunctionRepository(
    private val remote: OtherFunctionRemoteDataSource,
) : OtherFunctionRepository {

    override suspend fun downloadCalendar(): OtherFunctionDownloadResult = try {
        OtherFunctionDownloadResult.Success(remote.fetchCalendarFile())
    } catch (error: CancellationException) {
        throw error
    } catch (error: OtherFunctionRemoteException) {
        OtherFunctionDownloadResult.Failure(error.reason.toSyncFailure())
    } catch (_: Exception) {
        OtherFunctionDownloadResult.Failure(OtherFunctionSyncFailure.NETWORK)
    }

    override suspend fun fetchCalendarFileName(): String? = try {
        remote.fetchCalendarFileName()
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        null
    }

    override suspend fun downloadReportCard(
        language: ReportCardLanguage,
    ): OtherFunctionDownloadResult = try {
        OtherFunctionDownloadResult.Success(remote.fetchReportCardFile(language))
    } catch (error: CancellationException) {
        throw error
    } catch (error: OtherFunctionRemoteException) {
        OtherFunctionDownloadResult.Failure(error.reason.toSyncFailure())
    } catch (_: Exception) {
        OtherFunctionDownloadResult.Failure(OtherFunctionSyncFailure.NETWORK)
    }
}

private fun OtherFunctionRemoteFailure.toSyncFailure(): OtherFunctionSyncFailure = when (this) {
    OtherFunctionRemoteFailure.NETWORK -> OtherFunctionSyncFailure.NETWORK
    OtherFunctionRemoteFailure.PARSE -> OtherFunctionSyncFailure.PARSE
    OtherFunctionRemoteFailure.SESSION_EXPIRED -> OtherFunctionSyncFailure.SESSION_EXPIRED
}
