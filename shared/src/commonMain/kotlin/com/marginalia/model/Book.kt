package com.marginalia.model

import kotlinx.serialization.Serializable

@Serializable
data class Book(
    val id: String,
    val title: String,
    val author: String,
    val format: BookFormat,
    val filePath: String,
    val coverPath: String?,
    val addedAt: Long,
    val lastOpenedAt: Long?,
    val readingProgress: ReadingProgress,
    val status: ReadingStatus,
    val territoryId: String
)

@Serializable
enum class BookFormat { EPUB, PDF, MOBI, AZW3, FB2, CBZ }

@Serializable
enum class ReadingStatus { UNREAD, READING, FINISHED, ABANDONED }

@Serializable
data class ReadingProgress(
    val cfi: String?,
    val pageNumber: Int?,
    val percentage: Float,
    val lastReadAt: Long?
) {
    companion object {
        fun create(
            cfi: String?,
            pageNumber: Int?,
            percentage: Float,
            lastReadAt: Long?
        ): ReadingProgress = ReadingProgress(
            cfi = cfi,
            pageNumber = pageNumber,
            percentage = percentage.coerceIn(0f, 1f),
            lastReadAt = lastReadAt
        )
    }
}
