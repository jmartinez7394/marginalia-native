package com.marginalia.device

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeviceCapabilitiesTest {

    @Test
    fun `FakeDeviceCapabilities defaults to EINK with EMR stylus`() {
        val capabilities = FakeDeviceCapabilities()
        assertEquals(DisplayType.EINK, capabilities.displayType)
        assertTrue(capabilities.hasEMRStylus)
        assertTrue(capabilities.supportsPartialRefresh)
    }

    @Test
    fun `FakeDeviceCapabilities can be configured for LCD without stylus`() {
        val capabilities = FakeDeviceCapabilities(
            displayType = DisplayType.LCD,
            hasEMRStylus = false,
            supportsPartialRefresh = false
        )
        assertEquals(DisplayType.LCD, capabilities.displayType)
        assertFalse(capabilities.hasEMRStylus)
        assertFalse(capabilities.supportsPartialRefresh)
    }

    @Test
    fun `FakeDeviceCapabilities can override manufacturer`() {
        val capabilities = FakeDeviceCapabilities(manufacturer = "Onyx Boox")
        assertEquals("Onyx Boox", capabilities.manufacturer)
    }

    @Test
    fun `FakeDeviceCapabilities hasPhysicalPageButtons defaults to false`() {
        val capabilities = FakeDeviceCapabilities()
        assertFalse(capabilities.hasPhysicalPageButtons)
    }

    @Test
    fun `FakeDeviceCapabilities can configure physical page buttons`() {
        val capabilities = FakeDeviceCapabilities(hasPhysicalPageButtons = true)
        assertTrue(capabilities.hasPhysicalPageButtons)
    }
}
