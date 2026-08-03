package team.bjtuss.bjtuselfservice.shared.domain.change

import kotlinx.coroutines.CancellationException

enum class DataChangeKind { ADDED, MODIFIED, DELETED }

data class DataItemChange<T>(
    val kind: DataChangeKind,
    val before: T? = null,
    val after: T? = null,
) {
    init {
        require(
            when (kind) {
                DataChangeKind.ADDED -> before == null && after != null
                DataChangeKind.MODIFIED -> before != null && after != null
                DataChangeKind.DELETED -> before != null && after == null
            },
        )
    }
}

fun interface DataChangeRecorder<T> {
    suspend fun record(before: List<T>, after: List<T>)
}

suspend fun <T> DataChangeRecorder<T>?.recordSafely(before: List<T>, after: List<T>) {
    val recorder = this ?: return
    try {
        recorder.record(before, after)
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        // The auxiliary feed must not turn a successful school-data refresh into a failure.
    }
}

/**
 * Pairs duplicate identities by occurrence order instead of silently dropping them through associateBy.
 * New-list order is retained for added/modified items; old-list order is retained for deleted items.
 */
fun <T, K> detectDataChanges(
    before: List<T>,
    after: List<T>,
    identity: (T) -> K,
    equivalent: (T, T) -> Boolean = { old, new -> old == new },
): List<DataItemChange<T>> {
    val beforeGroups = before.groupBy(identity)
    val afterGroups = after.groupBy(identity)
    val orderedKeys = LinkedHashSet<K>().apply {
        after.forEach { add(identity(it)) }
        before.forEach { add(identity(it)) }
    }
    return buildList {
        orderedKeys.forEach { key ->
            val oldItems = beforeGroups[key].orEmpty()
            val newItems = afterGroups[key].orEmpty()
            val paired = minOf(oldItems.size, newItems.size)
            repeat(paired) { index ->
                val old = oldItems[index]
                val new = newItems[index]
                if (!equivalent(old, new)) {
                    add(DataItemChange(DataChangeKind.MODIFIED, before = old, after = new))
                }
            }
            newItems.drop(paired).forEach { add(DataItemChange(DataChangeKind.ADDED, after = it)) }
            oldItems.drop(paired).forEach { add(DataItemChange(DataChangeKind.DELETED, before = it)) }
        }
    }
}
