package team.bjtuss.bjtuselfservice.shared.data.home

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import team.bjtuss.bjtuselfservice.shared.cache.CacheStore
import team.bjtuss.bjtuselfservice.shared.domain.change.DataChangeKind
import team.bjtuss.bjtuselfservice.shared.domain.change.DataChangeRecorder
import team.bjtuss.bjtuselfservice.shared.domain.change.detectDataChanges
import team.bjtuss.bjtuselfservice.shared.domain.course.Course
import team.bjtuss.bjtuselfservice.shared.domain.exam.ExamSchedule
import team.bjtuss.bjtuselfservice.shared.domain.grade.Grade
import team.bjtuss.bjtuselfservice.shared.domain.home.HomeChangeDomain
import team.bjtuss.bjtuselfservice.shared.domain.home.HomeChangeFeedSnapshot
import team.bjtuss.bjtuselfservice.shared.domain.home.HomeChangeRecord
import team.bjtuss.bjtuselfservice.shared.domain.homework.Homework

private const val HOME_CHANGE_FEED_KEY = "home_change_feed_v1"
private const val MAX_RECORDS = 100

interface HomeChangeFeedRepository {
    val records: StateFlow<List<HomeChangeRecord>>
    suspend fun acceptRefresh(
        domain: HomeChangeDomain,
        hadPreviousItems: Boolean,
        changes: List<HomeChangeRecord>,
    ): Boolean
    suspend fun clear(domain: HomeChangeDomain? = null): Boolean
}

class CacheStoreHomeChangeFeedRepository(
    accountScope: String,
    private val cacheStore: CacheStore,
) : HomeChangeFeedRepository {
    private val accountScope = accountScope.trim().also {
        require(it.isNotEmpty()) { "accountScope cannot be blank" }
    }
    private val mutex = Mutex()
    private var snapshot = runCatching {
        cacheStore.metadata(this.accountScope, HOME_CHANGE_FEED_KEY)
            ?.let(::decodeHomeChangeFeed)
    }.getOrNull() ?: HomeChangeFeedSnapshot()
    private val mutableRecords = MutableStateFlow(snapshot.records)
    override val records: StateFlow<List<HomeChangeRecord>> = mutableRecords.asStateFlow()

    override suspend fun acceptRefresh(
        domain: HomeChangeDomain,
        hadPreviousItems: Boolean,
        changes: List<HomeChangeRecord>,
    ): Boolean = mutex.withLock {
        val alreadyBaselined = domain in snapshot.baselineDomains
        val shouldAppend = alreadyBaselined || hadPreviousItems
        val combined = if (shouldAppend) {
            (snapshot.records + changes).distinctBy(HomeChangeRecord::stableKey).takeLast(MAX_RECORDS)
        } else {
            snapshot.records
        }
        persist(snapshot.copy(baselineDomains = snapshot.baselineDomains + domain, records = combined))
    }

    override suspend fun clear(domain: HomeChangeDomain?): Boolean = mutex.withLock {
        val remaining = if (domain == null) emptyList() else snapshot.records.filterNot { it.domain == domain }
        persist(snapshot.copy(records = remaining))
    }

    private fun persist(updated: HomeChangeFeedSnapshot): Boolean = try {
        cacheStore.putMetadata(accountScope, HOME_CHANGE_FEED_KEY, encodeHomeChangeFeed(updated))
        snapshot = updated
        mutableRecords.value = updated.records
        true
    } catch (_: Exception) {
        false
    }
}

fun gradeChangeRecorder(feed: HomeChangeFeedRepository): DataChangeRecorder<Grade> =
    changeRecorder(
        feed = feed,
        domain = HomeChangeDomain.GRADES,
        identity = { listOf(it.courseName, it.courseTeacher, it.courseYear, it.semester) },
        // 只比分数/学分/学期等业务字段。detail（组成与说明）会因解析 <br>/空白变化而抖动，
        // 不应冒充「成绩变动」；id 是本地库生成的，也必须忽略。
        equivalent = { old, new -> gradesSemanticallyEqual(old, new) },
        title = Grade::courseName,
        detail = { "${it.courseScore} · ${it.courseCredits} 学分 · ${it.semester}" },
    )

/** 成绩信息流等价：忽略本地 id 与详情 HTML 文本差异。 */
internal fun gradesSemanticallyEqual(old: Grade, new: Grade): Boolean =
    old.courseName == new.courseName &&
        old.courseTeacher == new.courseTeacher &&
        old.courseScore == new.courseScore &&
        old.courseCredits == new.courseCredits &&
        old.courseYear == new.courseYear &&
        old.semester == new.semester

fun courseChangeRecorder(feed: HomeChangeFeedRepository): DataChangeRecorder<Course> =
    changeRecorder(
        feed = feed,
        domain = HomeChangeDomain.COURSES,
        identity = { listOf(it.courseId, it.isCurrentSemester, it.courseTime, it.courseLocationIndex) },
        equivalent = { old, new -> old.copy(id = 0) == new.copy(id = 0) },
        title = Course::courseName,
        detail = { "${it.courseTeacher} · ${it.courseTime} · ${it.coursePlace}" },
    )

fun examChangeRecorder(feed: HomeChangeFeedRepository): DataChangeRecorder<ExamSchedule> =
    changeRecorder(
        feed = feed,
        domain = HomeChangeDomain.EXAMS,
        identity = { listOf(it.examType, it.courseName) },
        equivalent = { old, new -> old.copy(id = 0) == new.copy(id = 0) },
        title = ExamSchedule::courseName,
        detail = { "${it.examType} · ${it.examTimeAndPlace} · ${it.examStatus}" },
    )

fun homeworkChangeRecorder(feed: HomeChangeFeedRepository): DataChangeRecorder<Homework> =
    changeRecorder(
        feed = feed,
        domain = HomeChangeDomain.HOMEWORK,
        identity = { listOf(it.courseName, it.upId) },
        equivalent = { old, new ->
            old.copy(id = 0, idSnId = null) == new.copy(id = 0, idSnId = null)
        },
        title = Homework::title,
        detail = { "${it.courseName} · ${it.endTime} · ${it.subStatus}" },
    )

private fun <T, K> changeRecorder(
    feed: HomeChangeFeedRepository,
    domain: HomeChangeDomain,
    identity: (T) -> K,
    equivalent: (T, T) -> Boolean,
    title: (T) -> String,
    detail: (T) -> String,
): DataChangeRecorder<T> = DataChangeRecorder { before, after ->
    val records = detectDataChanges(before, after, identity, equivalent).map { change ->
        val displayItem = change.after ?: change.before ?: error("change has no item")
        HomeChangeRecord(
            domain = domain,
            kind = change.kind,
            title = title(displayItem).ifBlank { domain.title },
            beforeDetail = change.before?.let(detail).orEmpty(),
            afterDetail = change.after?.let(detail).orEmpty(),
        )
    }
        // 二次保险：展示文案完全一致的「修改」不进信息流（避免解析抖动误报）。
        .filterNot { record ->
            record.kind == DataChangeKind.MODIFIED &&
                record.beforeDetail == record.afterDetail
        }
    feed.acceptRefresh(domain, before.isNotEmpty(), records)
}

internal fun encodeHomeChangeFeed(snapshot: HomeChangeFeedSnapshot): String = buildString {
    writePart("1")
    writePart(snapshot.baselineDomains.joinToString(",", transform = HomeChangeDomain::name))
    writePart(snapshot.records.size.toString())
    snapshot.records.forEach { record ->
        writePart(record.domain.name)
        writePart(record.kind.name)
        writePart(record.title)
        writePart(record.beforeDetail)
        writePart(record.afterDetail)
    }
}

internal fun decodeHomeChangeFeed(encoded: String): HomeChangeFeedSnapshot? = try {
    val reader = LengthPrefixedReader(encoded)
    if (reader.read() != "1") return null
    val baselines = reader.read().takeIf(String::isNotEmpty)?.split(',').orEmpty()
        .map { HomeChangeDomain.valueOf(it) }.toSet()
    val count = reader.read().toInt().takeIf { it in 0..MAX_RECORDS } ?: return null
    val records = buildList {
        repeat(count) {
            add(
                HomeChangeRecord(
                    domain = HomeChangeDomain.valueOf(reader.read()),
                    kind = DataChangeKind.valueOf(reader.read()),
                    title = reader.read(),
                    beforeDetail = reader.read(),
                    afterDetail = reader.read(),
                ),
            )
        }
    }
    if (!reader.finished) return null
    HomeChangeFeedSnapshot(baselines, records)
} catch (_: Exception) {
    null
}

private fun StringBuilder.writePart(value: String) {
    append(value.length).append(':').append(value)
}

private class LengthPrefixedReader(private val value: String) {
    private var index = 0
    val finished: Boolean get() = index == value.length

    fun read(): String {
        val colon = value.indexOf(':', index).takeIf { it >= index } ?: error("missing length")
        val length = value.substring(index, colon).toInt().takeIf { it >= 0 } ?: error("bad length")
        val start = colon + 1
        val end = start + length
        require(end <= value.length)
        index = end
        return value.substring(start, end)
    }
}
