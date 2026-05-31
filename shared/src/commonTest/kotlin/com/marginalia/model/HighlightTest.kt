package com.marginalia.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HighlightTest {

    @Test
    fun `Highlight can be created with all fields`() {
        val highlight = Highlight(
            id = "h1",
            bookId = "book1",
            cfi = "epubcfi(/6/4[chapter01]!/4/2/1:0)",
            text = "some highlighted text",
            colour = HighlightColour.YELLOW,
            annotation = "my annotation",
            createdAt = 1000L,
            pageNumber = null
        )
        assertEquals("h1", highlight.id)
        assertEquals(HighlightColour.YELLOW, highlight.colour)
        assertNotNull(highlight.annotation)
    }

    @Test
    fun `Highlight annotation can be null`() {
        val highlight = Highlight(
            id = "h2",
            bookId = "book1",
            cfi = "epubcfi(/6/4[chapter01]!/4/2/1:0)",
            text = "text",
            colour = HighlightColour.GREEN,
            annotation = null,
            createdAt = 1000L,
            pageNumber = null
        )
        assertNull(highlight.annotation)
    }

    @Test
    fun `HighlightColour has all four expected values`() {
        val colours = HighlightColour.entries.toTypedArray()
        assertEquals(4, colours.size)
        assertTrue(colours.contains(HighlightColour.YELLOW))
        assertTrue(colours.contains(HighlightColour.GREEN))
        assertTrue(colours.contains(HighlightColour.BLUE))
        assertTrue(colours.contains(HighlightColour.PINK))
    }

    @Test
    fun `Highlight pageNumber is set for PDF`() {
        val highlight = Highlight(
            id = "h3",
            bookId = "book1",
            cfi = "",
            text = "pdf text",
            colour = HighlightColour.BLUE,
            annotation = null,
            createdAt = 1000L,
            pageNumber = 42
        )
        assertEquals(42, highlight.pageNumber)
    }

    @Test
    fun `Highlight pageNumber is null for EPUB`() {
        val highlight = Highlight(
            id = "h4",
            bookId = "book1",
            cfi = "epubcfi(/6/4[chapter01]!/4/2/1:0)",
            text = "epub text",
            colour = HighlightColour.PINK,
            annotation = null,
            createdAt = 1000L,
            pageNumber = null
        )
        assertNull(highlight.pageNumber)
    }
}
