package team.bjtuss.bjtuselfservice.shared.data.exam

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import team.bjtuss.bjtuselfservice.shared.domain.exam.ExamSchedule

class ExamScheduleRepositoryTest {
    @Test
    fun refreshReplacesOnlyCurrentAccountSnapshot() = runBlocking {
        val local = FakeLocal(ExamScheduleSnapshot(listOf(exam(7, "旧考试"))))
        val repository = DefaultExamScheduleRepository(
            "student-a",
            local,
            FakeRemote(listOf(exam(0, "新考试"))),
        )

        val result = assertIs<ExamScheduleRefreshResult.Success>(repository.refresh())

        assertEquals("新考试", result.snapshot.exams.single().courseName)
        assertEquals(100, result.snapshot.exams.single().id)
        assertEquals(listOf("student-a"), local.replacedAccounts)
    }

    @Test
    fun remoteAndCacheFailurePreserveOldSnapshot() = runBlocking {
        val cached = ExamScheduleSnapshot(listOf(exam(7, "完整缓存")))
        val firstLocal = FakeLocal(cached)
        val remoteFailure = DefaultExamScheduleRepository(
            "student-a",
            firstLocal,
            FakeRemote(error = ExamScheduleRemoteException(ExamScheduleRemoteFailure.NETWORK)),
        )
        assertEquals(
            cached,
            assertIs<ExamScheduleRefreshResult.Failure>(remoteFailure.refresh()).snapshot,
        )
        assertTrue(firstLocal.replacedAccounts.isEmpty())

        val cacheFailure = DefaultExamScheduleRepository(
            "student-a",
            FakeLocal(cached, failReplace = true),
            FakeRemote(listOf(exam(0, "新考试"))),
        )
        val second = assertIs<ExamScheduleRefreshResult.Failure>(cacheFailure.refresh())
        assertEquals(ExamScheduleSyncFailure.CACHE, second.reason)
        assertEquals(cached, second.snapshot)
    }

    private class FakeRemote(
        private val exams: List<ExamSchedule> = emptyList(),
        private val error: Exception? = null,
    ) : ExamScheduleRemoteDataSource {
        override suspend fun fetchExams(): List<ExamSchedule> {
            error?.let { throw it }
            return exams
        }
    }

    private class FakeLocal(
        var snapshot: ExamScheduleSnapshot,
        private val failReplace: Boolean = false,
    ) : ExamScheduleLocalDataSource {
        val replacedAccounts = mutableListOf<String>()
        override fun load(accountScope: String): ExamScheduleSnapshot = snapshot
        override fun replace(accountScope: String, exams: List<ExamSchedule>) {
            if (failReplace) error("synthetic exam cache failure")
            replacedAccounts += accountScope
            snapshot = ExamScheduleSnapshot(
                exams.mapIndexed { index, exam -> exam.copy(id = 100 + index) },
            )
        }
    }

    private fun exam(id: Int, course: String) = ExamSchedule(
        id = id,
        examType = "期末考试",
        courseName = course,
        examTimeAndPlace = "时间地点",
        examStatus = "正常",
        detail = "详情",
    )
}
