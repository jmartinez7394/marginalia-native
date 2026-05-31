package com.marginalia.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class BookTest {

    private fun aBook() = Book(
        id = "book-001",
        title = "Meditations",
        author = "Marcus Aurelius",
        format = BookFormat.EPUB,
        filePath = "library/books/meditations.epub",
        coverPath = null,
        addedAt = 1000L,
        lastOpenedAt = null,
        readingProgress = ReadingProgress(null, null, 0f, null),
        status = ReadingStatus.UNREAD,
        territoryId = "library"
    )

    @Test
    fun bookCreation() {
        val book = aBook()
        assertEquals("book-001", book.id)
        assertEquals("Meditations", book.title)
        assertEquals(BookFormat.EPUB, book.format)
        assertEquals(ReadingStatus.UNREAD, book.status)
        assertEquals("library", book.territoryId)
    }

    @Test
    fun bookEquality() {
        assertEquals(aBook(), aBook())
    }

    @Test
    fun bookCopyWithModification() {
        val original = aBook()
        val updated = original.copy(status = ReadingStatus.READING, lastOpenedAt = 2000L)
        assertEquals(ReadingStatus.READING, updated.status)
        assertEquals(2000L, updated.lastOpenedAt)
        assertEquals(ReadingStatus.UNREAD, original.status)
        assertNull(original.lastOpenedAt)
    }

    @Test
    fun bookInequalityOnDifferentIds() {
        val a = aBook()
        val b = aBook().copy(id = "book-002")
        assertNotEquals(a, b)
    }
}
