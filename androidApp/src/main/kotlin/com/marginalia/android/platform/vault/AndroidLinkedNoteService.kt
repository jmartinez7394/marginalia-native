package com.marginalia.android.platform.vault

import android.util.Log
import com.marginalia.animachora.Territory
import com.marginalia.model.Book
import com.marginalia.model.Highlight
import com.marginalia.model.LinkedNote
import com.marginalia.model.Result
import com.marginalia.vault.LinkedNoteError
import com.marginalia.vault.LinkedNoteGenerator
import com.marginalia.vault.LinkedNoteService
import com.marginalia.vault.VaultFileSystem
import java.time.LocalDate

class AndroidLinkedNoteService(
    private val fileSystem: VaultFileSystem
) : LinkedNoteService {

    override suspend fun createLinkedNote(
        book: Book,
        territory: Territory
    ): Result<LinkedNote, LinkedNoteError> {
        return try {
            val today = LocalDate.now().toString()
            val content = LinkedNoteGenerator.generate(book, emptyList(), territory, today)
            val path = linkedNotePath(book, territory)
            fileSystem.createDirectory("${territory.folderPath}/notes")
            fileSystem.writeFile(path, content)
            Log.d(TAG, "createLinkedNote: wrote $path")
            val note = LinkedNote(
                id = book.id,
                bookId = book.id,
                filePath = path,
                title = "${book.title} - ${book.author}",
                createdAt = System.currentTimeMillis(),
                lastModifiedAt = System.currentTimeMillis(),
                territoryId = territory.id
            )
            Result.Success(note)
        } catch (e: Exception) {
            Log.e(TAG, "createLinkedNote failed: ${e.message}")
            Result.Failure(LinkedNoteError.WriteError(e.message ?: "Unknown error"))
        }
    }

    override suspend fun updateLinkedNote(
        linkedNote: LinkedNote,
        highlights: List<Highlight>
    ): Result<LinkedNote, LinkedNoteError> {
        return try {
            val existing = fileSystem.readFile(linkedNote.filePath) ?: ""
            val newHighlights = highlights.filter { h ->
                !existing.contains("^ann-${h.id}")
            }
            if (newHighlights.isEmpty()) {
                return Result.Success(linkedNote)
            }

            val appendBlock = buildString {
                for (h in newHighlights) {
                    append("> ${h.text}\n")
                    append("^ann-${h.id}\n")
                    h.emotionalTag?.let { tag ->
                        append("%%emotion:${tag.name.lowercase()}%%\n")
                    }
                    if (!h.annotation.isNullOrEmpty()) {
                        append("\n*${h.annotation}*\n")
                    }
                    append("\n")
                }
            }

            val highlightsSectionMarker = "## Highlights and Annotations"
            val updated = if (existing.contains(highlightsSectionMarker)) {
                val splitPoint = existing.indexOf("\n---\n", existing.indexOf(highlightsSectionMarker))
                    .takeIf { it >= 0 }
                    ?: existing.length
                existing.substring(0, splitPoint) + "\n" + appendBlock + existing.substring(splitPoint)
            } else {
                existing + "\n## Highlights and Annotations\n\n$appendBlock"
            }

            fileSystem.writeFile(linkedNote.filePath, updated)
            Log.d(TAG, "updateLinkedNote: appended ${newHighlights.size} highlights to ${linkedNote.filePath}")
            val updatedNote = linkedNote.copy(lastModifiedAt = System.currentTimeMillis())
            Result.Success(updatedNote)
        } catch (e: Exception) {
            Log.e(TAG, "updateLinkedNote failed: ${e.message}")
            Result.Failure(LinkedNoteError.WriteError(e.message ?: "Unknown error"))
        }
    }

    override suspend fun getLinkedNote(bookId: String): LinkedNote? = null

    private fun linkedNotePath(book: Book, territory: Territory): String {
        val sanitisedTitle = LinkedNoteGenerator.sanitiseFilename(book.title)
        val sanitisedAuthor = LinkedNoteGenerator.sanitiseFilename(book.author)
        return "${territory.folderPath}/notes/$sanitisedTitle - $sanitisedAuthor.md"
    }

    companion object {
        private const val TAG = "AndroidLinkedNoteService"
    }
}
