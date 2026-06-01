package com.marginalia.android.platform.vault

import com.marginalia.model.Book
import com.marginalia.model.BookFormat
import com.marginalia.model.MarginAnnotation
import com.marginalia.model.ReadingProgress
import com.marginalia.model.ReadingStatus
import com.marginalia.model.Result
import com.marginalia.vault.LibraryError
import com.marginalia.vault.LibraryRepository
import com.marginalia.vault.MarginAnnotationError
import com.marginalia.vault.VaultFileSystem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import com.marginalia.model.LinkedNote
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AndroidMarginAnnotationRepositoryTest {

    private lateinit var fakeFs: LocalFakeVaultFileSystem
    private lateinit var fakeLibrary: LocalFakeLibraryRepository
    private lateinit var repo: AndroidMarginAnnotationRepository

    @BeforeTest
    fun setUp() {
        fakeFs = LocalFakeVaultFileSystem()
        fakeLibrary = LocalFakeLibraryRepository(mapOf("book-1" to "library"))
        repo = AndroidMarginAnnotationRepository(fakeFs, fakeLibrary)
    }

    private fun makeAnnotation(
        annotationId: String = "margin-001",
        bookId: String = "book-1",
        anchorCfi: String = "epubcfi(/6/4!/4/2/1:0)",
        createdAt: Long = 1000L
    ) = MarginAnnotation(
        annotationId = annotationId,
        bookId = bookId,
        bookTitle = "Meditations",
        author = "Marcus Aurelius",
        linkedNotePath = "library/notes/Meditations - Marcus Aurelius.md",
        anchorCfi = anchorCfi,
        anchoredPassageText = "The passage text",
        chapterLabel = "Book IV",
        createdAt = createdAt,
        inkNoteId = annotationId
    )

    @Test
    fun `save annotation then read back — identical`() = runTest {
        val annotation = makeAnnotation()
        val saveResult = repo.saveAnnotation(annotation)
        assertTrue(saveResult is Result.Success)

        val loaded = repo.getAnnotationsForBook("book-1")
        assertEquals(1, loaded.size)
        assertEquals(annotation, loaded.first())
    }

    @Test
    fun `update processed field`() = runTest {
        val annotation = makeAnnotation()
        repo.saveAnnotation(annotation)

        val updated = annotation.copy(processed = true, processedAt = 2000L, transcription = "Written text")
        val updateResult = repo.updateAnnotation(updated)
        assertTrue(updateResult is Result.Success)

        val loaded = repo.getAnnotationsForBook("book-1")
        assertEquals(1, loaded.size)
        assertEquals(true, loaded.first().processed)
        assertEquals(2000L, loaded.first().processedAt)
        assertEquals("Written text", loaded.first().transcription)
    }

    @Test
    fun `delete annotation — no longer returned`() = runTest {
        val annotation = makeAnnotation()
        repo.saveAnnotation(annotation)

        repo.getAnnotationsForBook("book-1")
        val deleteResult = repo.deleteAnnotation("margin-001")
        assertTrue(deleteResult is Result.Success)

        val loaded = repo.getAnnotationsForBook("book-1")
        assertTrue(loaded.isEmpty())
    }

    @Test
    fun `observeAnnotations emits after save`() = runTest {
        val empty = repo.observeAnnotations("book-1").first()
        assertTrue(empty.isEmpty())

        repo.saveAnnotation(makeAnnotation())

        val withAnnotation = repo.observeAnnotations("book-1").first()
        assertEquals(1, withAnnotation.size)
    }

    @Test
    fun `cfiHash generation is deterministic for same CFI`() {
        val cfi = "epubcfi(/6/4!/4/2/1:42)"
        val hash1 = repo.cfiHash(cfi)
        val hash2 = repo.cfiHash(cfi)
        assertEquals(hash1, hash2)
        assertEquals(8, hash1.length)
    }

    @Test
    fun `cfiHash differs for different CFIs`() {
        val hash1 = repo.cfiHash("epubcfi(/6/4!/4/2/1:0)")
        val hash2 = repo.cfiHash("epubcfi(/6/8!/4/2/1:0)")
        assertTrue(hash1 != hash2)
    }

    @Test
    fun `BOM in JSON file does not crash parser`() = runTest {
        val annotation = makeAnnotation()
        repo.saveAnnotation(annotation)

        // Prepend BOM to the file
        val dir = "library/.marginalia/margin-notes"
        val filename = repo.fileName(annotation)
        val path = "$dir/$filename"
        val original = fakeFs.readFile(path) ?: ""
        fakeFs.writeFile(path, "﻿$original")

        val loaded = repo.getAnnotationsForBook("book-1")
        assertEquals(1, loaded.size)
        assertEquals(annotation, loaded.first())
    }

    @Test
    fun `getAnnotation by ID returns correct annotation`() = runTest {
        val annotation = makeAnnotation()
        repo.saveAnnotation(annotation)
        repo.getAnnotationsForBook("book-1")

        val found = repo.getAnnotation("margin-001")
        assertNotNull(found)
        assertEquals(annotation, found)
    }

    @Test
    fun `getAnnotation returns null for unknown ID`() = runTest {
        assertNull(repo.getAnnotation("does-not-exist"))
    }

    @Test
    fun `deleteAnnotation returns AnnotationNotFound for unknown ID`() = runTest {
        val result = repo.deleteAnnotation("no-such-id")
        assertTrue(result is Result.Failure)
        assertTrue(result.error is MarginAnnotationError.AnnotationNotFound)
    }

    @Test
    fun `multiple annotations for same book are all saved and loaded`() = runTest {
        val a1 = makeAnnotation(annotationId = "margin-001", anchorCfi = "epubcfi(/6/4!/1:0)", createdAt = 1000L)
        val a2 = makeAnnotation(annotationId = "margin-002", anchorCfi = "epubcfi(/6/8!/1:0)", createdAt = 2000L)
        repo.saveAnnotation(a1)
        repo.saveAnnotation(a2)

        val loaded = repo.getAnnotationsForBook("book-1")
        assertEquals(2, loaded.size)
    }
}

private class LocalFakeVaultFileSystem : VaultFileSystem {
    val files = mutableMapOf<String, String>()

    override suspend fun readFile(path: String): String? = files[path]

    override suspend fun writeFile(path: String, content: String) {
        files[path] = content
    }

    override suspend fun deleteFile(path: String) {
        files.remove(path)
    }

    // Returns full vault-relative paths (matching AndroidVaultFileSystem behaviour)
    override suspend fun listFiles(directory: String): List<String> {
        val prefix = if (directory.endsWith("/")) directory else "$directory/"
        return files.keys.filter { it.startsWith(prefix) && !it.removePrefix(prefix).contains("/") }
    }

    override suspend fun fileExists(path: String): Boolean = files.containsKey(path)

    override suspend fun createDirectory(path: String) {}

    override suspend fun moveFile(fromPath: String, toPath: String) {
        val content = files.remove(fromPath)
        if (content != null) files[toPath] = content
    }
}

private class LocalFakeLibraryRepository(
    private val bookTerritoryMap: Map<String, String> = emptyMap()
) : LibraryRepository {

    private val books = bookTerritoryMap.map { (bookId, territoryId) ->
        Book(
            id = bookId,
            title = "Test Book",
            author = "Test Author",
            format = BookFormat.EPUB,
            filePath = "$territoryId/books/test.epub",
            coverPath = null,
            addedAt = 0L,
            lastOpenedAt = null,
            readingProgress = ReadingProgress(null, null, 0f, null),
            status = ReadingStatus.UNREAD,
            territoryId = territoryId
        )
    }.toMutableList()

    override suspend fun getAllBooks(territoryId: String): List<Book> =
        books.filter { it.territoryId == territoryId }

    override suspend fun getBook(bookId: String): Book? =
        books.find { it.id == bookId }

    override suspend fun addBook(book: Book): Result<Book, LibraryError> {
        books.add(book)
        return Result.Success(book)
    }

    override suspend fun updateBook(book: Book): Result<Book, LibraryError> {
        val index = books.indexOfFirst { it.id == book.id }
        if (index == -1) return Result.Failure(LibraryError.FileNotFound(book.id))
        books[index] = book
        return Result.Success(book)
    }

    override suspend fun removeBook(bookId: String): Result<Unit, LibraryError> {
        val removed = books.removeAll { it.id == bookId }
        return if (removed) Result.Success(Unit) else Result.Failure(LibraryError.FileNotFound(bookId))
    }

    override suspend fun getLinkedNote(bookId: String): LinkedNote? = null

    override suspend fun createLinkedNote(book: Book): Result<LinkedNote, LibraryError> =
        Result.Failure(LibraryError.WriteError("Not implemented"))

    override fun observeBooks(territoryId: String): Flow<List<Book>> =
        MutableStateFlow<Map<String, List<Book>>>(emptyMap()).map { it[territoryId] ?: emptyList() }
}
