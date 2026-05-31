package com.marginalia.model

data class Highlight(
    val id: String,
    val bookId: String,
    val cfi: String,
    val text: String,
    val colour: HighlightColour,
    val annotation: String?,
    val createdAt: Long,
    val pageNumber: Int?
)

enum class HighlightColour { YELLOW, GREEN, BLUE, PINK }
