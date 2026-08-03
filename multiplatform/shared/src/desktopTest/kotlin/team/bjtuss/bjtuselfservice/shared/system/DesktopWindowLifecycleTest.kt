package team.bjtuss.bjtuselfservice.shared.system

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopWindowLifecycleTest {
    @Test
    fun closeHidesAttachedWindowWithoutReplacingItsState() {
        val lifecycle = DesktopWindowLifecycle()
        val handle = FakeWindowHandle()
        handle.unsavedState = "lifecycle-check"
        lifecycle.attach(handle)

        assertTrue(lifecycle.closeWindow())

        assertFalse(handle.visible)
        assertEquals("lifecycle-check", handle.unsavedState)
    }

    @Test
    fun dockReopenShowsAndFocusesTheSameWindow() {
        val lifecycle = DesktopWindowLifecycle()
        val handle = FakeWindowHandle()
        lifecycle.attach(handle)
        lifecycle.closeWindow()

        assertTrue(lifecycle.reopenWindow())

        assertTrue(handle.visible)
        assertEquals(1, handle.focusCount)
    }

    @Test
    fun detachedWindowCannotReceiveLaterReopenEvent() {
        val lifecycle = DesktopWindowLifecycle()
        val handle = FakeWindowHandle()
        lifecycle.attach(handle)
        lifecycle.detach(handle)

        assertFalse(lifecycle.reopenWindow())
        assertEquals(0, handle.focusCount)
    }

    private class FakeWindowHandle : DesktopWindowHandle {
        var visible = true
        var focusCount = 0
        var unsavedState = ""

        override fun hide() {
            visible = false
        }

        override fun showAndFocus() {
            visible = true
            focusCount += 1
        }
    }
}
