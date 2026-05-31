package com.marginalia.device

class FakeDeviceCapabilities(
    override val displayType: DisplayType = DisplayType.EINK,
    override val hasEMRStylus: Boolean = true,
    override val hasPhysicalPageButtons: Boolean = false,
    override val manufacturer: String = "Test",
    override val supportsPartialRefresh: Boolean = true
) : DeviceCapabilities
