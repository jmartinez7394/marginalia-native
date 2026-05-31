package com.marginalia.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CfiAnchorTest {

    @Test
    fun `CfiAnchor can be created with all fields`() {
        val anchor = CfiAnchor(
            cfi = "epubcfi(/6/4[chapter01]!/4/2/1:0)",
            bookId = "book1",
            chapterTitle = "Chapter One",
            nearbyText = "The beginning of the story"
        )
        assertEquals("epubcfi(/6/4[chapter01]!/4/2/1:0)", anchor.cfi)
        assertEquals("book1", anchor.bookId)
        assertEquals("Chapter One", anchor.chapterTitle)
        assertEquals("The beginning of the story", anchor.nearbyText)
    }

    @Test
    fun `CfiAnchor can be created with all optional fields null`() {
        val anchor = CfiAnchor(
            cfi = "epubcfi(/6/4[chapter01]!/4/2/1:0)",
            bookId = "book1",
            chapterTitle = null,
            nearbyText = null
        )
        assertNull(anchor.chapterTitle)
        assertNull(anchor.nearbyText)
    }

    @Test
    fun `CfiAnchor equality based on cfi and bookId`() {
        val a1 = CfiAnchor("epubcfi(/6/4!/4/2/1:0)", "book1", "Ch 1", "text")
        val a2 = CfiAnchor("epubcfi(/6/4!/4/2/1:0)", "book1", "Ch 1", "text")
        assertEquals(a1, a2)
    }
}
