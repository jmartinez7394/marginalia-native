package com.marginalia.settings

interface SettingsPersistence {
    fun <T> read(key: String, default: T): T
    fun <T> write(key: String, value: T)
}
