package com.marginalia.ink

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InkNoteTest {

    private fun makeStroke(
        id: String = "s1",
        x: Float = 0.5f,
        y: Float = 0.5f,
        pressure: Float = 0.8f
    ) = Stroke(
        id = id,
        points = listOf(StrokePoint(x, y, pressure)),
        colour = InkColour.BLACK,
        width = 0.01f,
        erased = false,
        timestamp = 1000L
    )

    private fun makeRevision(
        revisionId: String = "rev-1",
        sessionId: String = "session-1",
        strokes: List<Stroke> = listOf(makeStroke())
    ) = InkNoteRevision(
        revisionId = revisionId,
        sessionId = sessionId,
        strokes = strokes,
        createdAt = 1000L,
        transcription = null
    )

    private fun makeNote(
        id: String = "note-1",
        revisions: List<InkNoteRevision> = listOf(makeRevision()),
        currentRevisionId: String = "rev-1"
    ) = InkNote(
        id = id,
        title = "Test Note",
        context = InkNoteContext.Freeform,
        revisions = revisions,
        currentRevisionId = currentRevisionId,
        createdAt = 1000L,
        territoryId = "territory-1"
    )

    @Test
    fun `StrokePoint coordinates are normalised 0_0 to 1_0`() {
        val point = StrokePoint(x = 0.25f, y = 0.75f, pressure = 0.8f)
        assertTrue(point.x in 0f..1f)
        assertTrue(point.y in 0f..1f)
        assertTrue(point.pressure in 0f..1f)
    }

    @Test
    fun `Stroke with erased false is active`() {
        val stroke = makeStroke()
        assertFalse(stroke.erased)
    }

    @Test
    fun `InkColour has BLACK and HIGHLIGHT`() {
        val colours = InkColour.entries.toTypedArray()
        assertEquals(2, colours.size)
        assertTrue(colours.contains(InkColour.BLACK))
        assertTrue(colours.contains(InkColour.HIGHLIGHT))
    }

    @Test
    fun `InkNote currentRevision returns correct revision`() {
        val note = makeNote()
        val current = KintsugiHistory.currentRevision(note)
        assertEquals("rev-1", current.revisionId)
    }

    @Test
    fun `KintsugiHistory previousRevisions returns empty for single revision`() {
        val note = makeNote()
        val previous = KintsugiHistory.previousRevisions(note)
        assertTrue(previous.isEmpty())
    }

    @Test
    fun `KintsugiHistory addRevision creates new revision and preserves previous`() {
        val note = makeNote()
        val newStrokes = listOf(makeStroke("s2", 0.3f, 0.4f))
        val updated = KintsugiHistory.addRevision(note, newStrokes, "session-2", createdAt = 2000L)

        assertEquals(2, updated.revisions.size)
        assertNotEquals(note.currentRevisionId, updated.currentRevisionId)

        val newRevision = KintsugiHistory.currentRevision(updated)
        assertEquals("session-2", newRevision.sessionId)
        assertEquals(newStrokes, newRevision.strokes)
        assertNull(newRevision.transcription)

        val previous = KintsugiHistory.previousRevisions(updated)
        assertEquals(1, previous.size)
        assertEquals("rev-1", previous.first().revisionId)
    }

    @Test
    fun `KintsugiHistory hasMultipleRevisions is false for single revision`() {
        val note = makeNote()
        assertFalse(KintsugiHistory.hasMultipleRevisions(note))
    }

    @Test
    fun `KintsugiHistory hasMultipleRevisions is true after addRevision`() {
        val note = makeNote()
        val updated = KintsugiHistory.addRevision(note, emptyList(), "session-2", 2000L)
        assertTrue(KintsugiHistory.hasMultipleRevisions(updated))
    }

    @Test
    fun `InkNoteContext sealed variants are distinct`() {
        val freeform: InkNoteContext = InkNoteContext.Freeform
        val margin: InkNoteContext = InkNoteContext.MarginAnnotation("book1", "cfi/1", "text")
        val canvas: InkNoteContext = InkNoteContext.CanvasTessera("canvas1", "(0,1)")

        assertTrue(freeform is InkNoteContext.Freeform)
        assertTrue(margin is InkNoteContext.MarginAnnotation)
        assertTrue(canvas is InkNoteContext.CanvasTessera)

        assertNotEquals(freeform, margin as InkNoteContext)
        assertNotEquals(margin as InkNoteContext, canvas as InkNoteContext)
    }

    @Test
    fun `InkNoteRevision transcription is null before Scribe processes`() {
        val revision = makeRevision()
        assertNull(revision.transcription)
    }

    @Test
    fun `MarginAnnotation anchoredText can be null`() {
        val context = InkNoteContext.MarginAnnotation(
            bookId = "book1",
            cfi = "epubcfi(/6/4!/4/2/1:0)",
            anchoredText = null
        )
        assertNull(context.anchoredText)
    }

    @Test
    fun `InkNote title can be null for untitled notes`() {
        val note = InkNote(
            id = "note-2",
            title = null,
            context = InkNoteContext.Freeform,
            revisions = listOf(makeRevision()),
            currentRevisionId = "rev-1",
            createdAt = 1000L,
            territoryId = "territory-1"
        )
        assertNull(note.title)
    }
}
