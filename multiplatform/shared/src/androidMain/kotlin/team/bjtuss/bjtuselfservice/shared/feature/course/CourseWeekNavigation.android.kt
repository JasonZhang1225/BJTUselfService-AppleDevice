package team.bjtuss.bjtuselfservice.shared.feature.course

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * 宽屏课表在 Android 平板上走桌面布局，原先只接了桌面触摸板滚轮。
 * 这里补手指横滑翻周；超过 touch slop 后才消费事件，格子点击仍可用。
 * desktop actual 不走这条路径，Mac/Windows 保持原触摸板分页。
 */
internal actual fun Modifier.courseWeekScrollNavigation(
    accumulator: CourseWeekScrollAccumulator,
    onDirection: (CourseWeekScrollDirection) -> Unit,
): Modifier = composed {
    val latestDirection = rememberUpdatedState(onDirection)
    val density = LocalDensity.current
    val distanceThreshold = with(density) { 48.dp.toPx() }
    val velocityThreshold = with(density) { 700.dp.toPx() }
    pointerInput(distanceThreshold, velocityThreshold) {
        val touchSlop = viewConfiguration.touchSlop
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            var totalX = 0f
            var totalY = 0f
            var dragging = false
            val tracker = VelocityTracker()
            tracker.addPosition(down.uptimeMillis, down.position)
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) {
                    if (dragging) {
                        weekSwipeDirection(
                            displacementX = totalX,
                            displacementY = totalY,
                            velocityX = tracker.calculateVelocity().x,
                            distanceThreshold = distanceThreshold,
                            velocityThreshold = velocityThreshold,
                        )?.let(latestDirection.value)
                        accumulator.resetGesture()
                    }
                    break
                }
                val delta = change.positionChange()
                totalX += delta.x
                totalY += delta.y
                tracker.addPosition(change.uptimeMillis, change.position)
                if (!dragging) {
                    if (abs(totalY) > touchSlop && abs(totalY) > abs(totalX)) {
                        break
                    }
                    if (abs(totalX) > touchSlop && abs(totalX) > abs(totalY)) {
                        dragging = true
                        change.consume()
                    }
                } else {
                    change.consume()
                }
            }
        }
    }
}
