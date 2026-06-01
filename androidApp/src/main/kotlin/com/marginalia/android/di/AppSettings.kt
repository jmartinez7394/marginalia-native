package com.marginalia.android.di

import com.marginalia.settings.Setting
import com.marginalia.settings.SettingCategory

object AppSettings {

    // Reading display
    val READING_BG_COLOUR = Setting(
        key = "reading.backgroundColour",
        default = "white",
        userVisible = true,
        category = SettingCategory.READING,
        description = "Reading background colour (white, sepia, dark)"
    )

    // Ink
    val INK_SETTLING_DELAY_MS = Setting(
        key = "ink.settling_delay_ms",
        default = 500,
        userVisible = true,
        category = SettingCategory.WRITING,
        description = "Ms after pen lift before REGAL settle"
    )
    val INK_STROKE_BBOX_PADDING_PX = Setting(
        key = "ink.stroke_bbox_padding_px",
        default = 20,
        userVisible = false,
        category = SettingCategory.WRITING,
        description = "Padding around stroke bounding box for partial refresh"
    )

    // Refresh modes
    val REFRESH_SCROLL_PAUSE_MS = Setting(
        key = "refresh.scroll_pause_threshold_ms",
        default = 300,
        userVisible = false,
        category = SettingCategory.DISPLAY,
        description = "Ms of scroll pause before REGAL settle"
    )
    val REFRESH_PAGE_TURN_DELAY_MS = Setting(
        key = "refresh.page_turn_delay_ms",
        default = 0,
        userVisible = false,
        category = SettingCategory.DISPLAY,
        description = "Ms delay after page turn before GC16 fires (0–100)"
    )
    val REFRESH_WRITING_MODE_EXIT = Setting(
        key = "refresh.writing_mode_exit",
        default = "GC16",
        userVisible = false,
        category = SettingCategory.DISPLAY,
        description = "Refresh mode on writing mode exit"
    )
    val REFRESH_PAGE_TURN = Setting(
        key = "refresh.page_turn",
        default = "GC16",
        userVisible = false,
        category = SettingCategory.DISPLAY,
        description = "Refresh mode for page turns"
    )
    val REFRESH_CANVAS_PAN_SETTLE = Setting(
        key = "refresh.canvas_pan_settle",
        default = "REGAL",
        userVisible = false,
        category = SettingCategory.DISPLAY,
        description = "Refresh mode after canvas pan gesture ends"
    )
    val REFRESH_CANVAS_ZOOM_SETTLE = Setting(
        key = "refresh.canvas_zoom_settle",
        default = "GC16",
        userVisible = false,
        category = SettingCategory.DISPLAY,
        description = "Refresh mode after canvas zoom gesture ends"
    )

    // Concept registry
    val REGISTRY_SIGNAL_THRESHOLD = Setting(
        key = "registry.signal_threshold",
        default = 3,
        userVisible = false,
        category = SettingCategory.ADVANCED,
        description = "Number of occurrences before a concept candidate is surfaced for review"
    )
    val REGISTRY_DEFER_DAYS = Setting(
        key = "registry.defer_days",
        default = 7,
        userVisible = false,
        category = SettingCategory.ADVANCED,
        description = "Days to defer a concept candidate before re-surfacing"
    )

    // Debug
    val DEBUG_EINK_OVERLAY_ENABLED = Setting(
        key = "debug.eink_overlay_enabled",
        default = false,
        userVisible = false,
        category = SettingCategory.ADVANCED,
        description = "Show e-ink refresh mode debug overlay"
    )

    // Input
    val READING_VOLUME_KEYS_PAGE_TURN = Setting(
        key = "reading.volumeKeysForPageTurn",
        default = true,
        userVisible = true,
        category = SettingCategory.READING,
        description = "Use volume keys for page turn in reader"
    )

    val all: List<Setting<*>> = listOf(
        READING_BG_COLOUR,
        INK_SETTLING_DELAY_MS,
        INK_STROKE_BBOX_PADDING_PX,
        REFRESH_SCROLL_PAUSE_MS,
        REFRESH_PAGE_TURN_DELAY_MS,
        REFRESH_WRITING_MODE_EXIT,
        REFRESH_PAGE_TURN,
        REFRESH_CANVAS_PAN_SETTLE,
        REFRESH_CANVAS_ZOOM_SETTLE,
        REGISTRY_SIGNAL_THRESHOLD,
        REGISTRY_DEFER_DAYS,
        DEBUG_EINK_OVERLAY_ENABLED,
        READING_VOLUME_KEYS_PAGE_TURN
    )
}
