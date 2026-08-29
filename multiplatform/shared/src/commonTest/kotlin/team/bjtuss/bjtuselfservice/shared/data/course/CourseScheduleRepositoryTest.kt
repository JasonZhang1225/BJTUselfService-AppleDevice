package team.bjtuss.bjtuselfservice.shared.data.course

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import team.bjtuss.bjtuselfservice.shared.domain.course.Course

class CourseScheduleRepositoryTest {
    @Test
    fun refreshReplacesOneAccountSnapshot() = runBlocking {
        val local = FakeLocal(CourseScheduleSnapshot(listOf(course(7, "旧课")), 2))
        val repository = DefaultCourseScheduleRepository(
            "student-a",
            local,
            FakeRemote(RemoteCourseScheduleSnapshot(listOf(course(0, "新课")), 9)),
        )

        val result = assertIs<CourseScheduleRefreshResult.Success>(repository.refresh())

        assertEquals("新课", result.snapshot.courses.single().courseName)
        assertEquals(100, result.snapshot.courses.single().id)
        assertEquals(9, result.snapshot.currentWeek)
        assertEquals(listOf("student-a"), local.replacedAccounts)
    }

    @Test
    fun remoteOrLocalFailureKeepsCompleteOldSnapshot() = runBlocking {
        val cached = CourseScheduleSnapshot(listOf(course(7, "完整缓存")), 6)
        val remoteFailureLocal = FakeLocal(cached)
        val remoteFailure = DefaultCourseScheduleRepository(
            "student-a",
            remoteFailureLocal,
            FakeRemote(error = CourseScheduleRemoteException(CourseScheduleRemoteFailure.NETWORK)),
        )
        val first = assertIs<CourseScheduleRefreshResult.Failure>(remoteFailure.refresh())
        assertEquals(cached, first.snapshot)
        assertTrue(remoteFailureLocal.replacedAccounts.isEmpty())

        val localFailure = DefaultCourseScheduleRepository(
            "student-a",
            FakeLocal(cached, failReplace = true),
            FakeRemote(RemoteCourseScheduleSnapshot(listOf(course(0, "新课")), 10)),
        )
        val second = assertIs<CourseScheduleRefreshResult.Failure>(localFailure.refresh())
        assertEquals(CourseScheduleSyncFailure.CACHE, second.reason)
        assertEquals(cached, second.snapshot)
    }

    @Test
    fun calendarReconciliationPersistsCorrectedCurrentWeek() {
        val local = FakeLocal(CourseScheduleSnapshot(listOf(course(7, "旧课")), 1))
        val repository = DefaultCourseScheduleRepository(
            "student-a",
            local,
            FakeRemote(RemoteCourseScheduleSnapshot(emptyList(), 1)),
        )

        val corrected = repository.reconcileCurrentWeek(26)

        assertEquals(26, corrected.currentWeek)
        assertEquals(26, local.snapshot.currentWeek)
        assertEquals(listOf("student-a"), local.replacedAccounts)
    }

    private class FakeRemote(
        private val snapshot: RemoteCourseScheduleSnapshot? = null,
        private val error: Exception? = null,
    ) : CourseScheduleRemoteDataSource {
        override suspend fun fetchSchedule(): RemoteCourseScheduleSnapshot {
            error?.let { throw it }
            return requireNotNull(snapshot)
        }
    }

    private class FakeLocal(
        var snapshot: CourseScheduleSnapshot,
        private val failReplace: Boolean = false,
    ) : CourseScheduleLocalDataSource {
        val replacedAccounts = mutableListOf<String>()

        override fun load(accountScope: String): CourseScheduleSnapshot = snapshot

        override fun replace(accountScope: String, snapshot: CourseScheduleSnapshot) {
            if (failReplace) error("synthetic course snapshot failure")
            replacedAccounts += accountScope
            this.snapshot = snapshot.copy(
                courses = snapshot.courses.mapIndexed { index, item -> item.copy(id = 100 + index) },
            )
        }
    }

    private fun course(id: Int, name: String) = Course(
        id = id,
        courseId = "course-$id",
        courseName = name,
        courseTeacher = "教师",
        courseLocationIndex = 1,
        courseTime = "第1-16周",
        coursePlace = "教室",
        isCurrentSemester = false,
    )
}
