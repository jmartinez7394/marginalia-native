package com.marginalia.ink

data class InkNoteRevision(
    val revisionId: String,
    val sessionId: String,
    val strokes: List<Stroke>,
    val createdAt: Long,
    val transcription: String?
)

data class InkNote(
    val id: String,
    val title: String?,
    val context: InkNoteContext,
    val revisions: List<InkNoteRevision>,
    val currentRevisionId: String,
    val createdAt: Long,
    val territoryId: String
)

sealed class InkNoteContext {
    object Freeform : InkNoteContext()
    data class MarginAnnotation(
        val bookId: String,
        val cfi: String,
        val anchoredText: String?
    ) : InkNoteContext()
    data class CanvasTessera(
        val canvasId: String,
        val tesseraPosition: String
    ) : InkNoteContext()
}
