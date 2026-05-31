package com.marginalia.model

data class CfiAnchor(
    val cfi: String,
    val bookId: String,
    val chapterTitle: String?,
    val nearbyText: String?
)
