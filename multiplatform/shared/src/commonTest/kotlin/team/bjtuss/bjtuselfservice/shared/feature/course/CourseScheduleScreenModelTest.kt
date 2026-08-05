package team.bjtuss.bjtuselfservice.shared.feature.course

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import team.bjtuss.bjtuselfservice.shared.data.course.CourseScheduleRefreshResult
import team.bjtuss.bjtuselfservice.shared.data.course.CourseScheduleRepository
import team.bjtuss.bjtuselfservice.shared.data.course.CourseScheduleSnapshot
import team.bjtuss.bjtuselfservice.shared.data.course.CourseScheduleSyncFailure
import team.bjtuss.bjtuselfservice.shared.domain.course.Course
import team.bjtuss.bjtuselfservice.shared.domain.change.DataChangeRecorder

class CourseScheduleScreenModelTest {
    @Test
    fun firstNetworkSnapshotFollowsCurrentWeekOnce() = runBlocking {
        val repository = FakeRepository(
            loaded = CourseScheduleSnapshot(listOf(course(1, week = 2)), 0),
            refreshed = CourseScheduleSnapshot(listOf(course(101, week = 8)), 8),
        )
        val model = CourseScheduleScreenModel(repository)

        model.initialize()

        assertEquals(8, model.state.value.selectedWeek)
        assertFalse(model.state.value.followCurrentWeek)
        assertEquals(CourseScheduleContentSource.NETWORK, model.state.value.source)
    }

    @Test
    fun disabledLoginSyncLoadsCacheWithoutNetworkRequest() = runBlocking {
        val cached = CourseScheduleSnapshot(listOf(course(1, week = 2)), 2)
        val repository = FakeRepository(cached, CourseScheduleSnapshot(emptyList(), 0))
        val model = CourseScheduleScreenModel(repository)

        model.initialize(refreshFromNetwork = false)

        assertEquals(0, repository.refreshCount)
        assertEquals(CourseScheduleContentSource.CACHE, model.state.value.source)
        assertFalse(model.state.value.isLoading)
    }

    @Test
    fun autoSyncInitializeRetriesUntilSuccess() = runBlocking {
        // 失败回落快照的 currentWeek 置 0，避免中途失败先“锁定”教学周导致成功后不再跟随。
        val cached = CourseScheduleSnapshot(listOf(course(1, week = 2)), 0)
        val success = CourseScheduleSnapshot(listOf(course(9, week = 5)), 5)
        val repository = FakeRepository(
            loaded = cached,
            refreshed = success,
            failFirstN = 2,
        )
        val model = CourseScheduleScreenModel(repository)

        model.initialize()

        assertEquals(3, repository.refreshCount)
        assertEquals(CourseScheduleContentSource.NETWORK, model.state.value.source)
        assertNull(model.state.value.failure)
        assertEquals(5, model.state.value.selectedWeek)
    }

    @Test
    fun autoSyncInitializeStopsAfterMaxFailedAttempts() = runBlocking {
        val cached = CourseScheduleSnapshot(listOf(course(1, week = 2)), 2)
        val repository = FakeRepository(
            loaded = cached,
            refreshed = cached,
            failFirstN = 10,
        )
        val model = CourseScheduleScreenModel(repository)

        model.initialize()

        assertEquals(AUTO_SYNC_MAX_ATTEMPTS, repository.refreshCount)
        assertEquals(CourseScheduleSyncFailure.NETWORK, model.state.value.failure)
        assertEquals(CourseScheduleContentSource.CACHE, model.state.value.source)
    }

    @Test
    fun manualWeekAndScheduleSwitchRemainPredictable() = runBlocking {
        val current = course(1, week = 3, selection = false)
        val selection = course(2, week = 5, selection = true)
        val snapshot = CourseScheduleSnapshot(listOf(current, selection), 6)
        val model = CourseScheduleScreenModel(FakeRepository(snapshot, snapshot))
        model.initialize()

        model.selectWeek(3)
        assertEquals(listOf(1), model.state.value.visibleCourses.map(Course::id))
        model.selectScheduleType(CourseScheduleType.SELECTION)
        assertEquals(0, model.state.value.selectedWeek)
        assertEquals(listOf(2), model.state.value.visibleCourses.map(Course::id))
        model.selectScheduleType(CourseScheduleType.CURRENT)
        assertEquals(6, model.state.value.selectedWeek)
        assertTrue(model.state.value.visibleCourses.isEmpty())
    }

    @Test
    fun dayAndDetailSelectionAreValidated() = runBlocking {
        val item = course(1, week = 3)
        val snapshot = CourseScheduleSnapshot(listOf(item), 3)
        val model = CourseScheduleScreenModel(FakeRepository(snapshot, snapshot))
        model.initialize()

        model.selectDay(4)
        model.selectDay(9)
        model.showCourseDetails(1)

        assertEquals(4, model.state.value.selectedDay)
        assertEquals(item, model.state.value.selectedCourse)
        model.dismissCourseDetails()
        assertEquals(null, model.state.value.selectedCourse)
    }

    @Test
    fun successfulRefreshRecordsBeforeAndAfterSnapshots() = runBlocking {
        val old = course(1, week = 3)
        val updated = old.copy(id = 101, coursePlace = "新教室")
        var captured: Pair<List<Course>, List<Course>>? = null
        val model = CourseScheduleScreenModel(
            repository = FakeRepository(
                CourseScheduleSnapshot(listOf(old), 3),
                CourseScheduleSnapshot(listOf(updated), 3),
            ),
            changeRecorder = DataChangeRecorder { before, after -> captured = before to after },
        )

        model.initialize()

        assertEquals(listOf(old), captured?.first)
        assertEquals(listOf(updated), captured?.second)
    }

    private class FakeRepository(
        private val loaded: CourseScheduleSnapshot,
        private var refreshed: CourseScheduleSnapshot,
        private val failFirstN: Int = 0,
    ) : CourseScheduleRepository {
        var refreshCount = 0
        override fun load(): CourseScheduleSnapshot = loaded
        override suspend fun refresh(): CourseScheduleRefreshResult {
            refreshCount += 1
            return if (refreshCount <= failFirstN) {
                CourseScheduleRefreshResult.Failure(
                    snapshot = loaded,
                    reason = CourseScheduleSyncFailure.NETWORK,
                )
            } else {
                CourseScheduleRefreshResult.Success(refreshed)
            }
        }
    }

    private fun course(id: Int, week: Int, selection: Boolean = false) = Course(
        id = id,
        courseId = "course-$id",
        courseName = "课程$id",
        courseTeacher = "教师",
        courseLocationIndex = 1,
        courseTime = "第${week}周",
        coursePlace = "教室",
        isCurrentSemester = selection,
    )
}
