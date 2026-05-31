package com.marginalia.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConceptNoteTest {

    private fun makeConcept(
        id: String = "c1",
        name: String = "Virtue Ethics",
        status: ConceptStatus = ConceptStatus.SEED,
        aliases: List<String> = emptyList()
    ) = ConceptNote(
        id = id,
        name = name,
        aliases = aliases,
        status = status,
        practiceDepth = null,
        filePath = "notes/virtue-ethics.md",
        territoryId = "territory-1",
        crossReferences = emptyList(),
        createdAt = 1000L,
        lastModifiedAt = 1000L
    )

    @Test
    fun `ConceptNote can be created with status SEED`() {
        val concept = makeConcept(status = ConceptStatus.SEED)
        assertEquals(ConceptStatus.SEED, concept.status)
    }

    @Test
    fun `ConceptNote status can be changed to DEVELOPING`() {
        val concept = makeConcept(status = ConceptStatus.SEED)
        val updated = concept.copy(status = ConceptStatus.DEVELOPING)
        assertEquals(ConceptStatus.DEVELOPING, updated.status)
    }

    @Test
    fun `ConceptNote status can be changed to SETTLED`() {
        val concept = makeConcept(status = ConceptStatus.DEVELOPING)
        val updated = concept.copy(status = ConceptStatus.SETTLED)
        assertEquals(ConceptStatus.SETTLED, updated.status)
    }

    @Test
    fun `ConceptNote aliases list is searchable`() {
        val concept = makeConcept(aliases = listOf("Arete", "Moral Virtue"))
        assertTrue(concept.aliases.contains("Arete"))
        assertTrue(concept.aliases.contains("Moral Virtue"))
    }

    @Test
    fun `ConceptNote practiceDepth can be null`() {
        val concept = makeConcept()
        assertNull(concept.practiceDepth)
    }

    @Test
    fun `ConceptNote practiceDepth can be set to all values`() {
        PracticeDepth.entries.forEach { depth ->
            val concept = makeConcept().copy(practiceDepth = depth)
            assertEquals(depth, concept.practiceDepth)
        }
    }

    @Test
    fun `ConceptStatus has exactly three values`() {
        assertEquals(3, ConceptStatus.entries.size)
    }

    @Test
    fun `PracticeDepth has exactly four values`() {
        assertEquals(4, PracticeDepth.entries.size)
    }
}
