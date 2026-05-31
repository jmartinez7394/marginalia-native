package com.marginalia.scribe

import com.marginalia.model.ConceptNote

data class ScribeContext(
    val noteTitle: String?,
    val territoryId: String,
    val territoryName: String,
    val anchoredPassageText: String?,
    val bookTitle: String?,
    val author: String?,
    val chapterLabel: String?,
    val conceptRegistry: List<ConceptNote>,
    val userDescription: String?
)
