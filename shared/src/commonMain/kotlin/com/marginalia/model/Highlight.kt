package com.marginalia.model

import kotlinx.serialization.Serializable

@Serializable
data class Highlight(
    val id: String,
    val bookId: String,
    val href: String = "",
    val cfi: String,
    val locatorJson: String = "",
    val text: String,
    val colour: HighlightColour,
    val annotation: String?,
    val emotionalTag: EmotionalTag? = null,
    val conceptLink: String? = null,
    val createdAt: Long,
    val pageNumber: Int?
)

@Serializable
enum class HighlightColour { YELLOW, GREEN, BLUE, PINK }
