package com.marginalia.settings

class SettingsRegistry(private val persistence: SettingsPersistence) {

    private val registered = mutableMapOf<String, Setting<*>>()

    fun <T> register(setting: Setting<T>): Setting<T> {
        registered[setting.key] = setting
        return setting
    }

    fun <T> get(setting: Setting<T>): T = persistence.read(setting.key, setting.default)

    fun <T> set(setting: Setting<T>, value: T) {
        persistence.write(setting.key, value)
    }

    fun getAllUserVisible(): List<Setting<*>> = registered.values.filter { it.userVisible }
}
