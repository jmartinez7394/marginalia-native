package com.marginalia.vault

import com.marginalia.model.Highlight
import com.marginalia.model.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
private data class HighlightsJson(
    val schemaVersion: Int,
    val bookId: String,
    val highlights: List<Highlight>
)

class HighlightFileRepository(
    private val fileSystem: VaultFileSystem,
    private val libraryRepository: LibraryRepository
) : HighlightRepository {

    private val json = Json { ignoreUnknownKeys = true }

    // Key: bookId → list of highlights. Territory is resolved per-call.
    private val _bookHighlights = MutableStateFlow<Map<String, List<Highlight>>>(emptyMap())

    override suspend fun getHighlights(bookId: String): List<Highlight> {
        val territoryId = resolveTerritory(bookId)
            ?: return emptyList()
        val loaded = loadFromDisk(territoryId, bookId)
        _bookHighlights.value = _bookHighlights.value + (bookId to loaded)
        return loaded
    }

    override suspend fun addHighlight(highlight: Highlight): Result<Highlight, HighlightError> {
        val territoryId = resolveTerritory(highlight.bookId)
            ?: return Result.Failure(HighlightError.WriteError("Book not found: ${highlight.bookId}"))
        val existing = loadFromDisk(territoryId, highlight.bookId)
        val updated = existing + highlight
        return if (writeToDisk(territoryId, highlight.bookId, updated)) {
            _bookHighlights.value = _bookHighlights.value + (highlight.bookId to updated)
            Result.Success(highlight)
        } else {
            Result.Failure(HighlightError.WriteError("Failed to write highlights for ${highlight.bookId}"))
        }
    }

    override suspend fun updateHighlight(highlight: Highlight): Result<Highlight, HighlightError> {
        val territoryId = resolveTerritory(highlight.bookId)
            ?: return Result.Failure(HighlightError.WriteError("Book not found: ${highlight.bookId}"))
        val existing = loadFromDisk(territoryId, highlight.bookId)
        if (existing.none { it.id == highlight.id }) {
            return Result.Failure(HighlightError.HighlightNotFound)
        }
        val updated = existing.map { if (it.id == highlight.id) highlight else it }
        return if (writeToDisk(territoryId, highlight.bookId, updated)) {
            _bookHighlights.value = _bookHighlights.value + (highlight.bookId to updated)
            Result.Success(highlight)
        } else {
            Result.Failure(HighlightError.WriteError("Failed to write highlights for ${highlight.bookId}"))
        }
    }

    override suspend fun deleteHighlight(highlightId: String): Result<Unit, HighlightError> {
        val bookId = _bookHighlights.value.entries
            .find { (_, list) -> list.any { it.id == highlightId } }?.key
            ?: return Result.Failure(HighlightError.HighlightNotFound)
        val territoryId = resolveTerritory(bookId)
            ?: return Result.Failure(HighlightError.HighlightNotFound)
        val existing = loadFromDisk(territoryId, bookId)
        val updated = existing.filter { it.id != highlightId }
        return if (writeToDisk(territoryId, bookId, updated)) {
            _bookHighlights.value = _bookHighlights.value + (bookId to updated)
            Result.Success(Unit)
        } else {
            Result.Failure(HighlightError.WriteError("Failed to write highlights for $bookId"))
        }
    }

    override fun observeHighlights(bookId: String): Flow<List<Highlight>> =
        _bookHighlights.map { it[bookId] ?: emptyList() }

    private suspend fun resolveTerritory(bookId: String): String? =
        libraryRepository.getBook(bookId)?.territoryId

    private suspend fun loadFromDisk(territoryId: String, bookId: String): List<Highlight> {
        val path = highlightsPath(territoryId, bookId)
        val raw = fileSystem.readFile(path) ?: return emptyList()

        // Strip UTF-8 BOM (U+FEFF) if present — added by Windows editors and some tools.
        val content = if (raw.isNotEmpty() && raw[0].code == 0xFEFF) raw.drop(1) else raw

        return try {
            json.decodeFromString<HighlightsJson>(content).highlights
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun writeToDisk(
        territoryId: String,
        bookId: String,
        highlights: List<Highlight>
    ): Boolean {
        return try {
            fileSystem.createDirectory("$territoryId/.marginalia/highlights")
            val data = HighlightsJson(schemaVersion = 1, bookId = bookId, highlights = highlights)
            fileSystem.writeFile(highlightsPath(territoryId, bookId), json.encodeToString(data))
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun highlightsPath(territoryId: String, bookId: String) =
        "$territoryId/.marginalia/highlights/$bookId.json"
}
