package com.marginalia.model

import kotlinx.serialization.Serializable

@Serializable
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

@Serializable
enum class HighlightColour { YELLOW, GREEN, BLUE, PINK }
