package com.marginalia.device

interface DeviceCapabilities {
    val displayType: DisplayType
    val hasEMRStylus: Boolean
    val hasPhysicalPageButtons: Boolean
    val manufacturer: String
    val supportsPartialRefresh: Boolean
}

enum class DisplayType { EINK, AMOLED, LCD, UNKNOWN }
