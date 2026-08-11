package team.bjtuss.bjtuselfservice.shared.feature.course

import androidx.compose.ui.Modifier
import kotlin.math.abs

internal enum class CourseWeekScrollDirection {
    PREVIOUS,
    NEXT,
}

/** 触摸板滚轮事件只由 desktop actual 接入；移动端保持原 Modifier。 */
internal expect fun Modifier.courseWeekScrollNavigation(
    accumulator: CourseWeekScrollAccumulator,
    onDirection: (CourseWeekScrollDirection) -> Unit,
): Modifier

/** 把触摸板横向位移按距离分页；不等待 macOS 长短不定的惯性尾流“结束”。 */
internal class CourseWeekScrollAccumulator(
    private val threshold: Float = 36f,
    private val quietGapMillis: Long = 80L,
    private val minTurnIntervalMillis: Long = 180L,
) {
    private var accumulatedX = 0f
    private var lastEventMillis = Long.MIN_VALUE
    private var lastTurnMillis = Long.MIN_VALUE
    private var lastTurnPositive: Boolean? = null

    fun resetGesture() {
        accumulatedX = 0f
        lastEventMillis = Long.MIN_VALUE
        lastTurnMillis = Long.MIN_VALUE
        lastTurnPositive = null
    }

    fun add(
        deltaX: Float,
        deltaY: Float,
        eventTimeMillis: Long,
    ): CourseWeekScrollDirection? {
        val hasQuietGap = lastEventMillis != Long.MIN_VALUE &&
            eventTimeMillis - lastEventMillis > quietGapMillis
        if (hasQuietGap) {
            resetGesture()
        }
        lastEventMillis = eventTimeMillis

        if (abs(deltaX) <= abs(deltaY) || deltaX == 0f) {
            accumulatedX = 0f
            return null
        }
        if (accumulatedX != 0f && (accumulatedX > 0f) != (deltaX > 0f)) {
            accumulatedX = 0f
            // 反向输入是新的明确意图，不受上一页的节流影响。
            lastTurnMillis = Long.MIN_VALUE
        }
        accumulatedX += deltaX
        if (abs(accumulatedX) < threshold) return null

        if (lastTurnPositive != null && lastTurnPositive != (accumulatedX > 0f)) {
            lastTurnMillis = Long.MIN_VALUE
        }
        if (lastTurnMillis != Long.MIN_VALUE &&
            eventTimeMillis - lastTurnMillis < minTurnIntervalMillis
        ) {
            // 丢弃刚翻页后的惯性位移；不把它保留到节流结束后突然补翻一页。
            accumulatedX = 0f
            return null
        }
        val direction = if (accumulatedX > 0f) {
            CourseWeekScrollDirection.NEXT
        } else {
            CourseWeekScrollDirection.PREVIOUS
        }
        accumulatedX = 0f
        lastTurnMillis = eventTimeMillis
        lastTurnPositive = direction == CourseWeekScrollDirection.NEXT
        return direction
    }
}
