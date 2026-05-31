package com.marginalia.settings

data class Setting<T>(
    val key: String,
    val default: T,
    val userVisible: Boolean,
    val category: SettingCategory,
    val description: String,
    val validator: ((T) -> Boolean)? = null
)

enum class SettingCategory {
    READING, WRITING, SYNC, DISPLAY, PRIVACY, ADVANCED
}
