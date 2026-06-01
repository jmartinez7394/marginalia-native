package com.marginalia.device

interface DisplayRefreshManager {
    fun refreshFull()
    fun refreshRegalFull()
    fun refreshPartial(bounds: RefreshBounds)
    fun refreshFast()
    fun refreshDU()
    val currentMode: RefreshMode
}

enum class RefreshMode { GC16, REGAL, A2, DU }

data class RefreshBounds(val left: Int, val top: Int, val right: Int, val bottom: Int)

class NoOpDisplayRefreshManager : DisplayRefreshManager {
    override var currentMode: RefreshMode = RefreshMode.GC16
    override fun refreshFull() {}
    override fun refreshRegalFull() {}
    override fun refreshPartial(bounds: RefreshBounds) {}
    override fun refreshFast() {}
    override fun refreshDU() {}
}
