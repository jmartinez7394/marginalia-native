package com.marginalia.animachora

data class Territory(
    val id: String,
    val name: String,
    val type: TerritoryType,
    val folderPath: String,
    val symbol: TerritorySymbol,
    val shade: TerritoryShade,
    val colour: String?,
    val ghostSymbolOpacity: Int,
    val isPrivate: Boolean,
    val createdAt: Long,
    val lastEnteredAt: Long?
)

enum class TerritoryType {
    LIBRARY, STUDY, WORKSHOP, ARCHIVE, PRACTICE,
    CHAPEL, OBSERVATORY, ATELIER, CLINIC, ANTECHAMBER, CUSTOM
}

enum class TerritorySymbol {
    LAMP, NOTEBOOK, COMPASS, MAGNIFIER, CANDLE,
    HANDS, TELESCOPE, BRUSH, CHAIRS, DOORWAY, CUSTOM
}

enum class TerritoryShade { WARM, COOL, NEUTRAL, DARK, LIGHT }
