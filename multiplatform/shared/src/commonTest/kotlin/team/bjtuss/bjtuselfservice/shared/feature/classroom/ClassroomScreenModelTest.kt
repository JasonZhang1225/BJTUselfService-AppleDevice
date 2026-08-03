package team.bjtuss.bjtuselfservice.shared.feature.classroom

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import team.bjtuss.bjtuselfservice.shared.data.classroom.ClassroomFetchFailure
import team.bjtuss.bjtuselfservice.shared.data.classroom.ClassroomFetchResult
import team.bjtuss.bjtuselfservice.shared.data.classroom.ClassroomRepository
import team.bjtuss.bjtuselfservice.shared.domain.classroom.ClassroomBuildingInfo
import team.bjtuss.bjtuselfservice.shared.domain.classroom.ClassroomCapacity
import team.bjtuss.bjtuselfservice.shared.domain.classroom.ClassroomSortDirection
import team.bjtuss.bjtuselfservice.shared.domain.classroom.ClassroomSortField

class ClassroomScreenModelTest {
    @Test
    fun selectsBuildingAndLoadsRooms() = runBlocking {
        val model = ClassroomScreenModel(FakeRepository(successInfo()))
        model.selectBuilding("思源楼")
        val loaded = assertIs<ClassroomBuildingState.Loaded>(model.state.value.buildingState)
        assertEquals(3, loaded.info.classrooms.size)
    }

    @Test
    fun filtersFreeSeatsAndCapacity() = runBlocking {
        val model = ClassroomScreenModel(FakeRepository(successInfo()))
        model.selectBuilding("思源楼")
        model.setOnlyWithFreeSeats(true)
        model.setCapacityRange(100, null)
        assertEquals(listOf("SY103"), model.state.value.visibleClassrooms.map { it.name })
    }

    @Test
    fun sortsAndTogglesDirection() = runBlocking {
        val model = ClassroomScreenModel(FakeRepository(successInfo()))
        model.selectBuilding("思源楼")
        model.setSortField(ClassroomSortField.CAPACITY)
        assertEquals(listOf("SY102", "SY101", "SY103"), model.state.value.visibleClassrooms.map { it.name })
        model.setSortField(ClassroomSortField.CAPACITY)
        assertEquals(ClassroomSortDirection.DESCENDING, model.state.value.sortDirection)
        assertEquals(listOf("SY103", "SY101", "SY102"), model.state.value.visibleClassrooms.map { it.name })
    }

    @Test
    fun refreshFailureKeepsPreviousSnapshot() = runBlocking {
        val repository = SequenceRepository(
            ClassroomFetchResult.Success(successInfo()),
            ClassroomFetchResult.Failure(ClassroomFetchFailure.NETWORK),
        )
        val model = ClassroomScreenModel(repository)
        model.selectBuilding("思源楼")
        model.refresh()
        val failed = assertIs<ClassroomBuildingState.Failed>(model.state.value.buildingState)
        assertEquals(3, failed.cached?.classrooms?.size)
        assertEquals(3, model.state.value.visibleClassrooms.size)
    }

    @Test
    fun rejectsUnknownBuilding() {
        runBlocking {
            val model = ClassroomScreenModel(FakeRepository(successInfo()))
            assertFailsWith<IllegalArgumentException> { model.selectBuilding("不存在的楼") }
        }
    }
}

private class FakeRepository(private val info: ClassroomBuildingInfo) : ClassroomRepository {
    override suspend fun fetchBuildingInfo(buildingName: String) = ClassroomFetchResult.Success(
        info.copy(buildingName = buildingName),
    )
}

private class SequenceRepository(vararg results: ClassroomFetchResult) : ClassroomRepository {
    private val queue = results.toMutableList()
    override suspend fun fetchBuildingInfo(buildingName: String): ClassroomFetchResult = queue.removeAt(0)
}

private fun successInfo() = ClassroomBuildingInfo(
    buildingName = "思源楼",
    effectiveStart = "a",
    effectiveEnd = "b",
    classrooms = listOf(
        ClassroomCapacity("SY101", 50.0, 45, 90),
        ClassroomCapacity("SY102", 100.0, 32, 32),
        ClassroomCapacity("SY103", 25.0, 30, 120),
    ),
)
