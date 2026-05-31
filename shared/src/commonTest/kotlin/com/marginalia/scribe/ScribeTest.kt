package com.marginalia.scribe

import com.marginalia.ai.AIError
import com.marginalia.model.ConceptNote
import com.marginalia.model.ConceptStatus
import com.marginalia.sync.SyncResult
import com.marginalia.sync.SyncState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScribeTest {

    private fun makeConcept(
        id: String,
        name: String,
        aliases: List<String> = emptyList()
    ) = ConceptNote(
        id = id,
        name = name,
        aliases = aliases,
        status = ConceptStatus.SEED,
        practiceDepth = null,
        filePath = "notes/$name.md",
        territoryId = "t1",
        crossReferences = emptyList(),
        createdAt = 1000L,
        lastModifiedAt = 1000L
    )

    private fun makeContext(
        territoryId: String = "t1",
        territoryName: String = "Library"
    ) = ScribeContext(
        noteTitle = null,
        territoryId = territoryId,
        territoryName = territoryName,
        anchoredPassageText = null,
        bookTitle = null,
        author = null,
        chapterLabel = null,
        conceptRegistry = emptyList(),
        userDescription = null
    )

    // ScribeContext
    @Test
    fun `ScribeContext can be constructed with all optional fields null`() {
        val context = makeContext()
        assertNull(context.noteTitle)
        assertNull(context.anchoredPassageText)
        assertNull(context.bookTitle)
        assertNull(context.author)
        assertNull(context.chapterLabel)
        assertNull(context.userDescription)
        assertTrue(context.conceptRegistry.isEmpty())
    }

    @Test
    fun `ScribeContext territoryId and territoryName are required`() {
        val context = makeContext(territoryId = "library-001", territoryName = "My Library")
        assertEquals("library-001", context.territoryId)
        assertEquals("My Library", context.territoryName)
    }

    // ScribeInput
    @Test
    fun `ScribeInput revisionId can be null for external images`() {
        val input = ScribeInput(
            sourceType = ScribeSourceType.EXTERNAL_IMAGE,
            imageBytes = ByteArray(100),
            imageWidth = 1080,
            imageHeight = 1440,
            context = makeContext(),
            noteId = "note-1",
            revisionId = null
        )
        assertNull(input.revisionId)
        assertEquals(ScribeSourceType.EXTERNAL_IMAGE, input.sourceType)
    }

    @Test
    fun `ScribeSourceType has three variants`() {
        val types = ScribeSourceType.entries.toTypedArray()
        assertEquals(3, types.size)
        assertTrue(types.contains(ScribeSourceType.INK_NOTE))
        assertTrue(types.contains(ScribeSourceType.MARGIN_ANNOTATION))
        assertTrue(types.contains(ScribeSourceType.EXTERNAL_IMAGE))
    }

    // WikilinkSuggestion
    @Test
    fun `WikilinkSuggestion matched concept is set when found in registry`() {
        val suggestion = WikilinkSuggestion(
            term = "Virtue Ethics",
            matchedConcept = "Virtue Ethics",
            confidence = 1.0f
        )
        assertNotNull(suggestion.matchedConcept)
        assertEquals("Virtue Ethics", suggestion.matchedConcept)
    }

    @Test
    fun `WikilinkSuggestion matchedConcept is null for new concept candidate`() {
        val suggestion = WikilinkSuggestion(
            term = "Eudaimonia",
            matchedConcept = null,
            confidence = 0.7f
        )
        assertNull(suggestion.matchedConcept)
    }

    // ScribeResult
    @Test
    fun `ScribeResult_Success carries transcribed text and wikilinks`() {
        val result = ScribeResult.Success(
            transcribedText = "The practice of virtue...",
            detectedConcepts = listOf("Virtue Ethics"),
            suggestedWikilinks = listOf(WikilinkSuggestion("Virtue Ethics", "Virtue Ethics", 1.0f))
        )
        assertTrue(result is ScribeResult.Success)
        assertEquals("The practice of virtue...", result.transcribedText)
        assertEquals(1, result.suggestedWikilinks.size)
    }

    @Test
    fun `ScribeResult_ScribeAiError carries AIError`() {
        val error = AIError.NetworkError("connection failed")
        val result = ScribeResult.ScribeAiError(error)
        assertTrue(result is ScribeResult.ScribeAiError)
        assertEquals(error, result.error)
    }

    @Test
    fun `ScribeResult_NoInkContent is distinct sealed variant`() {
        val result: ScribeResult = ScribeResult.NoInkContent
        assertTrue(result is ScribeResult.NoInkContent)
    }

    // ScribeWikilinkResolver — exact match
    @Test
    fun `resolveWikilinks returns exact match for concept name in text`() {
        val registry = listOf(
            makeConcept("c1", "Virtue Ethics"),
            makeConcept("c2", "Logos")
        )
        val suggestions = ScribeWikilinkResolver.resolveWikilinks(
            "This text discusses Virtue Ethics and its applications.",
            registry
        )
        val virtueMatch = suggestions.find { it.term == "Virtue Ethics" }
        assertNotNull(virtueMatch)
        assertEquals("Virtue Ethics", virtueMatch.matchedConcept)
        assertEquals(1.0f, virtueMatch.confidence)
    }

    // ScribeWikilinkResolver — alias match
    @Test
    fun `resolveWikilinks returns alias match mapping alias to concept name`() {
        val registry = listOf(
            makeConcept("c1", "Virtue Ethics", aliases = listOf("Arete", "Moral Virtue"))
        )
        val suggestions = ScribeWikilinkResolver.resolveWikilinks(
            "The ancient Greeks called it Arete.",
            registry
        )
        val aliasMatch = suggestions.find { it.term == "Arete" }
        assertNotNull(aliasMatch)
        assertEquals("Virtue Ethics", aliasMatch.matchedConcept)
    }

    // ScribeWikilinkResolver — no match
    @Test
    fun `resolveWikilinks returns empty list when no registry concepts appear in text`() {
        val registry = listOf(
            makeConcept("c1", "Virtue Ethics"),
            makeConcept("c2", "Logos")
        )
        val suggestions = ScribeWikilinkResolver.resolveWikilinks(
            "This text has no matching philosophical terms.",
            registry
        )
        assertTrue(suggestions.isEmpty())
    }

    @Test
    fun `resolveWikilinks only matches concepts present in text`() {
        val registry = listOf(
            makeConcept("c1", "Virtue Ethics"),
            makeConcept("c2", "Logos"),
            makeConcept("c3", "Stoicism")
        )
        val suggestions = ScribeWikilinkResolver.resolveWikilinks(
            "Logos is the principle of rationality.",
            registry
        )
        assertEquals(1, suggestions.size)
        assertEquals("Logos", suggestions.first().term)
    }
}

class SyncResultTest {

    @Test
    fun `SyncResult_Conflict carries conflicted file list`() {
        val files = listOf("notes/note1.md", "notes/note2.md")
        val result = SyncResult.Conflict(files)
        assertEquals(files, result.conflictedFiles)
        assertEquals(2, result.conflictedFiles.size)
    }

    @Test
    fun `SyncResult_Error carries message`() {
        val result = SyncResult.Error("network timeout")
        assertEquals("network timeout", result.message)
    }

    @Test
    fun `SyncResult_Success is distinct from other variants`() {
        val success: SyncResult = SyncResult.Success
        assertTrue(success is SyncResult.Success)
        assertFalse(success is SyncResult.Conflict)
        assertFalse(success is SyncResult.Error)
        assertFalse(success is SyncResult.NotConfigured)
    }

    @Test
    fun `SyncResult_NotConfigured is distinct sealed variant`() {
        val result: SyncResult = SyncResult.NotConfigured
        assertTrue(result is SyncResult.NotConfigured)
    }

    @Test
    fun `SyncState_LastSync carries timestamp and result`() {
        val syncResult = SyncResult.Success
        val state = SyncState.LastSync(at = 1000L, result = syncResult)
        assertEquals(1000L, state.at)
        assertEquals(syncResult, state.result)
    }
}
