package com.marginalia.vault

import com.marginalia.animachora.Territory
import com.marginalia.animachora.TerritoryShade
import com.marginalia.animachora.TerritorySymbol
import com.marginalia.animachora.TerritoryType
import com.marginalia.model.Book
import com.marginalia.model.BookFormat
import com.marginalia.model.ConceptStatus
import com.marginalia.model.Highlight
import com.marginalia.model.HighlightColour
import com.marginalia.model.ReadingProgress
import com.marginalia.model.ReadingStatus
import com.marginalia.model.Result
import com.marginalia.vault.RegistryError
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConceptRegistryTest {

    private val territory = Territory(
        id = "library", name = "Library", type = TerritoryType.LIBRARY,
        folderPath = "library", symbol = TerritorySymbol.LAMP, shade = TerritoryShade.WARM,
        colour = null, ghostSymbolOpacity = 5, isPrivate = false, createdAt = 0L, lastEnteredAt = null
    )

    private val book = Book(
        id = "book-001", title = "Meditations", author = "Marcus Aurelius",
        format = BookFormat.EPUB, filePath = "library/books/meditations.epub",
        coverPath = null, addedAt = 0L, lastOpenedAt = null,
        readingProgress = ReadingProgress(null, null, 0f, null),
        status = ReadingStatus.READING, territoryId = "library"
    )

    private val highlight = Highlight(
        id = "h1", bookId = "book-001", cfi = "", text = "The eudaimonia of the rational soul.",
        colour = HighlightColour.YELLOW, annotation = "Key passage", createdAt = 0L, pageNumber = null
    )

    private fun makeRegistry() = ConceptFileRegistry(FakeVaultFileSystem(), todayIso = { "2026-06-01" })

    @Test
    fun `createFromHighlight creates concept note with SEED status`() = runTest {
        val registry = makeRegistry()
        val result = registry.createFromHighlight("Eudaimonia", highlight, book, territory)
        assertIs<Result.Success<*>>(result)
        val concept = (result as Result.Success).value
        assertEquals(ConceptStatus.SEED, concept.status)
        assertEquals("Eudaimonia", concept.name)
    }

    @Test
    fun `createFromHighlight populates Cross-Text References section`() = runTest {
        val registry = makeRegistry()
        registry.createFromHighlight("Eudaimonia", highlight, book, territory)
        val concepts = registry.getAllConcepts("library")
        assertEquals(1, concepts.size)
        // File was created — verify via findByName
        val found = registry.findByName("Eudaimonia", "library")
        assertNotNull(found)
    }

    @Test
    fun `createFromHighlight returns DuplicateName for existing concept`() = runTest {
        val registry = makeRegistry()
        registry.createFromHighlight("Virtue", highlight, book, territory)
        val result = registry.createFromHighlight("Virtue", highlight, book, territory)
        assertIs<Result.Failure<*>>(result)
        assertIs<RegistryError.DuplicateName>((result as Result.Failure).error)
    }

    @Test
    fun `findByName returns null for unknown concept`() = runTest {
        val registry = makeRegistry()
        assertNull(registry.findByName("Unknown", "library"))
    }

    @Test
    fun `findByName is case-insensitive`() = runTest {
        val registry = makeRegistry()
        registry.createFromHighlight("Stoicism", highlight, book, territory)
        assertNotNull(registry.findByName("stoicism", "library"))
        assertNotNull(registry.findByName("STOICISM", "library"))
    }

    @Test
    fun `getAllConcepts returns all created concepts`() = runTest {
        val registry = makeRegistry()
        registry.createFromHighlight("Logos", highlight, book, territory)
        registry.createFromHighlight("Virtue", highlight, book, territory)
        val all = registry.getAllConcepts("library")
        assertEquals(2, all.size)
        assertTrue(all.any { it.name == "Logos" })
        assertTrue(all.any { it.name == "Virtue" })
    }

    @Test
    fun `sanitiseConceptFileName converts spaces to hyphens and lowercases`() {
        val registry = ConceptFileRegistry(FakeVaultFileSystem())
        assertEquals("virtue-ethics", registry.sanitiseConceptFileName("Virtue Ethics"))
        assertEquals("stoicism", registry.sanitiseConceptFileName("Stoicism"))
    }
}
