package team.bjtuss.bjtuselfservice.shared.domain.courseware

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CoursewareRulesTest {
    @Test
    fun visibleTreeOnlyIncludesChildrenOfExpandedFolders() {
        val course = course()

        val collapsed = visibleCoursewareTree(course.children, emptySet())
        val expanded = visibleCoursewareTree(course.children, setOf(folder().stableKey))

        assertEquals(listOf("章节", "课程说明.pdf"), collapsed.map { it.node.name })
        assertEquals(listOf(0, 0), collapsed.map(VisibleCoursewareNode::depth))
        assertEquals(listOf("章节", "第一讲.pdf", "课程说明.pdf"), expanded.map { it.node.name })
        assertEquals(listOf(0, 1, 0), expanded.map(VisibleCoursewareNode::depth))
    }

    @Test
    fun compactPathResolvesFoldersAndRejectsInvalidPath() {
        val course = course()

        assertEquals(listOf("第一讲.pdf"), nodesAtCoursewarePath(course, listOf(folder().stableKey))?.map { it.name })
        assertEquals(listOf("章节"), coursewarePathNames(course, listOf(folder().stableKey)))
        assertNull(nodesAtCoursewarePath(course, listOf("missing")))
    }

    private fun course() = CoursewareCourse(
        id = 17,
        name = "程序设计",
        courseNumber = "CS101",
        groupId = "G1",
        semesterCode = "2026-1",
        teacherId = 28,
        children = listOf(
            folder(),
            resource(3, "课程说明.pdf"),
        ),
    )

    private fun folder() = CoursewareNode(
        id = 1,
        courseId = 17,
        name = "章节",
        kind = CoursewareNodeKind.FOLDER,
        children = listOf(resource(2, "第一讲.pdf")),
    )

    private fun resource(id: Int, name: String) = CoursewareNode(
        id = id,
        courseId = 17,
        name = name,
        kind = CoursewareNodeKind.RESOURCE,
        rpId = "rp-$id",
        extension = "pdf",
    )
}
