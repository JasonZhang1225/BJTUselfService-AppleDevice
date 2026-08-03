package team.bjtuss.bjtuselfservice.shared.feature.exam

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import team.bjtuss.bjtuselfservice.shared.data.exam.ExamScheduleRefreshResult
import team.bjtuss.bjtuselfservice.shared.data.exam.ExamScheduleRepository
import team.bjtuss.bjtuselfservice.shared.data.exam.ExamScheduleSnapshot
import team.bjtuss.bjtuselfservice.shared.domain.exam.ExamSchedule
import team.bjtuss.bjtuselfservice.shared.domain.change.DataChangeRecorder

class ExamScheduleScreenModelTest {
    @Test
    fun initializationAppliesNetworkAndTypeFilter() = runBlocking {
        val first = exam(1, "期末考试")
        val second = exam(2, "补考")
        val snapshot = ExamScheduleSnapshot(listOf(first, second))
        val model = ExamScheduleScreenModel(FakeRepository(snapshot, snapshot))

        model.initialize()
        model.selectType("补考")

        assertEquals(ExamScheduleContentSource.NETWORK, model.state.value.source)
        assertEquals(listOf(2), model.state.value.visibleExams.map(ExamSchedule::id))
        model.selectType("不存在")
        assertEquals(2, model.state.value.visibleExams.size)
    }

    @Test
    fun detailSelectionAndDismissAreStable() = runBlocking {
        val item = exam(1, "期末考试")
        val snapshot = ExamScheduleSnapshot(listOf(item))
        val model = ExamScheduleScreenModel(FakeRepository(snapshot, snapshot))
        model.initialize()

        model.showExamDetails(1)
        assertEquals(item, model.state.value.selectedExam)
        model.dismissExamDetails()
        assertEquals(null, model.state.value.selectedExam)
        assertFalse(model.state.value.isLoading)
    }

    @Test
    fun successfulRefreshRecordsBeforeAndAfterSnapshots() = runBlocking {
        val old = exam(1, "期末考试")
        val updated = old.copy(id = 101, examTimeAndPlace = "新时间地点")
        var captured: Pair<List<ExamSchedule>, List<ExamSchedule>>? = null
        val model = ExamScheduleScreenModel(
            repository = FakeRepository(
                ExamScheduleSnapshot(listOf(old)),
                ExamScheduleSnapshot(listOf(updated)),
            ),
            changeRecorder = DataChangeRecorder { before, after -> captured = before to after },
        )

        model.initialize()

        assertEquals(listOf(old), captured?.first)
        assertEquals(listOf(updated), captured?.second)
    }

    private class FakeRepository(
        private val loaded: ExamScheduleSnapshot,
        private val refreshed: ExamScheduleSnapshot,
    ) : ExamScheduleRepository {
        override fun load(): ExamScheduleSnapshot = loaded
        override suspend fun refresh(): ExamScheduleRefreshResult =
            ExamScheduleRefreshResult.Success(refreshed)
    }

    private fun exam(id: Int, type: String) = ExamSchedule(
        id = id,
        examType = type,
        courseName = "课程$id",
        examTimeAndPlace = "时间地点",
        examStatus = "正常",
        detail = "详情",
    )
}
