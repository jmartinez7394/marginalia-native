package com.marginalia.vault

import com.marginalia.model.Highlight
import com.marginalia.model.HighlightColour
import com.marginalia.model.Result
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class HighlightRepositoryTest {

    private fun makeHighlight(
        id: String = "h1",
        bookId: String = "book1",
        colour: HighlightColour = HighlightColour.YELLOW,
        annotation: String? = null
    ) = Highlight(
        id = id,
        bookId = bookId,
        cfi = "epubcfi(/6/4[chap01]!/4/2/1:0)",
        text = "Some highlighted text",
        colour = colour,
        annotation = annotation,
        createdAt = 1000L,
        pageNumber = null
    )

    private fun makeRepo(): HighlightFileRepository {
        val fs = FakeVaultFileSystem()
        val library = FakeLibraryRepository(mapOf("book1" to "library", "book2" to "library"))
        return HighlightFileRepository(fs, library)
    }

    @Test
    fun `add highlight and read it back — identical`() = runTest {
        val repo = makeRepo()
        val highlight = makeHighlight()

        val addResult = repo.addHighlight(highlight)
        assertIs<Result.Success<Highlight>>(addResult)

        val loaded = repo.getHighlights("book1")
        assertEquals(1, loaded.size)
        assertEquals(highlight, loaded.first())
    }

    @Test
    fun `update highlight annotation field`() = runTest {
        val repo = makeRepo()
        val original = makeHighlight(annotation = null)
        repo.addHighlight(original)

        val updated = original.copy(annotation = "My note on this passage")
        val updateResult = repo.updateHighlight(updated)
        assertIs<Result.Success<Highlight>>(updateResult)

        val loaded = repo.getHighlights("book1")
        assertEquals("My note on this passage", loaded.first().annotation)
    }

    @Test
    fun `delete highlight — no longer in list`() = runTest {
        val repo = makeRepo()
        val h1 = makeHighlight(id = "h1")
        val h2 = makeHighlight(id = "h2")
        repo.addHighlight(h1)
        repo.addHighlight(h2)

        val deleteResult = repo.deleteHighlight("h1")
        assertIs<Result.Success<Unit>>(deleteResult)

        val remaining = repo.getHighlights("book1")
        assertEquals(1, remaining.size)
        assertFalse(remaining.any { it.id == "h1" })
        assertTrue(remaining.any { it.id == "h2" })
    }

    @Test
    fun `observeHighlights emits after add`() = runTest {
        val repo = makeRepo()

        val initial = repo.observeHighlights("book1").first()
        assertTrue(initial.isEmpty())

        repo.addHighlight(makeHighlight())

        val afterAdd = repo.observeHighlights("book1").first()
        assertEquals(1, afterAdd.size)
    }

    @Test
    fun `empty vault returns empty list`() = runTest {
        val repo = makeRepo()
        val result = repo.getHighlights("book1")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `BOM in JSON file does not crash parser`() = runTest {
        val fs = FakeVaultFileSystem()
        val library = FakeLibraryRepository(mapOf("book1" to "library"))
        val repo = HighlightFileRepository(fs, library)

        // Write a highlights JSON with a UTF-8 BOM prefix (U+FEFF = ﻿)
        val bom = "﻿"
        val jsonWithBom = "${bom}{\"schemaVersion\":1,\"bookId\":\"book1\",\"highlights\":[]}"
        fs.writeFile("library/.marginalia/highlights/book1.json", jsonWithBom)

        val result = repo.getHighlights("book1")
        assertTrue(result.isEmpty(), "BOM-prefixed JSON should parse successfully and return empty list")
    }

    @Test
    fun `update non-existent highlight returns HighlightNotFound`() = runTest {
        val repo = makeRepo()
        val result = repo.updateHighlight(makeHighlight(id = "ghost"))
        val failure = assertIs<Result.Failure<HighlightError>>(result)
        assertIs<HighlightError.HighlightNotFound>(failure.error)
    }

    @Test
    fun `delete non-existent highlight returns HighlightNotFound`() = runTest {
        val repo = makeRepo()
        val result = repo.deleteHighlight("ghost-id")
        val failure = assertIs<Result.Failure<HighlightError>>(result)
        assertIs<HighlightError.HighlightNotFound>(failure.error)
    }

    @Test
    fun `annotation overwrites previous annotation on update`() = runTest {
        val repo = makeRepo()
        val original = makeHighlight(annotation = "first note")
        repo.addHighlight(original)

        val secondUpdate = original.copy(annotation = "second note")
        repo.updateHighlight(secondUpdate)

        val loaded = repo.getHighlights("book1")
        assertEquals("second note", loaded.first().annotation)
    }

    @Test
    fun `null annotation clears annotation field on update`() = runTest {
        val repo = makeRepo()
        val original = makeHighlight(annotation = "some note")
        repo.addHighlight(original)

        val cleared = original.copy(annotation = null)
        val result = repo.updateHighlight(cleared)
        assertIs<Result.Success<Highlight>>(result)

        val loaded = repo.getHighlights("book1")
        assertEquals(null, loaded.first().annotation)
    }
}
