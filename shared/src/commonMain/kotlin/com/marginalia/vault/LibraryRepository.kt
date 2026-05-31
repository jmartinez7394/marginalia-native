package com.marginalia.vault

import com.marginalia.model.Book
import com.marginalia.model.LinkedNote
import com.marginalia.model.Result
import kotlinx.coroutines.flow.Flow

interface LibraryRepository {
    suspend fun getAllBooks(territoryId: String): List<Book>
    suspend fun getBook(bookId: String): Book?
    suspend fun addBook(book: Book): Result<Book, LibraryError>
    suspend fun updateBook(book: Book): Result<Book, LibraryError>
    suspend fun removeBook(bookId: String): Result<Unit, LibraryError>
    suspend fun getLinkedNote(bookId: String): LinkedNote?
    suspend fun createLinkedNote(book: Book): Result<LinkedNote, LibraryError>
    fun observeBooks(territoryId: String): Flow<List<Book>>
}

sealed class LibraryError {
    data class FileNotFound(val path: String) : LibraryError()
    data class ParseError(val message: String) : LibraryError()
    data class WriteError(val message: String) : LibraryError()
    object BookAlreadyExists : LibraryError()
}
