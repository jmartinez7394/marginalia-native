package com.marginalia.animachora

data class AnimaChora(
    val name: String,
    val territories: List<Territory>,
    val crossTerritoryLinks: List<CrossTerritoryLink>,
    val createdAt: Long,
    val lastUpdatedAt: Long
)

data class CrossTerritoryLink(
    val id: String,
    val fromTerritoryId: String,
    val fromConceptName: String,
    val toTerritoryId: String,
    val toConceptName: String,
    val note: String?,
    val createdAt: Long
)
