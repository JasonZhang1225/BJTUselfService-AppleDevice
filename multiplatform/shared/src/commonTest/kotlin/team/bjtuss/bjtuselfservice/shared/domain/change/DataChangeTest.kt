package team.bjtuss.bjtuselfservice.shared.domain.change

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class DataChangeTest {
    @Test
    fun detectsAddedModifiedAndDeletedInStableOrder() {
        val changes = detectDataChanges(
            before = listOf(Item("a", "old"), Item("b", "gone")),
            after = listOf(Item("a", "new"), Item("c", "added")),
            identity = Item::key,
        )

        assertEquals(
            listOf(DataChangeKind.MODIFIED, DataChangeKind.ADDED, DataChangeKind.DELETED),
            changes.map(DataItemChange<Item>::kind),
        )
    }

    @Test
    fun duplicateIdentitiesArePairedByOccurrenceInsteadOfDropped() {
        val changes = detectDataChanges(
            before = listOf(Item("same", "one"), Item("same", "two")),
            after = listOf(Item("same", "one"), Item("same", "changed"), Item("same", "three")),
            identity = Item::key,
        )

        assertEquals(listOf(DataChangeKind.MODIFIED, DataChangeKind.ADDED), changes.map { it.kind })
        assertEquals("two", changes.first().before?.value)
        assertEquals("changed", changes.first().after?.value)
    }

    @Test
    fun auxiliaryRecorderFailureDoesNotBreakSuccessfulRefresh() = runBlocking {
        val recorder = DataChangeRecorder<Int> { _, _ -> error("cache unavailable") }

        recorder.recordSafely(listOf(1), listOf(2))
    }

    private data class Item(val key: String, val value: String)
}
