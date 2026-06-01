package com.marginalia.model

import kotlinx.serialization.Serializable

@Serializable
data class MarginAnnotation(
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
    val processed: Boolean = false,
    val processedAt: Long? = null,
    val linkedNoteBlockRef: String? = null,
    val transcription: String? = null,
    val promoted: Boolean = false,
    val promotedNoteId: String? = null
)
