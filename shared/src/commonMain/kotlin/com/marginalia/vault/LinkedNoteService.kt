package com.marginalia.vault

import com.marginalia.animachora.Territory
import com.marginalia.model.Book
import com.marginalia.model.Highlight
import com.marginalia.model.LinkedNote
import com.marginalia.model.Result

interface LinkedNoteService {
    suspend fun createLinkedNote(
        book: Book,
        territory: Territory
    ): Result<LinkedNote, LinkedNoteError>

    suspend fun updateLinkedNote(
        linkedNote: LinkedNote,
        highlights: List<Highlight>
    ): Result<LinkedNote, LinkedNoteError>

    suspend fun getLinkedNote(bookId: String): LinkedNote?
}

sealed class LinkedNoteError {
    data class WriteError(val message: String) : LinkedNoteError()
    object BookNotFound : LinkedNoteError()
}
