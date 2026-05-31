package com.marginalia.android.platform.display

import android.content.Context
import android.view.View
import com.marginalia.device.DisplayRefreshManager
import com.marginalia.device.RefreshBounds
import com.marginalia.device.RefreshMode

class AndroidDisplayRefreshManager(
    private val context: Context
) : DisplayRefreshManager {

    override var currentMode: RefreshMode = RefreshMode.GC16
        private set

    override fun refreshFull() {
        currentMode = RefreshMode.GC16
        tryOnyxRefresh(ONYX_MODE_GC16, null)
    }

    override fun refreshPartial(bounds: RefreshBounds) {
        currentMode = RefreshMode.REGAL
        tryOnyxRefresh(ONYX_MODE_REGAL, bounds)
    }

    override fun refreshFast() {
        currentMode = RefreshMode.A2
        tryOnyxRefresh(ONYX_MODE_A2, null)
    }

    override fun refreshDU() {
        currentMode = RefreshMode.DU
        tryOnyxRefresh(ONYX_MODE_DU, null)
    }

    private fun tryOnyxRefresh(mode: Int, bounds: RefreshBounds?) {
        try {
            val controllerClass = Class.forName("com.onyx.android.sdk.api.device.epd.EpdController")
            if (bounds != null) {
                val method = controllerClass.getMethod(
                    "refreshScreenRegion",
                    Context::class.java,
                    Int::class.java,
                    Int::class.java,
                    Int::class.java,
                    Int::class.java,
                    Int::class.java
                )
                method.invoke(null, context, mode, bounds.left, bounds.top, bounds.right, bounds.bottom)
            } else {
                val method = controllerClass.getMethod(
                    "refreshScreen",
                    Context::class.java,
                    Int::class.java
                )
                method.invoke(null, context, mode)
            }
        } catch (e: Exception) {
            // SDK not present — no-op on non-e-ink devices.
        }
    }

    companion object {
        private const val ONYX_MODE_GC16 = 2
        private const val ONYX_MODE_REGAL = 9
        private const val ONYX_MODE_A2 = 4
        private const val ONYX_MODE_DU = 1
    }
}
