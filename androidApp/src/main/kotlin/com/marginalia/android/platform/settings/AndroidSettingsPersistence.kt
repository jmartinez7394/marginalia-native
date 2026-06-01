package com.marginalia.android.platform.settings

import android.content.Context
import com.marginalia.settings.SettingsPersistence

class AndroidSettingsPersistence(context: Context) : SettingsPersistence {

    private val prefs = context.getSharedPreferences("marginalia_settings", Context.MODE_PRIVATE)

    @Suppress("UNCHECKED_CAST")
    override fun <T> read(key: String, default: T): T = when (default) {
        is String -> (prefs.getString(key, default) ?: default) as T
        is Int -> prefs.getInt(key, default) as T
        is Long -> prefs.getLong(key, default) as T
        is Boolean -> prefs.getBoolean(key, default) as T
        is Float -> prefs.getFloat(key, default) as T
        else -> default
    }

    override fun <T> write(key: String, value: T) {
        prefs.edit().apply {
            when (value) {
                is String -> putString(key, value)
                is Int -> putInt(key, value)
                is Long -> putLong(key, value)
                is Boolean -> putBoolean(key, value)
                is Float -> putFloat(key, value)
            }
            apply()
        }
    }
}
