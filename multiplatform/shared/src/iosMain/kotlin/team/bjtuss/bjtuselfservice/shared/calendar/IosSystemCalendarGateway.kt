package team.bjtuss.bjtuselfservice.shared.calendar

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.coroutines.resume
import platform.EventKit.EKCalendar
import platform.EventKit.EKEntityType
import platform.EventKit.EKEvent
import platform.EventKit.EKEventStore
import platform.EventKit.EKRecurrenceEnd
import platform.EventKit.EKRecurrenceFrequency
import platform.EventKit.EKRecurrenceRule
import platform.EventKit.EKSpan
import platform.Foundation.NSDate
import platform.Foundation.NSProcessInfo
import platform.Foundation.create
import platform.Foundation.timeIntervalSince1970
import team.bjtuss.bjtuselfservice.shared.domain.calendar.AcademicCalendarEvent

private const val EVENT_MARKER_PREFIX = "[BJTU-ID:"
private val BEIJING_TIME_ZONE = TimeZone.of("Asia/Shanghai")

/** iOS EventKit：仅在用户主动点“加入日历”时请求完整日历访问。 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class IosSystemCalendarGateway : SystemCalendarGateway {
    override val isAvailable: Boolean = true

    override suspend fun install(calendars: List<SystemCalendarBatch>): SystemCalendarInstallResult {
        if (calendars.isEmpty() || calendars.all { it.events.isEmpty() }) {
            return SystemCalendarInstallResult.Failed(SystemCalendarFailure.UNAVAILABLE)
        }
        val store = EKEventStore()
        val granted = requestCalendarAccess(store)
        if (!granted) return SystemCalendarInstallResult.Failed(SystemCalendarFailure.PERMISSION_DENIED)
        return runCatching { installAuthorized(store, calendars) }
            .getOrElse { SystemCalendarInstallResult.Failed(SystemCalendarFailure.IO) }
    }

    private suspend fun requestCalendarAccess(store: EKEventStore): Boolean =
        suspendCancellableCoroutine { continuation ->
            val completion: (Boolean, platform.Foundation.NSError?) -> Unit = { granted, _ ->
                if (continuation.isActive) continuation.resume(granted)
            }
            val majorVersion = NSProcessInfo.processInfo.operatingSystemVersion.useContents { majorVersion }
            if (majorVersion >= 17) {
                store.requestFullAccessToEventsWithCompletion(completion)
            } else {
                @Suppress("DEPRECATION")
                store.requestAccessToEntityType(EKEntityType.EKEntityTypeEvent, completion)
            }
        }

    private fun installAuthorized(
        store: EKEventStore,
        batches: List<SystemCalendarBatch>,
    ): SystemCalendarInstallResult {
        var calendarCount = 0
        var inserted = 0
        var updated = 0
        batches.filter { it.events.isNotEmpty() }.forEach { batch ->
            val calendar = findOrCreateCalendar(store, batch.name) ?: return SystemCalendarInstallResult.Failed(
                SystemCalendarFailure.IO,
            )
            calendarCount += 1
            val existing = existingManagedEvents(store, calendar, batch.events)
            val desiredMarkers = batch.events.mapTo(mutableSetOf()) { it.marker() }
            existing.all.forEach { (marker, event) ->
                if (marker.startsWith("$EVENT_MARKER_PREFIX" + "course-") && marker !in desiredMarkers) {
                    if (!store.removeEvent(event, EKSpan.EKSpanThisEvent, commit = false, error = null)) {
                        return SystemCalendarInstallResult.Failed(SystemCalendarFailure.IO)
                    }
                }
            }
            batch.events.forEach { draft ->
                val marker = draft.marker()
                val existingEvent = existing.primary[marker]
                val event = existingEvent ?: EKEvent.eventWithEventStore(store).also {
                    inserted += 1
                }
                if (existingEvent != null) updated += 1
                event.calendar = calendar
                event.title = draft.title
                event.startDate = draft.startLocal.toNSDate()
                event.endDate = draft.endLocal.toNSDate()
                event.location = draft.location.ifBlank { null }
                event.notes = buildString {
                    append(marker)
                    if (draft.notes.isNotBlank()) {
                        append('\n')
                        append(draft.notes)
                    }
                }
                event.allDay = false
                event.recurrenceRules
                    ?.filterIsInstance<EKRecurrenceRule>()
                    ?.forEach(event::removeRecurrenceRule)
                draft.recurrence?.takeIf { it.occurrenceCount > 1 }?.let { recurrence ->
                    val end = EKRecurrenceEnd.recurrenceEndWithOccurrenceCount(
                        recurrence.occurrenceCount.toULong(),
                    )
                    event.addRecurrenceRule(
                        EKRecurrenceRule(
                            recurrenceWithFrequency = EKRecurrenceFrequency.EKRecurrenceFrequencyWeekly,
                            interval = 1L,
                            end = end,
                        ),
                    )
                }
                val span = if (existingEvent != null && draft.recurrence != null) {
                    EKSpan.EKSpanFutureEvents
                } else {
                    EKSpan.EKSpanThisEvent
                }
                if (!store.saveEvent(event, span, commit = false, error = null)) {
                    return SystemCalendarInstallResult.Failed(SystemCalendarFailure.IO)
                }
            }
            // 先让重复规则进入数据库，才能按日期取到并删除停课/单双周 occurrence。
            if (!store.commit(null)) return SystemCalendarInstallResult.Failed(SystemCalendarFailure.IO)
            batch.events.forEach { draft ->
                val recurrence = draft.recurrence ?: return@forEach
                val marker = draft.marker()
                val duration = draft.endLocal.toNSDate().timeIntervalSince1970 -
                    draft.startLocal.toNSDate().timeIntervalSince1970
                recurrence.excludedStartLocals.forEach { excludedText ->
                    val excludedStart = excludedText.toNSDate()
                    val queryStart = NSDate.create(
                        timeIntervalSince1970 = excludedStart.timeIntervalSince1970 - 1.0,
                    )
                    val queryEnd = NSDate.create(
                        timeIntervalSince1970 = excludedStart.timeIntervalSince1970 + duration + 1.0,
                    )
                    val predicate = store.predicateForEventsWithStartDate(queryStart, queryEnd, listOf(calendar))
                    val occurrence = store.eventsMatchingPredicate(predicate)
                        .filterIsInstance<EKEvent>()
                        .firstOrNull { event ->
                            val eventStart = event.startDate ?: return@firstOrNull false
                            event.notes?.lineSequence()?.firstOrNull() == marker &&
                                kotlin.math.abs(eventStart.timeIntervalSince1970 - excludedStart.timeIntervalSince1970) < 1.0
                        }
                    if (occurrence != null && !store.removeEvent(
                            occurrence,
                            EKSpan.EKSpanThisEvent,
                            commit = false,
                            error = null,
                        )
                    ) {
                        return SystemCalendarInstallResult.Failed(SystemCalendarFailure.IO)
                    }
                }
            }
        }
        if (!store.commit(null)) return SystemCalendarInstallResult.Failed(SystemCalendarFailure.IO)
        return SystemCalendarInstallResult.Installed(calendarCount, inserted, updated)
    }

    private fun findOrCreateCalendar(store: EKEventStore, name: String): EKCalendar? {
        val existing = store.calendarsForEntityType(EKEntityType.EKEntityTypeEvent)
            .filterIsInstance<EKCalendar>()
            .firstOrNull { it.title == name && it.allowsContentModifications }
        if (existing != null) return existing
        val source = store.defaultCalendarForNewEvents?.source ?: return null
        val calendar = EKCalendar.calendarForEntityType(EKEntityType.EKEntityTypeEvent, store)
        calendar.title = name
        calendar.source = source
        return calendar.takeIf { store.saveCalendar(it, commit = true, error = null) }
    }

    private fun existingManagedEvents(
        store: EKEventStore,
        calendar: EKCalendar,
        drafts: List<AcademicCalendarEvent>,
    ): ExistingManagedEvents {
        // ISO 本地时间是固定宽度 yyyy-MM-ddTHH:mm:ss，可安全按字符串求时序端点。
        val start = drafts.minByOrNull { it.startLocal }?.startLocal?.toNSDate()
            ?: return ExistingManagedEvents(emptyMap(), emptyList())
        val latestText = drafts.maxOfOrNull { it.recurrence?.lastEndLocal ?: it.endLocal }
            ?: return ExistingManagedEvents(emptyMap(), emptyList())
        val latest = latestText.toNSDate()
        val end = NSDate.create(timeIntervalSince1970 = latest.timeIntervalSince1970 + 1.0)
        val predicate = store.predicateForEventsWithStartDate(start, end, listOf(calendar))
        val all = store.eventsMatchingPredicate(predicate)
            .filterIsInstance<EKEvent>()
            .mapNotNull { event ->
                val marker = event.notes?.lineSequence()?.firstOrNull()
                    ?.takeIf { it.startsWith(EVENT_MARKER_PREFIX) }
                marker?.let { it to event }
            }
        val primary = mutableMapOf<String, EKEvent>()
        all.forEach { (marker, event) ->
            val previous = primary[marker]
            val eventStart = event.startDate?.timeIntervalSince1970 ?: Double.POSITIVE_INFINITY
            val previousStart = previous?.startDate?.timeIntervalSince1970 ?: Double.POSITIVE_INFINITY
            if (previous == null || eventStart < previousStart) {
                primary[marker] = event
            }
        }
        return ExistingManagedEvents(primary, all)
    }

    private fun AcademicCalendarEvent.marker(): String = "$EVENT_MARKER_PREFIX$stableId]"

    private fun String.toNSDate(): NSDate {
        val instant = LocalDateTime.parse(this).toInstant(BEIJING_TIME_ZONE)
        return NSDate.create(
            timeIntervalSince1970 = instant.epochSeconds.toDouble() +
                instant.nanosecondsOfSecond / 1_000_000_000.0,
        )
    }
}

private data class ExistingManagedEvents(
    val primary: Map<String, EKEvent>,
    val all: List<Pair<String, EKEvent>>,
)
