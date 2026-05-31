package com.marginalia.android.platform.device

import android.content.Context
import android.hardware.input.InputManager
import android.os.Build
import android.view.InputDevice
import com.marginalia.device.DeviceCapabilities
import com.marginalia.device.DisplayType

class AndroidDeviceCapabilities(context: Context) : DeviceCapabilities {

    override val displayType: DisplayType = detectDisplayType()
    override val manufacturer: String = Build.MANUFACTURER ?: "unknown"
    override val hasEMRStylus: Boolean = detectEMRStylus(context)
    override val hasPhysicalPageButtons: Boolean = detectPhysicalPageButtons()
    override val supportsPartialRefresh: Boolean =
        displayType == DisplayType.EINK && isOnyxSdkAvailable()

    companion object {
        private val KNOWN_EINK_MANUFACTURERS = setOf(
            "onyx", "boox", "remarkable", "supernote", "kobo", "kindle"
        )

        private fun isOnyxSdkAvailable(): Boolean = try {
            Class.forName("com.onyx.android.sdk.api.device.epd.EpdController")
            true
        } catch (e: ClassNotFoundException) {
            false
        }

        private fun detectDisplayType(): DisplayType {
            if (isOnyxSdkAvailable()) return DisplayType.EINK

            val mfr = Build.MANUFACTURER?.lowercase() ?: ""
            if (KNOWN_EINK_MANUFACTURERS.any { mfr.contains(it) }) return DisplayType.EINK

            return DisplayType.UNKNOWN
        }

        private fun detectEMRStylus(context: Context): Boolean {
            val mfr = Build.MANUFACTURER?.lowercase() ?: ""
            if (mfr.contains("onyx") || mfr.contains("boox")) return true

            val inputManager = context.getSystemService(Context.INPUT_SERVICE) as? InputManager
            val deviceIds = inputManager?.inputDeviceIds ?: return false
            return deviceIds.any { id ->
                val device = inputManager.getInputDevice(id)
                device?.sources?.let {
                    (it and InputDevice.SOURCE_STYLUS) == InputDevice.SOURCE_STYLUS
                } ?: false
            }
        }

        private fun detectPhysicalPageButtons(): Boolean {
            val mfr = Build.MANUFACTURER?.lowercase() ?: ""
            return mfr.contains("onyx") || mfr.contains("boox")
        }
    }
}
