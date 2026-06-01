package com.marginalia.vault

import com.marginalia.model.Book
import com.marginalia.model.BookFormat
import com.marginalia.model.LinkedNote
import com.marginalia.model.ReadingProgress
import com.marginalia.model.ReadingStatus
import com.marginalia.model.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeLibraryRepository(
    // Maps bookId → territoryId for test setup
    private val bookTerritoryMap: Map<String, String> = emptyMap()
) : LibraryRepository {

    private val books = mutableListOf<Book>()
    private val _flow = MutableStateFlow<Map<String, List<Book>>>(emptyMap())

    init {
        // Create stub Book entries so getBook(bookId) resolves territory
        bookTerritoryMap.forEach { (bookId, territoryId) ->
            books.add(
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
            )
        }
    }

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
        return if (removed) Result.Success(Unit)
        else Result.Failure(LibraryError.FileNotFound(bookId))
    }

    override suspend fun getLinkedNote(bookId: String): LinkedNote? = null

    override suspend fun createLinkedNote(book: Book): Result<LinkedNote, LibraryError> =
        Result.Failure(LibraryError.WriteError("Not implemented in fake"))

    override fun observeBooks(territoryId: String): Flow<List<Book>> =
        _flow.map { it[territoryId] ?: emptyList() }
}
