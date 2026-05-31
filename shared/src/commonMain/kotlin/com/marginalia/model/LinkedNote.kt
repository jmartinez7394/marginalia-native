package com.marginalia.model

data class LinkedNote(
    val id: String,
    val bookId: String,
    val filePath: String,
    val title: String,
    val createdAt: Long,
    val lastModifiedAt: Long,
    val territoryId: String
)
