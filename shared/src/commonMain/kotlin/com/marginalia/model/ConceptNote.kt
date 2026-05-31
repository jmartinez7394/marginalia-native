package com.marginalia.model

data class ConceptNote(
    val id: String,
    val name: String,
    val aliases: List<String>,
    val status: ConceptStatus,
    val practiceDepth: PracticeDepth?,
    val filePath: String,
    val territoryId: String,
    val crossReferences: List<String>,
    val createdAt: Long,
    val lastModifiedAt: Long
)

enum class ConceptStatus { SEED, DEVELOPING, SETTLED }

enum class PracticeDepth { CONCEPTUAL, UNDERSTANDING, GLIMPSED, EMBODIED }
