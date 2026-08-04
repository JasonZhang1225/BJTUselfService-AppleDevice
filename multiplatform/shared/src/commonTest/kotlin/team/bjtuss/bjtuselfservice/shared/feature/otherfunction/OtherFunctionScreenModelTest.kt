package team.bjtuss.bjtuselfservice.shared.feature.otherfunction

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import team.bjtuss.bjtuselfservice.shared.data.otherfunction.OtherFunctionDownloadResult
import team.bjtuss.bjtuselfservice.shared.data.otherfunction.OtherFunctionRepository
import team.bjtuss.bjtuselfservice.shared.data.otherfunction.OtherFunctionSyncFailure
import team.bjtuss.bjtuselfservice.shared.domain.homework.HomeworkFileContent
import team.bjtuss.bjtuselfservice.shared.domain.otherfunction.OtherFunctionFailure
import team.bjtuss.bjtuselfservice.shared.domain.otherfunction.OtherFunctionTaskState
import team.bjtuss.bjtuselfservice.shared.domain.otherfunction.ReportCardLanguage
import team.bjtuss.bjtuselfservice.shared.files.HomeworkFileGateway
import team.bjtuss.bjtuselfservice.shared.files.HomeworkFileGatewayFailure
import team.bjtuss.bjtuselfservice.shared.files.HomeworkFilePickResult
import team.bjtuss.bjtuselfservice.shared.files.HomeworkFileSaveResult

class OtherFunctionScreenModelTest {

    @Test
    fun calendarSuccessSavesFileAndShowsFileName() = runBlocking {
        val repository = FakeRepository(calendar = success("2024-2025校历.pdf"))
        val gateway = FakeFileGateway(HomeworkFileSaveResult.Saved)
        val model = OtherFunctionScreenModel(repository, gateway)

        model.downloadCalendar()

        assertEquals(
            OtherFunctionTaskState.Saved("2024-2025校历.pdf"),
            model.state.value.calendarState,
        )
        assertEquals("2024-2025校历.pdf", gateway.savedFileNames.single())
    }

    @Test
    fun saveCancellationIsNotRedError() = runBlocking {
        val repository = FakeRepository(calendar = success("校历.pdf"))
        val gateway = FakeFileGateway(HomeworkFileSaveResult.Cancelled)
        val model = OtherFunctionScreenModel(repository, gateway)

        model.downloadCalendar()

        assertEquals(OtherFunctionTaskState.SaveCancelled, model.state.value.calendarState)
    }

    @Test
    fun saveUnavailableMapsToSaveUnavailableFailure() = runBlocking {
        val repository = FakeRepository(calendar = success("校历.pdf"))
        val gateway = FakeFileGateway(
            HomeworkFileSaveResult.Failed(HomeworkFileGatewayFailure.UNAVAILABLE),
        )
        val model = OtherFunctionScreenModel(repository, gateway)

        model.downloadCalendar()

        assertEquals(
            OtherFunctionTaskState.Failed(OtherFunctionFailure.SAVE_UNAVAILABLE),
            model.state.value.calendarState,
        )
    }

    @Test
    fun networkFailureMapsToNetworkFailure() = runBlocking {
        val repository = FakeRepository(
            calendar = OtherFunctionDownloadResult.Failure(OtherFunctionSyncFailure.NETWORK),
        )
        val gateway = FakeFileGateway(HomeworkFileSaveResult.Saved)
        val model = OtherFunctionScreenModel(repository, gateway)

        model.downloadCalendar()

        assertEquals(
            OtherFunctionTaskState.Failed(OtherFunctionFailure.NETWORK),
            model.state.value.calendarState,
        )
        assertTrue(gateway.savedFileNames.isEmpty())
    }

    @Test
    fun sessionExpiredFailureMapsToSessionExpired() = runBlocking {
        val repository = FakeRepository(
            reportCard = OtherFunctionDownloadResult.Failure(OtherFunctionSyncFailure.SESSION_EXPIRED),
        )
        val gateway = FakeFileGateway(HomeworkFileSaveResult.Saved)
        val model = OtherFunctionScreenModel(repository, gateway)

        model.downloadReportCard()

        assertEquals(
            OtherFunctionTaskState.Failed(OtherFunctionFailure.SESSION_EXPIRED),
            model.state.value.reportCardState,
        )
    }

    @Test
    fun switchingLanguageAffectsRepositoryLanguage() = runBlocking {
        val repository = FakeRepository(reportCard = success("英文成绩单.pdf"))
        val gateway = FakeFileGateway(HomeworkFileSaveResult.Saved)
        val model = OtherFunctionScreenModel(repository, gateway)

        model.setReportCardLanguage(ReportCardLanguage.ENGLISH)
        model.downloadReportCard()

        assertEquals(ReportCardLanguage.ENGLISH, repository.lastReportCardLanguage)
        assertEquals(
            OtherFunctionTaskState.Saved("英文成绩单.pdf"),
            model.state.value.reportCardState,
        )
    }

    @Test
    fun refreshCalendarFileNameShowsLatestName() = runBlocking {
        val repository = FakeRepository(calendarFileName = "2024-2025校历.pdf")
        val model = OtherFunctionScreenModel(repository, FakeFileGateway(HomeworkFileSaveResult.Saved))

        model.refreshCalendarFileName()

        assertEquals("2024-2025校历.pdf", model.state.value.calendarFileName)
        assertEquals(false, model.state.value.calendarFileNameLoading)
    }

    @Test
    fun refreshCalendarFileNameFailureKeepsNullSilently() = runBlocking {
        val repository = FakeRepository(calendarFileName = null)
        val model = OtherFunctionScreenModel(repository, FakeFileGateway(HomeworkFileSaveResult.Saved))

        model.refreshCalendarFileName()

        assertEquals(null, model.state.value.calendarFileName)
        assertEquals(false, model.state.value.calendarFileNameLoading)
    }

    private fun success(fileName: String) = OtherFunctionDownloadResult.Success(
        HomeworkFileContent(fileName, "application/pdf", "pdf".encodeToByteArray()),
    )

    private class FakeRepository(
        private val calendar: OtherFunctionDownloadResult? = null,
        private val reportCard: OtherFunctionDownloadResult? = null,
        private val calendarFileName: String? = null,
    ) : OtherFunctionRepository {
        var lastReportCardLanguage: ReportCardLanguage? = null

        override suspend fun downloadCalendar(): OtherFunctionDownloadResult =
            calendar ?: error("calendar not stubbed")

        override suspend fun downloadReportCard(language: ReportCardLanguage): OtherFunctionDownloadResult {
            lastReportCardLanguage = language
            return reportCard ?: error("reportCard not stubbed")
        }

        override suspend fun fetchCalendarFileName(): String? = calendarFileName
    }

    private class FakeFileGateway(
        private val saveResult: HomeworkFileSaveResult,
    ) : HomeworkFileGateway {
        override val isAvailable: Boolean = true
        val savedFileNames = mutableListOf<String>()

        override suspend fun pickFiles(): HomeworkFilePickResult = HomeworkFilePickResult.Cancelled

        override suspend fun saveFile(file: HomeworkFileContent): HomeworkFileSaveResult {
            savedFileNames += file.fileName
            return saveResult
        }
    }
}
