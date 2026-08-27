package team.bjtuss.bjtuselfservice.shared.feature.phyvlab

import kotlin.test.Test
import kotlin.test.assertEquals
import team.bjtuss.bjtuselfservice.shared.domain.phyvlab.PhyVlabActivity

class PhyVlabActivityOrderTest {
    @Test
    fun defaultsToNewestMoodleActivityFirst() {
        val activities = listOf(
            activity(id = 2, title = "新 ID 但较早截止", dueTimestamp = 100),
            activity(id = 3, title = "最新 ID", dueTimestamp = 50),
            activity(id = 1, title = "最旧 ID", dueTimestamp = null),
        )

        assertEquals(
            listOf(3, 2, 1),
            orderPhyVlabActivities(activities, descending = true).map { it.id },
        )
    }

    @Test
    fun ascendingOrderPutsOldestActivityFirst() {
        val activities = listOf(
            activity(id = 2, title = "ID 2", dueTimestamp = 300),
            activity(id = 3, title = "ID 3", dueTimestamp = 100),
            activity(id = 1, title = "ID 1", dueTimestamp = null),
        )

        assertEquals(
            listOf(1, 2, 3),
            orderPhyVlabActivities(activities, descending = false).map { it.id },
        )
    }

    private fun activity(id: Int, title: String, dueTimestamp: Long?): PhyVlabActivity =
        PhyVlabActivity(
            id = id,
            courseId = 72,
            courseName = "大学物理I_(2026春)",
            title = title,
            activityType = "作业",
            activityUrl = "https://phyvlab.bjtu.edu.cn/mod/assign/view.php?id=$id",
            dueTimestamp = dueTimestamp,
        )
}
