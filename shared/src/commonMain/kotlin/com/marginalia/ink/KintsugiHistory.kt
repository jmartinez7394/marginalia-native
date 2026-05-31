package com.marginalia.ink

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

object KintsugiHistory {

    fun currentRevision(note: InkNote): InkNoteRevision =
        note.revisions.first { it.revisionId == note.currentRevisionId }

    fun previousRevisions(note: InkNote): List<InkNoteRevision> =
        note.revisions.filter { it.revisionId != note.currentRevisionId }

    @OptIn(ExperimentalUuidApi::class)
    fun addRevision(
        note: InkNote,
        newStrokes: List<Stroke>,
        sessionId: String,
        createdAt: Long = 0L
    ): InkNote {
        val newRevisionId = Uuid.random().toString()
        val newRevision = InkNoteRevision(
            revisionId = newRevisionId,
            sessionId = sessionId,
            strokes = newStrokes,
            createdAt = createdAt,
            transcription = null
        )
        return note.copy(
            revisions = note.revisions + newRevision,
            currentRevisionId = newRevisionId
        )
    }

    fun hasMultipleRevisions(note: InkNote): Boolean =
        note.revisions.size > 1
}
