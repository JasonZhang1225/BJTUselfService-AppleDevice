package team.bjtuss.bjtuselfservice.shared.data.home

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import team.bjtuss.bjtuselfservice.shared.domain.change.DataChangeKind
import team.bjtuss.bjtuselfservice.shared.domain.home.HomeChangeDomain
import team.bjtuss.bjtuselfservice.shared.domain.home.HomeChangeFeedSnapshot
import team.bjtuss.bjtuselfservice.shared.domain.home.HomeChangeRecord

class HomeChangeFeedCodecTest {
    @Test
    fun roundTripPreservesUnicodeDelimitersAndBaselines() {
        val snapshot = HomeChangeFeedSnapshot(
            baselineDomains = setOf(HomeChangeDomain.GRADES, HomeChangeDomain.HOMEWORK),
            records = listOf(
                HomeChangeRecord(
                    HomeChangeDomain.GRADES,
                    DataChangeKind.MODIFIED,
                    "高等数学:Ⅰ",
                    "80,旧",
                    "90,新",
                ),
            ),
        )

        assertEquals(snapshot, decodeHomeChangeFeed(encodeHomeChangeFeed(snapshot)))
    }

    @Test
    fun malformedOrOversizedPayloadIsRejected() {
        assertNull(decodeHomeChangeFeed("1:1"))
        assertNull(decodeHomeChangeFeed("1:11:02:10101:"))
    }
}
