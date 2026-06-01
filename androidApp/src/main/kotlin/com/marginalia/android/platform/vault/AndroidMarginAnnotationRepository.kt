package com.marginalia.android.platform.vault

import com.marginalia.model.MarginAnnotation
import com.marginalia.model.Result
import com.marginalia.vault.LibraryRepository
import com.marginalia.vault.MarginAnnotationError
import com.marginalia.vault.MarginAnnotationRepository
import com.marginalia.vault.VaultFileSystem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class MarginAnnotationJson(
    val schemaVersion: Int,
    val annotationId: String,
    val bookId: String,
    val bookTitle: String,
    val author: String,
    val linkedNotePath: String,
    val anchorCfi: String,
    val anchoredPassageText: String?,
    val chapterLabel: String?,
    val createdAt: Long,
    val inkNoteId: String,
    val processed: Boolean,
    val processedAt: Long?,
    val linkedNoteBlockRef: String?,
    val transcription: String?,
    val promoted: Boolean,
    val promotedNoteId: String?
)

@Singleton
class AndroidMarginAnnotationRepository @Inject constructor(
    private val fileSystem: VaultFileSystem,
    private val libraryRepository: LibraryRepository
) : MarginAnnotationRepository {

    private val json = Json { ignoreUnknownKeys = true }

    // bookId → list of annotations for that book
    private val _annotations = MutableStateFlow<Map<String, List<MarginAnnotation>>>(emptyMap())

    override suspend fun getAnnotationsForBook(bookId: String): List<MarginAnnotation> {
        val territoryId = resolveTerritory(bookId) ?: return emptyList()
        val loaded = loadAnnotationsForBook(territoryId, bookId)
        _annotations.value = _annotations.value + (bookId to loaded)
        return loaded
    }

    override suspend fun getAnnotation(annotationId: String): MarginAnnotation? =
        _annotations.value.values.flatten().find { it.annotationId == annotationId }

    override suspend fun saveAnnotation(annotation: MarginAnnotation): Result<MarginAnnotation, MarginAnnotationError> {
        val territoryId = resolveTerritory(annotation.bookId)
            ?: return Result.Failure(MarginAnnotationError.WriteError("Book not found: ${annotation.bookId}"))
        return try {
            val dir = marginNotesDir(territoryId)
            fileSystem.createDirectory(dir)
            fileSystem.writeFile("$dir/${fileName(annotation)}", encode(annotation))
            val current = _annotations.value[annotation.bookId] ?: emptyList()
            _annotations.value = _annotations.value + (annotation.bookId to (current + annotation))
            Result.Success(annotation)
        } catch (e: Exception) {
            Result.Failure(MarginAnnotationError.WriteError(e.message ?: "Write failed"))
        }
    }

    override suspend fun updateAnnotation(annotation: MarginAnnotation): Result<MarginAnnotation, MarginAnnotationError> {
        val territoryId = resolveTerritory(annotation.bookId)
            ?: return Result.Failure(MarginAnnotationError.WriteError("Book not found: ${annotation.bookId}"))
        val existing = _annotations.value[annotation.bookId]
            ?.find { it.annotationId == annotation.annotationId }
            ?: return Result.Failure(MarginAnnotationError.AnnotationNotFound)
        return try {
            val dir = marginNotesDir(territoryId)
            val oldName = fileName(existing)
            val newName = fileName(annotation)
            if (oldName != newName) {
                fileSystem.deleteFile("$dir/$oldName")
            }
            fileSystem.writeFile("$dir/$newName", encode(annotation))
            val updated = (_annotations.value[annotation.bookId] ?: emptyList())
                .map { if (it.annotationId == annotation.annotationId) annotation else it }
            _annotations.value = _annotations.value + (annotation.bookId to updated)
            Result.Success(annotation)
        } catch (e: Exception) {
            Result.Failure(MarginAnnotationError.WriteError(e.message ?: "Update failed"))
        }
    }

    override suspend fun deleteAnnotation(annotationId: String): Result<Unit, MarginAnnotationError> {
        val entry = _annotations.value.entries
            .firstNotNullOfOrNull { (bookId, list) ->
                list.find { it.annotationId == annotationId }?.let { bookId to it }
            } ?: return Result.Failure(MarginAnnotationError.AnnotationNotFound)
        val (bookId, annotation) = entry
        val territoryId = resolveTerritory(bookId)
            ?: return Result.Failure(MarginAnnotationError.AnnotationNotFound)
        return try {
            fileSystem.deleteFile("${marginNotesDir(territoryId)}/${fileName(annotation)}")
            val updated = (_annotations.value[bookId] ?: emptyList())
                .filter { it.annotationId != annotationId }
            _annotations.value = _annotations.value + (bookId to updated)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(MarginAnnotationError.WriteError(e.message ?: "Delete failed"))
        }
    }

    override fun observeAnnotations(bookId: String): Flow<List<MarginAnnotation>> =
        _annotations.map { it[bookId] ?: emptyList() }

    private suspend fun resolveTerritory(bookId: String): String? =
        libraryRepository.getBook(bookId)?.territoryId

    private suspend fun loadAnnotationsForBook(
        territoryId: String,
        bookId: String
    ): List<MarginAnnotation> {
        val dir = marginNotesDir(territoryId)
        return try {
            fileSystem.listFiles(dir)
                .filter { it.endsWith(".json") && fileNamePart(it).startsWith("$bookId-") }
                .mapNotNull { path ->
                    val raw = fileSystem.readFile(path) ?: return@mapNotNull null
                    runCatching { decode(stripBom(raw)) }.getOrNull()
                }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun fileNamePart(path: String): String = path.substringAfterLast("/")

    private fun marginNotesDir(territoryId: String) = "$territoryId/.marginalia/margin-notes"

    internal fun fileName(annotation: MarginAnnotation): String =
        "${annotation.bookId}-${cfiHash(annotation.anchorCfi)}-${annotation.createdAt}.json"

    internal fun cfiHash(cfi: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(cfi.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }.take(8)
    }

    private fun encode(annotation: MarginAnnotation): String = json.encodeToString(
        MarginAnnotationJson(
            schemaVersion = 1,
            annotationId = annotation.annotationId,
            bookId = annotation.bookId,
            bookTitle = annotation.bookTitle,
            author = annotation.author,
            linkedNotePath = annotation.linkedNotePath,
            anchorCfi = annotation.anchorCfi,
            anchoredPassageText = annotation.anchoredPassageText,
            chapterLabel = annotation.chapterLabel,
            createdAt = annotation.createdAt,
            inkNoteId = annotation.inkNoteId,
            processed = annotation.processed,
            processedAt = annotation.processedAt,
            linkedNoteBlockRef = annotation.linkedNoteBlockRef,
            transcription = annotation.transcription,
            promoted = annotation.promoted,
            promotedNoteId = annotation.promotedNoteId
        )
    )

    private fun decode(content: String): MarginAnnotation {
        val s = json.decodeFromString<MarginAnnotationJson>(content)
        return MarginAnnotation(
            annotationId = s.annotationId,
            bookId = s.bookId,
            bookTitle = s.bookTitle,
            author = s.author,
            linkedNotePath = s.linkedNotePath,
            anchorCfi = s.anchorCfi,
            anchoredPassageText = s.anchoredPassageText,
            chapterLabel = s.chapterLabel,
            createdAt = s.createdAt,
            inkNoteId = s.inkNoteId,
            processed = s.processed,
            processedAt = s.processedAt,
            linkedNoteBlockRef = s.linkedNoteBlockRef,
            transcription = s.transcription,
            promoted = s.promoted,
            promotedNoteId = s.promotedNoteId
        )
    }

    private fun stripBom(raw: String): String =
        if (raw.isNotEmpty() && raw[0].code == 0xFEFF) raw.drop(1) else raw
}
