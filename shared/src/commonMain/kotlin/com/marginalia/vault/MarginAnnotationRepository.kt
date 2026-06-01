package com.marginalia.vault

import com.marginalia.model.MarginAnnotation
import com.marginalia.model.Result
import kotlinx.coroutines.flow.Flow

interface MarginAnnotationRepository {
    suspend fun getAnnotationsForBook(bookId: String): List<MarginAnnotation>
    suspend fun getAnnotation(annotationId: String): MarginAnnotation?
    suspend fun saveAnnotation(annotation: MarginAnnotation): Result<MarginAnnotation, MarginAnnotationError>
    suspend fun updateAnnotation(annotation: MarginAnnotation): Result<MarginAnnotation, MarginAnnotationError>
    suspend fun deleteAnnotation(annotationId: String): Result<Unit, MarginAnnotationError>
    fun observeAnnotations(bookId: String): Flow<List<MarginAnnotation>>
}

sealed class MarginAnnotationError {
    data class WriteError(val message: String) : MarginAnnotationError()
    object AnnotationNotFound : MarginAnnotationError()
    data class ParseError(val message: String) : MarginAnnotationError()
}
