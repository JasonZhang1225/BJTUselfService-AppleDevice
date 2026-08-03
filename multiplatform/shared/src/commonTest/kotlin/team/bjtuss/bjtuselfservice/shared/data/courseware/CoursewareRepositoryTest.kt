package team.bjtuss.bjtuselfservice.shared.data.courseware

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import team.bjtuss.bjtuselfservice.shared.domain.courseware.CoursewareCourse
import team.bjtuss.bjtuselfservice.shared.domain.courseware.CoursewareNode
import team.bjtuss.bjtuselfservice.shared.domain.courseware.CoursewareNodeKind
import team.bjtuss.bjtuselfservice.shared.domain.courseware.CoursewareSnapshot
import team.bjtuss.bjtuselfservice.shared.domain.homework.HomeworkFileContent

class CoursewareRepositoryTest {
    @Test
    fun refreshPreservesCachedTreeThenCourseLoadReplacesOnlyCurrentAccountSnapshot() = runBlocking {
        val local = FakeLocal(snapshot("旧课件"))
        val catalog = snapshot("unused").let { value ->
            value.copy(courses = value.courses.map { it.copy(children = emptyList(), childrenLoaded = false) })
        }
        val repository = DefaultCoursewareRepository(
            "student-a",
            local,
            FakeRemote(catalog, folderChildren = snapshot("新课件").courses.single().children),
        )

        val refreshed = assertIs<CoursewareRefreshResult.Success>(repository.refresh())
        assertEquals("旧课件", refreshed.snapshot.courses.single().children.single().name)
        val loaded = assertIs<CoursewareOperationResult.Success<CoursewareSnapshot>>(
            repository.loadCourse(refreshed.snapshot, 17),
        )

        assertEquals("新课件", loaded.value.courses.single().children.single().name)
        assertEquals(listOf("student-a", "student-a"), local.replacedAccounts)
    }

    @Test
    fun remoteAndCacheFailurePreserveOldSnapshot() = runBlocking {
        val cached = snapshot("完整缓存")
        val remoteFailureLocal = FakeLocal(cached)
        val remoteFailure = DefaultCoursewareRepository(
            "student-a",
            remoteFailureLocal,
            FakeRemote(error = CoursewareRemoteException(CoursewareRemoteFailure.SESSION_EXPIRED)),
        )
        val first = assertIs<CoursewareRefreshResult.Failure>(remoteFailure.refresh())
        assertEquals(cached, first.snapshot)
        assertEquals(CoursewareSyncFailure.SESSION_EXPIRED, first.reason)
        assertTrue(remoteFailureLocal.replacedAccounts.isEmpty())

        val cacheFailure = DefaultCoursewareRepository(
            "student-a",
            FakeLocal(cached, failReplace = true),
            FakeRemote(snapshot("新课件")),
        )
        val second = assertIs<CoursewareRefreshResult.Failure>(cacheFailure.refresh())
        assertEquals(cached, second.snapshot)
        assertEquals(CoursewareSyncFailure.CACHE, second.reason)
    }

    @Test
    fun loadingFolderMergesDirectChildrenAndReplacesCachedSnapshot() = runBlocking {
        val unloadedFolder = CoursewareNode(
            id = 1,
            courseId = 17,
            name = "第一章",
            kind = CoursewareNodeKind.FOLDER,
            childrenLoaded = false,
        )
        val partial = snapshot("说明.pdf").let { current ->
            current.copy(courses = current.courses.map { it.copy(children = listOf(unloadedFolder)) })
        }
        val child = CoursewareNode(
            id = 2,
            courseId = 17,
            name = "第一讲.pdf",
            kind = CoursewareNodeKind.RESOURCE,
            rpId = "rp-2",
        )
        val local = FakeLocal(partial)
        val repository = DefaultCoursewareRepository(
            "student-a",
            local,
            FakeRemote(snapshot = partial, folderChildren = listOf(child)),
        )

        val result = assertIs<CoursewareOperationResult.Success<CoursewareSnapshot>>(
            repository.loadFolder(partial, 17, unloadedFolder.stableKey),
        )

        val loaded = result.value.courses.single().children.single()
        assertTrue(loaded.childrenLoaded)
        assertEquals(listOf(child), loaded.children)
        assertEquals(listOf("student-a"), local.replacedAccounts)
    }

    private class FakeLocal(
        var snapshot: CoursewareSnapshot,
        private val failReplace: Boolean = false,
    ) : CoursewareLocalDataSource {
        val replacedAccounts = mutableListOf<String>()

        override fun load(accountScope: String): CoursewareSnapshot = snapshot

        override fun replace(accountScope: String, snapshot: CoursewareSnapshot) {
            if (failReplace) error("synthetic courseware cache failure")
            replacedAccounts += accountScope
            this.snapshot = snapshot
        }
    }

    private class FakeRemote(
        private val snapshot: CoursewareSnapshot? = null,
        private val error: Exception? = null,
        private val folderChildren: List<CoursewareNode> = emptyList(),
    ) : CoursewareRemoteDataSource {
        override suspend fun fetchSnapshot(): CoursewareSnapshot {
            error?.let { throw it }
            return requireNotNull(snapshot)
        }

        override suspend fun fetchChildren(
            course: CoursewareCourse,
            parentId: Int,
        ): List<CoursewareNode> = folderChildren

        override suspend fun downloadResource(node: CoursewareNode): HomeworkFileContent =
            HomeworkFileContent(node.name, "application/octet-stream", byteArrayOf(1))

        override suspend fun downloadTeachingCalendar(course: CoursewareCourse): HomeworkFileContent =
            HomeworkFileContent("${course.name}_教学日历.pdf", "application/pdf", "%PDF".encodeToByteArray())
    }

    private fun snapshot(name: String) = CoursewareSnapshot(
        listOf(
            CoursewareCourse(
                id = 17,
                name = "程序设计",
                courseNumber = "CS101",
                groupId = "G1",
                semesterCode = "2026-1",
                teacherId = 28,
                children = listOf(
                    CoursewareNode(
                        id = 2,
                        courseId = 17,
                        name = name,
                        kind = CoursewareNodeKind.RESOURCE,
                        rpId = "rp-2",
                    ),
                ),
            ),
        ),
    )
}
