package com.marginalia.vault

import com.marginalia.model.Highlight
import com.marginalia.model.Result
import kotlinx.coroutines.flow.Flow

interface HighlightRepository {
    suspend fun getHighlights(bookId: String): List<Highlight>
    suspend fun addHighlight(highlight: Highlight): Result<Highlight, HighlightError>
    suspend fun updateHighlight(highlight: Highlight): Result<Highlight, HighlightError>
    suspend fun deleteHighlight(highlightId: String): Result<Unit, HighlightError>
    fun observeHighlights(bookId: String): Flow<List<Highlight>>
}

sealed class HighlightError {
    data class WriteError(val message: String) : HighlightError()
    object HighlightNotFound : HighlightError()
    data class ParseError(val message: String) : HighlightError()
}
