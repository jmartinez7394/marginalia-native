package com.marginalia.vault

import com.marginalia.animachora.Territory
import com.marginalia.animachora.TerritoryShade
import com.marginalia.animachora.TerritorySymbol
import com.marginalia.animachora.TerritoryType
import com.marginalia.model.Book
import com.marginalia.model.BookFormat
import com.marginalia.model.EmotionalTag
import com.marginalia.model.Highlight
import com.marginalia.model.HighlightColour
import com.marginalia.model.ReadingProgress
import com.marginalia.model.ReadingStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class LinkedNoteGeneratorTest {

    private val testTerritory = Territory(
        id = "library",
        name = "My Library",
        type = TerritoryType.LIBRARY,
        folderPath = "library",
        symbol = TerritorySymbol.LAMP,
        shade = TerritoryShade.WARM,
        colour = null,
        ghostSymbolOpacity = 5,
        isPrivate = false,
        createdAt = 0L,
        lastEnteredAt = null
    )

    private val testBook = Book(
        id = "book-001",
        title = "Meditations",
        author = "Marcus Aurelius",
        format = BookFormat.EPUB,
        filePath = "library/books/meditations.epub",
        coverPath = null,
        addedAt = 1000L,
        lastOpenedAt = null,
        readingProgress = ReadingProgress(null, null, 0f, null),
        status = ReadingStatus.READING,
        territoryId = "library"
    )

    private fun makeHighlight(
        id: String,
        text: String = "Highlighted text $id",
        annotation: String? = null
    ) = Highlight(
        id = id,
        bookId = "book-001",
        cfi = "epubcfi(/6/4[chap01]!/4/2/1:0)",
        text = text,
        colour = HighlightColour.YELLOW,
        annotation = annotation,
        createdAt = 1000L,
        pageNumber = null
    )

    @Test
    fun `generate with zero highlights produces valid markdown`() {
        val result = LinkedNoteGenerator.generate(testBook, emptyList(), testTerritory, "2026-06-01")
        assertTrue(result.contains("---"), "Should have YAML frontmatter delimiters")
        assertTrue(result.contains("type: \"book-note\""), "Should have correct type")
        assertTrue(result.contains("bookId: \"book-001\""), "Should include bookId")
        assertTrue(result.contains("# Meditations"), "Should have title heading")
        assertTrue(result.contains("**Marcus Aurelius**"), "Should have author")
        assertTrue(result.contains("## Highlights and Annotations"), "Should have highlights section")
        assertTrue(result.contains("## Reading Notes"), "Should have reading notes section")
    }

    @Test
    fun `generate with three highlights includes all in output`() {
        val highlights = listOf(
            makeHighlight("h1", "First passage"),
            makeHighlight("h2", "Second passage"),
            makeHighlight("h3", "Third passage")
        )
        val result = LinkedNoteGenerator.generate(testBook, highlights, testTerritory, "2026-06-01")
        assertTrue(result.contains("First passage"), "Should contain first highlight text")
        assertTrue(result.contains("Second passage"), "Should contain second highlight text")
        assertTrue(result.contains("Third passage"), "Should contain third highlight text")
        assertTrue(result.contains("^ann-h1"), "Should contain first block id")
        assertTrue(result.contains("^ann-h2"), "Should contain second block id")
        assertTrue(result.contains("^ann-h3"), "Should contain third block id")
    }

    @Test
    fun `generate with annotated highlight includes annotation below quote`() {
        val highlight = makeHighlight("h1", "A passage to annotate", annotation = "This is my annotation")
        val result = LinkedNoteGenerator.generate(testBook, listOf(highlight), testTerritory, "2026-06-01")
        val lines = result.lines()
        val quoteLine = lines.indexOfFirst { it.contains("> A passage to annotate") }
        assertTrue(quoteLine >= 0, "Should contain the quoted passage")
        val blockIdLine = lines.indexOfFirst { it.contains("^ann-h1") }
        assertTrue(blockIdLine > quoteLine, "Block ID should follow the quote")
        assertTrue(result.contains("This is my annotation"), "Should contain the annotation text")
    }

    @Test
    fun `block IDs are generated — non-empty and unique across highlights`() {
        val highlights = listOf(
            makeHighlight("highlight-uuid-1"),
            makeHighlight("highlight-uuid-2"),
            makeHighlight("highlight-uuid-3")
        )
        val result = LinkedNoteGenerator.generate(testBook, highlights, testTerritory, "2026-06-01")
        assertTrue(result.contains("^ann-highlight-uuid-1"), "Should have first block id")
        assertTrue(result.contains("^ann-highlight-uuid-2"), "Should have second block id")
        assertTrue(result.contains("^ann-highlight-uuid-3"), "Should have third block id")
        assertNotEquals(
            result.indexOf("^ann-highlight-uuid-1"),
            result.indexOf("^ann-highlight-uuid-2"),
            "Block IDs must be unique"
        )
    }

    @Test
    fun `frontmatter is valid YAML — threads array is present`() {
        val result = LinkedNoteGenerator.generate(testBook, emptyList(), testTerritory, "2026-06-01")
        assertTrue(result.contains("threads: []"), "threads array must be in frontmatter")
        assertTrue(result.contains("tags: []"), "tags array must be in frontmatter")
        val frontmatterEnd = result.indexOf("---\n\n")
        assertTrue(frontmatterEnd > 3, "Should have closing frontmatter delimiter")
    }

    @Test
    fun `title with special characters is sanitised for file path`() {
        val dirty = "Book: A \"Test\" Title / With Extras?"
        val clean = LinkedNoteGenerator.sanitiseFilename(dirty)
        assertFalse(clean.contains(":"), "Colon should be removed")
        assertFalse(clean.contains("\""), "Quote should be removed")
        assertFalse(clean.contains("/"), "Slash should be removed")
        assertFalse(clean.contains("?"), "Question mark should be removed")
    }

    @Test
    fun `generate uses importedAtIso in frontmatter`() {
        val result = LinkedNoteGenerator.generate(testBook, emptyList(), testTerritory, "2026-06-15")
        assertTrue(result.contains("importedAt: \"2026-06-15\""), "Should embed import date")
        assertTrue(result.contains("startedAt: \"2026-06-15\""), "Should embed start date")
    }

    @Test
    fun `annotation renders as italic text in linked note`() {
        val highlight = makeHighlight("h1", "A wise passage", annotation = "This resonates deeply")
        val result = LinkedNoteGenerator.generate(testBook, listOf(highlight), testTerritory, "2026-06-01")
        assertTrue(result.contains("*This resonates deeply*"), "Annotation should be wrapped in italic markers")
    }

    @Test
    fun `null annotation produces no annotation line after block ID`() {
        val highlight = makeHighlight("h1", "A wise passage", annotation = null)
        val result = LinkedNoteGenerator.generate(testBook, listOf(highlight), testTerritory, "2026-06-01")
        assertTrue(result.contains("^ann-h1"), "Block ID must be present")
        val lines = result.lines()
        val blockIdx = lines.indexOfFirst { it == "^ann-h1" }
        assertTrue(blockIdx >= 0, "Block ID line must exist")
        // The two lines following the block ID should not be italic-wrapped annotation text
        val nearBlock = lines.subList(blockIdx + 1, minOf(blockIdx + 3, lines.size))
        assertFalse(
            nearBlock.any { it.startsWith("*") && it.endsWith("*") && it.length > 2 },
            "No italic annotation line near block ID when annotation is null"
        )
    }

    @Test
    fun `emotion tag produces metadata line immediately after block ID`() {
        val highlight = makeHighlight("h1", "A troubling passage").copy(emotionalTag = EmotionalTag.MOVED)
        val result = LinkedNoteGenerator.generate(testBook, listOf(highlight), testTerritory, "2026-06-01")
        val lines = result.lines()
        val blockIdIdx = lines.indexOfFirst { it == "^ann-h1" }
        assertTrue(blockIdIdx >= 0, "Block ID must exist")
        assertEquals("%%emotion:moved%%", lines[blockIdIdx + 1], "Emotion line must immediately follow block ID")
    }

    @Test
    fun `no emotion tag produces no emotion metadata line`() {
        val highlight = makeHighlight("h1", "A passage", annotation = null)
        val result = LinkedNoteGenerator.generate(testBook, listOf(highlight), testTerritory, "2026-06-01")
        assertFalse(result.contains("%%emotion:"), "No emotion line when emotionalTag is null")
    }

    @Test
    fun `emotion tag lowercase in metadata line`() {
        val highlight = makeHighlight("h1").copy(emotionalTag = EmotionalTag.RESISTANT)
        val result = LinkedNoteGenerator.generate(testBook, listOf(highlight), testTerritory, "2026-06-01")
        assertTrue(result.contains("%%emotion:resistant%%"), "Emotion tag must be lowercase in metadata")
        assertFalse(result.contains("%%emotion:RESISTANT%%"), "Uppercase variant must not appear")
    }

    @Test
    fun `annotation appears after block ID line`() {
        val highlight = makeHighlight("h1", "A passage", annotation = "My thought")
        val result = LinkedNoteGenerator.generate(testBook, listOf(highlight), testTerritory, "2026-06-01")
        val lines = result.lines()
        val blockIdLine = lines.indexOfFirst { it == "^ann-h1" }
        assertTrue(blockIdLine >= 0, "Block ID line must exist")
        val annotationLine = lines.indexOfFirst { it.contains("*My thought*") }
        assertTrue(annotationLine > blockIdLine, "Annotation must appear after block ID")
    }
}
