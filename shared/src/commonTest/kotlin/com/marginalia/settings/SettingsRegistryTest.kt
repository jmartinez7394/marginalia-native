package com.marginalia.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsRegistryTest {

    private class FakeSettingsPersistence : SettingsPersistence {
        private val store = mutableMapOf<String, Any?>()

        @Suppress("UNCHECKED_CAST")
        override fun <T> read(key: String, default: T): T =
            (store.getOrDefault(key, default)) as T

        override fun <T> write(key: String, value: T) {
            store[key] = value
        }
    }

    private fun registry() = SettingsRegistry(FakeSettingsPersistence())

    @Test
    fun getReturnsDefaultBeforeSet() {
        val registry = registry()
        val setting = Setting("reading.font_size", 16, true, SettingCategory.READING, "Body font size")
        registry.register(setting)
        assertEquals(16, registry.get(setting))
    }

    @Test
    fun setThenGetReturnsNewValue() {
        val registry = registry()
        val setting = Setting("reading.font_size", 16, true, SettingCategory.READING, "Body font size")
        registry.register(setting)
        registry.set(setting, 20)
        assertEquals(20, registry.get(setting))
    }

    @Test
    fun registerReturnsSetting() {
        val registry = registry()
        val setting = Setting("sync.auto", false, false, SettingCategory.SYNC, "Auto sync")
        val returned = registry.register(setting)
        assertEquals(setting, returned)
    }

    @Test
    fun getAllUserVisibleFiltersCorrectly() {
        val registry = registry()
        val visible = Setting("display.theme", "light", true, SettingCategory.DISPLAY, "Theme")
        val hidden = Setting("advanced.debug", false, false, SettingCategory.ADVANCED, "Debug mode")
        registry.register(visible)
        registry.register(hidden)
        val result = registry.getAllUserVisible()
        assertTrue(result.contains(visible))
        assertFalse(result.contains(hidden))
    }

    @Test
    fun multipleSettingsAreIndependent() {
        val registry = registry()
        val s1 = Setting("a", 1, true, SettingCategory.READING, "A")
        val s2 = Setting("b", 2, true, SettingCategory.READING, "B")
        registry.register(s1)
        registry.register(s2)
        registry.set(s1, 10)
        assertEquals(10, registry.get(s1))
        assertEquals(2, registry.get(s2))
    }
}
