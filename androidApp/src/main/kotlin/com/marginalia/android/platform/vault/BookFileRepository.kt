package com.marginalia.android.platform.vault

import android.util.Log
import com.marginalia.model.Book
import com.marginalia.model.LinkedNote
import com.marginalia.model.Result
import com.marginalia.vault.LibraryError
import com.marginalia.vault.LibraryRepository
import com.marginalia.vault.VaultFileSystem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
private data class LibraryJson(
    val schemaVersion: Int,
    val books: List<Book>
)

class BookFileRepository(
    private val fileSystem: VaultFileSystem
) : LibraryRepository {

    private val json = Json { ignoreUnknownKeys = true }
    private val _territoryBooks = MutableStateFlow<Map<String, List<Book>>>(emptyMap())

    override suspend fun getAllBooks(territoryId: String): List<Book> {
        val loaded = loadFromDisk(territoryId)
        _territoryBooks.value = _territoryBooks.value + (territoryId to loaded)
        return loaded
    }

    override suspend fun getBook(bookId: String): Book? {
        return _territoryBooks.value.values.flatten().find { it.id == bookId }
    }

    override suspend fun addBook(book: Book): Result<Book, LibraryError> {
        val existing = loadFromDisk(book.territoryId)
        if (existing.any { it.id == book.id }) {
            return Result.Failure(LibraryError.BookAlreadyExists)
        }
        val updated = existing + book
        val writeResult = writeToDisk(book.territoryId, updated)
        return if (writeResult) {
            _territoryBooks.value = _territoryBooks.value + (book.territoryId to updated)
            Result.Success(book)
        } else {
            Result.Failure(LibraryError.WriteError("Failed to write library.json"))
        }
    }

    override suspend fun updateBook(book: Book): Result<Book, LibraryError> {
        val existing = loadFromDisk(book.territoryId)
        if (existing.none { it.id == book.id }) {
            return Result.Failure(LibraryError.FileNotFound(libraryJsonPath(book.territoryId)))
        }
        val updated = existing.map { if (it.id == book.id) book else it }
        val writeResult = writeToDisk(book.territoryId, updated)
        return if (writeResult) {
            _territoryBooks.value = _territoryBooks.value + (book.territoryId to updated)
            Result.Success(book)
        } else {
            Result.Failure(LibraryError.WriteError("Failed to write library.json"))
        }
    }

    override suspend fun removeBook(bookId: String): Result<Unit, LibraryError> {
        val territoryId = _territoryBooks.value.entries
            .find { (_, books) -> books.any { it.id == bookId } }?.key
            ?: return Result.Failure(LibraryError.FileNotFound(bookId))
        val existing = loadFromDisk(territoryId)
        val updated = existing.filter { it.id != bookId }
        val writeResult = writeToDisk(territoryId, updated)
        return if (writeResult) {
            _territoryBooks.value = _territoryBooks.value + (territoryId to updated)
            Result.Success(Unit)
        } else {
            Result.Failure(LibraryError.WriteError("Failed to write library.json"))
        }
    }

    override suspend fun getLinkedNote(bookId: String): LinkedNote? = null

    override suspend fun createLinkedNote(book: Book): Result<LinkedNote, LibraryError> =
        Result.Failure(LibraryError.WriteError("Not implemented in this session"))

    override fun observeBooks(territoryId: String): Flow<List<Book>> =
        _territoryBooks.map { it[territoryId] ?: emptyList() }

    private suspend fun loadFromDisk(territoryId: String): List<Book> {
        val path = libraryJsonPath(territoryId)
        val raw = fileSystem.readFile(path)
        if (raw == null) {
            Log.d(TAG, "loadFromDisk: no file at $path")
            return emptyList()
        }
        Log.d(TAG, "loadFromDisk: read ${raw.length} chars from $path")

        // Strip UTF-8 BOM (U+FEFF) if present — added by Windows editors and some tools.
        // trimStart removes all leading BOM chars, not just one.
        val hasBom = raw.isNotEmpty() && raw[0].code == 0xFEFF
        val content = if (hasBom) {
            Log.d(TAG, "loadFromDisk: stripping UTF-8 BOM from $path")
            raw.drop(1)
        } else {
            raw
        }

        return try {
            val result = json.decodeFromString<LibraryJson>(content).books
            Log.d(TAG, "loadFromDisk: parsed ${result.size} books from $path")
            result
        } catch (e: Exception) {
            Log.e(TAG, "loadFromDisk: JSON parse failed for $path — ${e.message}")
            Log.e(TAG, "loadFromDisk: content preview: ${content.take(200)}")
            emptyList()
        }
    }

    private suspend fun writeToDisk(territoryId: String, books: List<Book>): Boolean {
        return try {
            val path = libraryJsonPath(territoryId)
            fileSystem.createDirectory("$territoryId/.marginalia")
            val libraryJson = LibraryJson(schemaVersion = 1, books = books)
            fileSystem.writeFile(path, json.encodeToString(libraryJson))
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun libraryJsonPath(territoryId: String) = "$territoryId/.marginalia/library.json"

    companion object {
        private const val TAG = "BookFileRepository"
    }
}
