package com.marginalia.vault

import com.marginalia.animachora.Territory
import com.marginalia.model.Book
import com.marginalia.model.ConceptNote
import com.marginalia.model.Highlight
import com.marginalia.model.Result
import kotlinx.coroutines.flow.Flow

interface ConceptRegistry {
    suspend fun getAllConcepts(territoryId: String): List<ConceptNote>
    suspend fun getConcept(conceptId: String): ConceptNote?
    suspend fun findByName(name: String, territoryId: String): ConceptNote?
    suspend fun findByAlias(alias: String, territoryId: String): ConceptNote?
    suspend fun addConcept(concept: ConceptNote): Result<ConceptNote, RegistryError>
    suspend fun updateConcept(concept: ConceptNote): Result<ConceptNote, RegistryError>
    fun observeConcepts(territoryId: String): Flow<List<ConceptNote>>
    suspend fun createFromHighlight(
        conceptName: String,
        highlight: Highlight,
        sourceBook: Book,
        territory: Territory
    ): Result<ConceptNote, RegistryError>
}

sealed class RegistryError {
    data class WriteError(val message: String) : RegistryError()
    object ConceptNotFound : RegistryError()
    data class DuplicateName(val name: String) : RegistryError()
}
