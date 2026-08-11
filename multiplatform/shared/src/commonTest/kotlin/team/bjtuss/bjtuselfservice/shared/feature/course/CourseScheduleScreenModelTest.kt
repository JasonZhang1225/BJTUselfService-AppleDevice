package team.bjtuss.bjtuselfservice.shared.feature.course

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import team.bjtuss.bjtuselfservice.shared.data.course.CourseScheduleRefreshResult
import team.bjtuss.bjtuselfservice.shared.data.course.CourseScheduleRepository
import team.bjtuss.bjtuselfservice.shared.data.course.CourseScheduleSnapshot
import team.bjtuss.bjtuselfservice.shared.data.course.CourseScheduleSyncFailure
import team.bjtuss.bjtuselfservice.shared.data.classroomoccupancy.ClassroomOccupancyRepository
import team.bjtuss.bjtuselfservice.shared.data.classroomoccupancy.ClassroomOccupancyResult
import team.bjtuss.bjtuselfservice.shared.data.classroomoccupancy.SemesterOptions
import team.bjtuss.bjtuselfservice.shared.domain.classroomoccupancy.OccupancySemester
import team.bjtuss.bjtuselfservice.shared.domain.classroomoccupancy.OccupancyWeekDate
import team.bjtuss.bjtuselfservice.shared.domain.course.Course
import team.bjtuss.bjtuselfservice.shared.domain.change.DataChangeRecorder

class CourseScheduleScreenModelTest {
    @Test
    fun stateExposesTodayForCompactListContext() {
        val today = LocalDate(2026, 8, 11)
        val snapshot = CourseScheduleSnapshot(listOf(course(1, week = 24)), 24)
        val model = CourseScheduleScreenModel(
            repository = FakeRepository(snapshot, snapshot),
            todayProvider = { today },
        )

        assertEquals(today, model.state.value.todayDate)
    }

    @Test
    fun horizontalTrackpadGestureTurnsExactlyOneWeekUntilMomentumEnds() {
        val accumulator = CourseWeekScrollAccumulator(threshold = 30f, quietGapMillis = 180L)

        assertEquals(null, accumulator.add(deltaX = 12f, deltaY = 1f, eventTimeMillis = 1_000L))
        assertEquals(CourseWeekScrollDirection.NEXT, accumulator.add(20f, 1f, 1_016L))
        assertEquals(null, accumulator.add(60f, 0f, 1_032L))
        assertEquals(null, accumulator.add(1f, 20f, 1_048L))
        assertEquals(null, accumulator.add(40f, 0f, 1_140L))
        assertEquals(CourseWeekScrollDirection.NEXT, accumulator.add(31f, 0f, 1_400L))
    }

    @Test
    fun explicitGestureResetAllowsImmediateSecondSwipe() {
        val accumulator = CourseWeekScrollAccumulator(threshold = 30f)

        assertEquals(CourseWeekScrollDirection.NEXT, accumulator.add(31f, 0f, 1_000L))
        assertEquals(null, accumulator.add(31f, 0f, 1_050L))
        accumulator.resetGesture()
        assertEquals(CourseWeekScrollDirection.NEXT, accumulator.add(31f, 0f, 1_060L))
    }

    @Test
    fun reverseSwipeUnlocksImmediatelyWithoutWaitingForQuietGap() {
        val accumulator = CourseWeekScrollAccumulator(
            threshold = 30f,
            quietGapMillis = 1_000L,
            minTurnIntervalMillis = 1_000L,
        )

        assertEquals(CourseWeekScrollDirection.NEXT, accumulator.add(31f, 0f, 1_000L))
        assertEquals(CourseWeekScrollDirection.PREVIOUS, accumulator.add(-31f, 0f, 1_016L))
    }

    @Test
    fun distancePagerHasNoPersistentGestureLock() {
        val accumulator = CourseWeekScrollAccumulator(
            threshold = 30f,
            quietGapMillis = 80L,
            minTurnIntervalMillis = 180L,
        )

        assertEquals(CourseWeekScrollDirection.NEXT, accumulator.add(31f, 0f, 1_000L))
        assertEquals(null, accumulator.add(80f, 0f, 1_050L))
        assertEquals(CourseWeekScrollDirection.NEXT, accumulator.add(31f, 0f, 1_200L))
        assertEquals(CourseWeekScrollDirection.NEXT, accumulator.add(31f, 0f, 1_400L))
    }

    @Test
    fun verticalScrollDoesNotTurnWeekAndNegativeHorizontalScrollGoesBack() {
        val accumulator = CourseWeekScrollAccumulator(threshold = 30f)

        assertEquals(null, accumulator.add(deltaX = 20f, deltaY = 40f, eventTimeMillis = 1_000L))
        assertEquals(null, accumulator.add(deltaX = -16f, deltaY = 1f, eventTimeMillis = 1_300L))
        assertEquals(CourseWeekScrollDirection.PREVIOUS, accumulator.add(-16f, 1f, 1_316L))
    }

    @Test
    fun overviewPagerPlacesAllBeforeWeekOne() {
        assertEquals(31, COURSE_OVERVIEW_PAGE_COUNT)
        assertEquals(0, overviewPageForWeek(0))
        assertEquals(0, weekForOverviewPage(0))
        assertEquals(1, overviewPageForWeek(1))
        assertEquals(1, weekForOverviewPage(1))
        assertEquals(30, overviewPageForWeek(30))
        assertEquals(30, weekForOverviewPage(30))
    }

    @Test
    fun firstNetworkSnapshotFollowsCurrentWeekOnce() = runBlocking {
        val repository = FakeRepository(
            loaded = CourseScheduleSnapshot(listOf(course(1, week = 2)), 0),
            refreshed = CourseScheduleSnapshot(listOf(course(101, week = 8)), 8),
        )
        val model = CourseScheduleScreenModel(repository)

        model.initialize()

        assertEquals(8, model.state.value.selectedWeek)
        assertTrue(model.state.value.followCurrentWeek)
        assertEquals(CourseScheduleContentSource.NETWORK, model.state.value.source)
    }

    @Test
    fun cachedCurrentWeekDoesNotFreezeLaterNetworkCurrentWeek() = runBlocking {
        val repository = FakeRepository(
            loaded = CourseScheduleSnapshot(listOf(course(1, week = 23)), 23),
            refreshed = CourseScheduleSnapshot(listOf(course(2, week = 24)), 24),
        )
        val model = CourseScheduleScreenModel(repository)

        model.initialize()

        assertEquals(24, model.state.value.currentWeek)
        assertEquals(24, model.state.value.selectedWeek)
        assertTrue(model.state.value.followCurrentWeek)
    }

    @Test
    fun summerContinuationWeekStillFollowsCurrentWeek() = runBlocking {
        val repository = FakeRepository(
            loaded = CourseScheduleSnapshot(listOf(course(1, week = 26)), 26),
            refreshed = CourseScheduleSnapshot(listOf(course(2, week = 27)), 27),
        )
        val model = CourseScheduleScreenModel(repository)

        model.initialize()

        assertEquals(27, model.state.value.currentWeek)
        assertEquals(27, model.state.value.selectedWeek)
        assertTrue(model.state.value.followCurrentWeek)
    }

    @Test
    fun manualWeekStillWinsAgainstLaterNetworkRefresh() = runBlocking {
        val repository = FakeRepository(
            loaded = CourseScheduleSnapshot(listOf(course(1, week = 23)), 23),
            refreshed = CourseScheduleSnapshot(listOf(course(2, week = 24)), 24),
        )
        val model = CourseScheduleScreenModel(repository)
        model.initialize(refreshFromNetwork = false)
        model.selectWeek(20)

        model.initialize(refreshFromNetwork = true)

        assertEquals(24, model.state.value.currentWeek)
        assertEquals(20, model.state.value.selectedWeek)
        assertFalse(model.state.value.followCurrentWeek)
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
    fun calendarDateSelectionUpdatesWeekAndDayAtomicallyAndMarksHoliday() = runBlocking {
        val snapshot = CourseScheduleSnapshot(listOf(course(1, week = 2)), 2)
        val calendar = FakeCalendarRepository(
            weeks = listOf(
                week(1, LocalDate(2026, 9, 7)),
                week(2, LocalDate(2026, 9, 14)),
            ),
        )
        val model = CourseScheduleScreenModel(
            repository = FakeRepository(snapshot, snapshot),
            calendarRepository = calendar,
            todayProvider = { LocalDate(2026, 9, 15) },
        )
        model.initialize()
        model.ensureCalendarLoaded()

        model.selectDate(LocalDate(2026, 9, 16))
        assertEquals(2, model.state.value.selectedWeek)
        assertEquals(2, model.state.value.selectedDay)
        assertFalse(model.state.value.dateOutsideTeachingWeeks)
        assertFalse(model.state.value.followCurrentWeek)

        model.selectDate(LocalDate(2026, 10, 1))
        assertEquals(0, model.state.value.selectedWeek)
        assertTrue(model.state.value.dateOutsideTeachingWeeks)
        assertTrue(model.state.value.visibleCourses.isEmpty())
    }

    @Test
    fun manualWeekSelectionGetsConcreteDateAfterCalendarArrives() = runBlocking {
        val snapshot = CourseScheduleSnapshot(listOf(course(1, week = 2)), 2)
        val model = CourseScheduleScreenModel(
            repository = FakeRepository(snapshot, snapshot),
            calendarRepository = FakeCalendarRepository(listOf(week(2, LocalDate(2026, 9, 14)))),
        )
        model.initialize()
        model.selectWeek(2)
        model.selectDay(4)
        model.ensureCalendarLoaded()

        assertEquals(LocalDate(2026, 9, 18), model.state.value.selectedDate)
        assertEquals("2026-2027-1", model.state.value.calendarSemesterLabel)
    }

    @Test
    fun selectionScheduleUsesExactNextSemesterCalendarInsteadOfCurrentSummerDates() = runBlocking {
        val springWeeks = listOf(
            week(24, LocalDate(2026, 8, 10)),
            week(27, LocalDate(2026, 8, 31)),
        )
        val fallWeeks = listOf(
            week(1, LocalDate(2026, 9, 7)),
            week(2, LocalDate(2026, 9, 14)),
        )
        val snapshot = CourseScheduleSnapshot(
            courses = listOf(course(1, week = 1, selection = true)),
            currentWeek = 24,
        )
        val model = CourseScheduleScreenModel(
            repository = FakeRepository(snapshot, snapshot),
            calendarRepository = FakeCalendarRepository(
                weeks = springWeeks,
                selectedLabel = "2025-2026-2",
                allWeekDates = mapOf(
                    "2025-2026-2" to springWeeks,
                    "2026-2027-1" to fallWeeks,
                ),
            ),
            todayProvider = { LocalDate(2026, 8, 11) },
        )

        model.initialize()
        model.ensureCalendarLoaded()
        assertEquals("2025-2026-2", model.state.value.calendarSemesterLabel)
        assertEquals(LocalDate(2026, 8, 10), model.state.value.academicWeeks.first().startDate)

        model.selectScheduleType(CourseScheduleType.SELECTION)
        assertEquals("2026-2027-1", model.state.value.calendarSemesterLabel)
        assertEquals(LocalDate(2026, 9, 7), model.state.value.academicWeeks.first().startDate)
    }

    @Test
    fun dateSelectionCrossesBetweenCurrentAndSelectionCalendars() = runBlocking {
        val springWeeks = listOf(week(24, LocalDate(2026, 8, 10)))
        val fallWeeks = listOf(week(1, LocalDate(2026, 9, 7)))
        val snapshot = CourseScheduleSnapshot(
            courses = listOf(
                course(1, week = 24, selection = false),
                course(2, week = 1, selection = true),
            ),
            currentWeek = 24,
        )
        val model = CourseScheduleScreenModel(
            repository = FakeRepository(snapshot, snapshot),
            calendarRepository = FakeCalendarRepository(
                weeks = springWeeks,
                selectedLabel = "2025-2026-2",
                allWeekDates = mapOf(
                    "2025-2026-2" to springWeeks,
                    "2026-2027-1" to fallWeeks,
                ),
            ),
            todayProvider = { LocalDate(2026, 8, 11) },
        )

        model.initialize()
        model.ensureCalendarLoaded()

        model.selectDate(LocalDate(2026, 9, 9))
        assertEquals(CourseScheduleType.SELECTION, model.state.value.scheduleType)
        assertEquals("2026-2027-1", model.state.value.calendarSemesterLabel)
        assertEquals(1, model.state.value.selectedWeek)
        assertEquals(2, model.state.value.selectedDay)
        assertEquals(listOf(2), model.state.value.visibleCourses.map(Course::id))

        model.selectDate(LocalDate(2026, 8, 12))
        assertEquals(CourseScheduleType.CURRENT, model.state.value.scheduleType)
        assertEquals("2025-2026-2", model.state.value.calendarSemesterLabel)
        assertEquals(24, model.state.value.selectedWeek)
        assertEquals(2, model.state.value.selectedDay)
        assertEquals(listOf(1), model.state.value.visibleCourses.map(Course::id))
    }

    @Test
    fun selectionScheduleDoesNotReuseCurrentCalendarWhenNextSemesterIsMissing() {
        val springWeeks = listOf(week(24, LocalDate(2026, 8, 10)))
        val mappings = resolveCourseScheduleCalendarMappings(
            selectedSemesterLabel = "2025-2026-2",
            weekDates = mapOf("2025-2026-2" to springWeeks),
            today = LocalDate(2026, 8, 11),
        )

        assertEquals("2025-2026-2", mappings[CourseScheduleType.CURRENT]?.semesterLabel)
        assertEquals(null, mappings[CourseScheduleType.SELECTION])
    }

    @Test
    fun compactViewModeDoesNotLoseWeekOrDaySelection() = runBlocking {
        val snapshot = CourseScheduleSnapshot(listOf(course(1, week = 3)), 3)
        val model = CourseScheduleScreenModel(FakeRepository(snapshot, snapshot))
        model.initialize()
        assertEquals(CourseCompactViewMode.WEEK, model.state.value.compactViewMode)
        model.selectDay(5)
        model.selectCompactViewMode(CourseCompactViewMode.DAY)

        assertEquals(CourseCompactViewMode.DAY, model.state.value.compactViewMode)
        assertEquals(3, model.state.value.selectedWeek)
        assertEquals(5, model.state.value.selectedDay)
    }

    @Test
    fun repeatedDesktopWeekMovesReadLatestState() = runBlocking {
        val snapshot = CourseScheduleSnapshot(listOf(course(1, week = 3)), 3)
        val model = CourseScheduleScreenModel(FakeRepository(snapshot, snapshot))
        model.initialize()

        model.moveWeekBy(1)
        model.moveWeekBy(1)

        assertEquals(5, model.state.value.selectedWeek)
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

    private class FakeCalendarRepository(
        private val weeks: List<OccupancyWeekDate>,
        private val selectedLabel: String = "2026-2027-1",
        private val allWeekDates: Map<String, List<OccupancyWeekDate>> = mapOf(selectedLabel to weeks),
    ) : ClassroomOccupancyRepository {
        private val semester = OccupancySemester("$selectedLabel-1", selectedLabel)

        override suspend fun fetchOccupancy(
            week: Int,
            buildingId: String,
            semesterId: String?,
        ): ClassroomOccupancyResult = error("not used")

        override suspend fun fetchSemesters(): SemesterOptions = SemesterOptions(
            selected = semester,
            all = listOf(semester),
        )

        override suspend fun fetchWeekDates(): Map<String, List<OccupancyWeekDate>> =
            allWeekDates
    }

    private fun week(number: Int, start: LocalDate) = OccupancyWeekDate(
        week = number,
        startMonthDay = "${start.month.ordinal + 1}/${start.day}",
        endMonthDay = "",
        startDate = start,
    )

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
