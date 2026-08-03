package team.bjtuss.bjtuselfservice.shared.system

interface DesktopWindowHandle {
    fun hide()
    fun showAndFocus()
}

/** Keeps the macOS application process and Compose tree alive while its window is hidden. */
class DesktopWindowLifecycle {
    private var handle: DesktopWindowHandle? = null

    fun attach(handle: DesktopWindowHandle) {
        this.handle = handle
    }

    fun detach(handle: DesktopWindowHandle) {
        if (this.handle === handle) this.handle = null
    }

    fun closeWindow(): Boolean = handle?.let {
        it.hide()
        true
    } ?: false

    fun reopenWindow(): Boolean = handle?.let {
        it.showAndFocus()
        true
    } ?: false
}
