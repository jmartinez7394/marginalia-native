package com.marginalia.scribe

enum class ScribeSourceType { INK_NOTE, MARGIN_ANNOTATION, EXTERNAL_IMAGE }

data class ScribeInput(
    val sourceType: ScribeSourceType,
    val imageBytes: ByteArray,
    val imageWidth: Int,
    val imageHeight: Int,
    val context: ScribeContext,
    val noteId: String,
    val revisionId: String?
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ScribeInput) return false
        return sourceType == other.sourceType &&
            imageBytes.contentEquals(other.imageBytes) &&
            imageWidth == other.imageWidth &&
            imageHeight == other.imageHeight &&
            context == other.context &&
            noteId == other.noteId &&
            revisionId == other.revisionId
    }

    override fun hashCode(): Int {
        var result = sourceType.hashCode()
        result = 31 * result + imageBytes.contentHashCode()
        result = 31 * result + imageWidth
        result = 31 * result + imageHeight
        result = 31 * result + context.hashCode()
        result = 31 * result + noteId.hashCode()
        result = 31 * result + (revisionId?.hashCode() ?: 0)
        return result
    }
}
