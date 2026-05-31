package com.marginalia.animachora

object TerritoryDefaults {

    fun defaultSymbol(type: TerritoryType): TerritorySymbol = when (type) {
        TerritoryType.LIBRARY -> TerritorySymbol.LAMP
        TerritoryType.STUDY -> TerritorySymbol.MAGNIFIER
        TerritoryType.WORKSHOP -> TerritorySymbol.COMPASS
        TerritoryType.ARCHIVE -> TerritorySymbol.NOTEBOOK
        TerritoryType.PRACTICE -> TerritorySymbol.HANDS
        TerritoryType.CHAPEL -> TerritorySymbol.CANDLE
        TerritoryType.OBSERVATORY -> TerritorySymbol.TELESCOPE
        TerritoryType.ATELIER -> TerritorySymbol.BRUSH
        TerritoryType.CLINIC -> TerritorySymbol.CHAIRS
        TerritoryType.ANTECHAMBER -> TerritorySymbol.DOORWAY
        TerritoryType.CUSTOM -> TerritorySymbol.CUSTOM
    }

    fun defaultShade(type: TerritoryType): TerritoryShade = when (type) {
        TerritoryType.LIBRARY -> TerritoryShade.WARM
        TerritoryType.STUDY -> TerritoryShade.NEUTRAL
        TerritoryType.WORKSHOP -> TerritoryShade.COOL
        TerritoryType.ARCHIVE -> TerritoryShade.NEUTRAL
        TerritoryType.PRACTICE -> TerritoryShade.WARM
        TerritoryType.CHAPEL -> TerritoryShade.DARK
        TerritoryType.OBSERVATORY -> TerritoryShade.COOL
        TerritoryType.ATELIER -> TerritoryShade.LIGHT
        TerritoryType.CLINIC -> TerritoryShade.NEUTRAL
        TerritoryType.ANTECHAMBER -> TerritoryShade.NEUTRAL
        TerritoryType.CUSTOM -> TerritoryShade.NEUTRAL
    }

    fun isPrivateByDefault(type: TerritoryType): Boolean = when (type) {
        TerritoryType.PRACTICE, TerritoryType.CHAPEL, TerritoryType.CLINIC -> true
        else -> false
    }
}
