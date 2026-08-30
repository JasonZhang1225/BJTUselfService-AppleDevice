package team.bjtuss.bjtuselfservice.kmp

import android.app.Activity
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup

/**
 * HyperOS 3 当前是 SWITCHING_TYPE_NONE，并且 mIgnorePreferredRefreshRate=true。
 * 窗口上的 preferredRefreshRate / preferredDisplayModeId 会被忽略，还会把应用
 * 标成「跟随应用内设置」，PowerKeeper 随后把前台刷新率投成 60Hz。
 *
 * 原版 App 不声明窗口刷新率，由小米高刷名单投 120Hz。这里对齐原版：
 * 清掉窗口刷新率声明，并关掉 Android 15 ARR 省电降帧。
 */
internal class AndroidRefreshRateController(private val activity: Activity) {
    private val handler = Handler(Looper.getMainLooper())
    private var displayManager: DisplayManager? = null
    private val listener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
        override fun onDisplayChanged(displayId: Int) {
            if (!activity.isFinishing) apply()
        }
    }
    private var started = false

    fun start() {
        if (started) {
            apply()
            return
        }
        started = true
        displayManager = activity.getSystemService(DisplayManager::class.java)
        apply()
        displayManager?.registerDisplayListener(listener, handler)
        activity.window.decorView.post { apply() }
    }

    fun stop() {
        if (!started) return
        started = false
        displayManager?.unregisterDisplayListener(listener)
        displayManager = null
    }

    fun apply() {
        clearAppRefreshRateOverride(activity)
    }
}

internal fun clearAppRefreshRateOverride(activity: Activity) {
    val window = activity.window
    if (Build.VERSION.SDK_INT >= 35) {
        window.setFrameRatePowerSavingsBalanced(false)
        window.setFrameRateBoostOnTouchEnabled(true)
    }
    val attrs = window.attributes
    attrs.preferredDisplayModeId = 0
    attrs.preferredRefreshRate = 0f
    window.attributes = attrs
    val decor = window.decorView
    if (decor.isAttachedToWindow) {
        clearViewFrameRate(decor)
    } else {
        decor.addOnAttachStateChangeListener(
            object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    v.removeOnAttachStateChangeListener(this)
                    clearViewFrameRate(v)
                }

                override fun onViewDetachedFromWindow(v: View) = Unit
            },
        )
    }
}

private fun clearViewFrameRate(view: View) {
    if (Build.VERSION.SDK_INT < 35) return
    view.setRequestedFrameRate(View.REQUESTED_FRAME_RATE_CATEGORY_NO_PREFERENCE)
    if (view is ViewGroup) {
        for (index in 0 until view.childCount) {
            clearViewFrameRate(view.getChildAt(index))
        }
    }
}
