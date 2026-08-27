package team.bjtuss.bjtuselfservice.shared.feature.scroll

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import team.bjtuss.bjtuselfservice.shared.PlatformFamily
import team.bjtuss.bjtuselfservice.shared.currentPlatform

/**
 * 让桌面端滚动容器也能消费 Windows 触摸兼容层送来的按住/移动指针事件。
 *
 * Compose Desktop 默认的滚动路径可靠覆盖鼠标滚轮，但 Windows 触摸屏/远程触摸
 * 兼容层不会给 AWT 发送滚轮事件。这里仅在桌面端为已有 ScrollableState 增加
 * 一层越过 touch-slop 后才生效的纵向拖动映射；普通点击、按钮和长按仍保留给
 * 子组件。Android/iOS 使用平台原生触摸滚动，不叠加这层。
 */
@Composable
fun Modifier.desktopTouchScroll(
    state: ScrollableState,
    orientation: Orientation = Orientation.Vertical,
    enabled: Boolean = currentPlatform().family == PlatformFamily.MacOS,
): Modifier {
    if (!enabled) return this
    val draggableState = rememberDraggableState { delta ->
        // dispatchRawDelta 是同步入口，避免每个指针采样都启动新的协程；
        // LazyListState 与 ScrollState 都实现 ScrollableState。
        state.dispatchRawDelta(-delta)
    }
    return draggable(
        state = draggableState,
        orientation = orientation,
    )
}
