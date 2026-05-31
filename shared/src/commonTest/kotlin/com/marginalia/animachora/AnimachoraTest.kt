package com.marginalia.animachora

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class AnimachoraTest {

    private fun makeTerritory(
        id: String = "t1",
        type: TerritoryType = TerritoryType.LIBRARY
    ) = Territory(
        id = id,
        name = "Test Territory",
        type = type,
        folderPath = "library",
        symbol = TerritoryDefaults.defaultSymbol(type),
        shade = TerritoryDefaults.defaultShade(type),
        colour = null,
        ghostSymbolOpacity = 5,
        isPrivate = TerritoryDefaults.isPrivateByDefault(type),
        createdAt = 1000L,
        lastEnteredAt = null
    )

    // TerritoryDefaults — privacy
    @Test
    fun `PRACTICE is private by default`() {
        assertTrue(TerritoryDefaults.isPrivateByDefault(TerritoryType.PRACTICE))
    }

    @Test
    fun `CHAPEL is private by default`() {
        assertTrue(TerritoryDefaults.isPrivateByDefault(TerritoryType.CHAPEL))
    }

    @Test
    fun `CLINIC is private by default`() {
        assertTrue(TerritoryDefaults.isPrivateByDefault(TerritoryType.CLINIC))
    }

    @Test
    fun `all other territory types are public by default`() {
        val publicTypes = listOf(
            TerritoryType.LIBRARY,
            TerritoryType.STUDY,
            TerritoryType.WORKSHOP,
            TerritoryType.ARCHIVE,
            TerritoryType.OBSERVATORY,
            TerritoryType.ATELIER,
            TerritoryType.ANTECHAMBER,
            TerritoryType.CUSTOM
        )
        publicTypes.forEach { type ->
            assertFalse(TerritoryDefaults.isPrivateByDefault(type), "$type should be public by default")
        }
    }

    // TerritoryDefaults — symbols and shades
    @Test
    fun `defaultSymbol returns a value for every TerritoryType`() {
        TerritoryType.entries.forEach { type ->
            val symbol = TerritoryDefaults.defaultSymbol(type)
            assertTrue(TerritorySymbol.entries.contains(symbol), "Missing symbol for $type")
        }
    }

    @Test
    fun `defaultShade returns a value for every TerritoryType`() {
        TerritoryType.entries.forEach { type ->
            val shade = TerritoryDefaults.defaultShade(type)
            assertTrue(TerritoryShade.entries.contains(shade), "Missing shade for $type")
        }
    }

    // AnimaChora — territory operations
    @Test
    fun `AnimaChora can be created with territories`() {
        val lib = makeTerritory("t1", TerritoryType.LIBRARY)
        val workshop = makeTerritory("t2", TerritoryType.WORKSHOP)
        val animachora = AnimaChora(
            name = "My AnimaChora",
            territories = listOf(lib, workshop),
            crossTerritoryLinks = emptyList(),
            createdAt = 1000L,
            lastUpdatedAt = 2000L
        )
        assertEquals(2, animachora.territories.size)
        assertEquals("My AnimaChora", animachora.name)
    }

    @Test
    fun `adding a territory produces an updated territory list`() {
        val lib = makeTerritory("t1", TerritoryType.LIBRARY)
        val animachora = AnimaChora(
            name = "My AnimaChora",
            territories = listOf(lib),
            crossTerritoryLinks = emptyList(),
            createdAt = 1000L,
            lastUpdatedAt = 1000L
        )
        val workshop = makeTerritory("t2", TerritoryType.WORKSHOP)
        val updated = animachora.copy(
            territories = animachora.territories + workshop,
            lastUpdatedAt = 2000L
        )
        assertEquals(2, updated.territories.size)
        assertTrue(updated.territories.any { it.id == "t2" })
    }

    // CrossTerritoryLink — directionality
    @Test
    fun `CrossTerritoryLink is directional — from is not to`() {
        val link = CrossTerritoryLink(
            id = "link-1",
            fromTerritoryId = "t1",
            fromConceptName = "Virtue Ethics",
            toTerritoryId = "t2",
            toConceptName = "Practical Wisdom",
            note = "Connected by application",
            createdAt = 1000L
        )
        assertNotEquals(link.fromTerritoryId, link.toTerritoryId)
        assertEquals("t1", link.fromTerritoryId)
        assertEquals("t2", link.toTerritoryId)
    }

    @Test
    fun `CrossTerritoryLink note can be null`() {
        val link = CrossTerritoryLink(
            id = "link-2",
            fromTerritoryId = "t1",
            fromConceptName = "Logos",
            toTerritoryId = "t3",
            toConceptName = "Word",
            note = null,
            createdAt = 1000L
        )
        assertEquals(null, link.note)
    }

    @Test
    fun `AnimaChora stores cross-territory links`() {
        val link = CrossTerritoryLink(
            id = "link-1",
            fromTerritoryId = "t1",
            fromConceptName = "Eudaimonia",
            toTerritoryId = "t2",
            toConceptName = "Flourishing",
            note = null,
            createdAt = 1000L
        )
        val animachora = AnimaChora(
            name = "My AnimaChora",
            territories = listOf(makeTerritory("t1"), makeTerritory("t2", TerritoryType.STUDY)),
            crossTerritoryLinks = listOf(link),
            createdAt = 1000L,
            lastUpdatedAt = 1000L
        )
        assertEquals(1, animachora.crossTerritoryLinks.size)
        assertEquals("link-1", animachora.crossTerritoryLinks.first().id)
    }
}
