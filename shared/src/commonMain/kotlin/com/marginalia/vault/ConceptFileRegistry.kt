package com.marginalia.vault

import com.marginalia.animachora.Territory
import com.marginalia.model.Book
import com.marginalia.model.ConceptNote
import com.marginalia.model.ConceptStatus
import com.marginalia.model.Highlight
import com.marginalia.model.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class ConceptFileRegistry(
    private val fileSystem: VaultFileSystem,
    // Platform provides ISO date string ("2026-06-01") since KMP commonMain has no date library
    private val todayIso: () -> String = { "1970-01-01" }
) : ConceptRegistry {

    private val _conceptsByTerritory = MutableStateFlow<Map<String, List<ConceptNote>>>(emptyMap())

    override suspend fun getAllConcepts(territoryId: String): List<ConceptNote> {
        val notesDir = notesDir(territoryId)
        val files = try { fileSystem.listFiles(notesDir) } catch (e: Exception) { emptyList() }
        // listFiles returns full paths (e.g. "library/notes/virtue-ethics.md")
        val concepts = files
            .filter { it.endsWith(".md") }
            .mapNotNull { filePath ->
                val content = fileSystem.readFile(filePath) ?: return@mapNotNull null
                parseConceptNote(content, filePath, territoryId)
            }
        _conceptsByTerritory.value = _conceptsByTerritory.value + (territoryId to concepts)
        return concepts
    }

    override suspend fun getConcept(conceptId: String): ConceptNote? =
        _conceptsByTerritory.value.values.flatten().find { it.id == conceptId }

    override suspend fun findByName(name: String, territoryId: String): ConceptNote? {
        val all = getAllConcepts(territoryId)
        return all.find { it.name.equals(name, ignoreCase = true) }
    }

    override suspend fun findByAlias(alias: String, territoryId: String): ConceptNote? {
        val all = getAllConcepts(territoryId)
        return all.find { c -> c.aliases.any { it.equals(alias, ignoreCase = true) } }
    }

    override suspend fun addConcept(concept: ConceptNote): Result<ConceptNote, RegistryError> {
        val existing = findByName(concept.name, concept.territoryId)
        if (existing != null) return Result.Failure(RegistryError.DuplicateName(concept.name))
        return try {
            fileSystem.writeFile(concept.filePath, generateConceptFileContent(concept, emptyList()))
            invalidateCache(concept.territoryId)
            Result.Success(concept)
        } catch (e: Exception) {
            Result.Failure(RegistryError.WriteError(e.message ?: "Write failed"))
        }
    }

    override suspend fun updateConcept(concept: ConceptNote): Result<ConceptNote, RegistryError> {
        if (!fileSystem.fileExists(concept.filePath)) return Result.Failure(RegistryError.ConceptNotFound)
        return try {
            val existing = fileSystem.readFile(concept.filePath) ?: ""
            val updated = mergeFrontmatterUpdate(existing, concept)
            fileSystem.writeFile(concept.filePath, updated)
            invalidateCache(concept.territoryId)
            Result.Success(concept)
        } catch (e: Exception) {
            Result.Failure(RegistryError.WriteError(e.message ?: "Write failed"))
        }
    }

    override fun observeConcepts(territoryId: String): Flow<List<ConceptNote>> =
        _conceptsByTerritory.map { it[territoryId] ?: emptyList() }

    override suspend fun createFromHighlight(
        conceptName: String,
        highlight: Highlight,
        sourceBook: Book,
        territory: Territory
    ): Result<ConceptNote, RegistryError> {
        val existing = findByName(conceptName, territory.id)
        if (existing != null) return Result.Failure(RegistryError.DuplicateName(conceptName))

        val today = todayIso()
        val fileName = "${sanitiseConceptFileName(conceptName)}.md"
        val filePath = "${territory.folderPath}/notes/$fileName"
        val crossRef = "${sourceBook.title} — ${sourceBook.author}"
        val concept = ConceptNote(
            id = "concept-${sanitiseConceptFileName(conceptName)}",
            name = conceptName,
            aliases = emptyList(),
            status = ConceptStatus.SEED,
            practiceDepth = null,
            filePath = filePath,
            territoryId = territory.id,
            crossReferences = listOf(crossRef),
            createdAt = 0L,
            lastModifiedAt = 0L
        )
        val content = buildConceptNoteContent(conceptName, highlight, sourceBook, today)
        return try {
            fileSystem.createDirectory("${territory.folderPath}/notes")
            fileSystem.writeFile(filePath, content)
            invalidateCache(territory.id)
            Result.Success(concept)
        } catch (e: Exception) {
            Result.Failure(RegistryError.WriteError(e.message ?: "Write failed"))
        }
    }

    // Sanitise concept name to a valid file name component.
    // "Virtue Ethics" → "virtue-ethics"
    fun sanitiseConceptFileName(name: String): String =
        name.lowercase()
            .replace(Regex("""[^a-z0-9\s]"""), "")
            .trim()
            .replace(Regex("""\s+"""), "-")
            .ifEmpty { "concept" }
            .take(100)

    private fun invalidateCache(territoryId: String) {
        _conceptsByTerritory.value = _conceptsByTerritory.value - territoryId
    }

    private fun notesDir(territoryId: String) = "$territoryId/notes"

    private fun parseConceptNote(content: String, filePath: String, territoryId: String): ConceptNote? {
        val fm = parseFrontmatter(content)
        if (fm["type"] != "concept") return null
        val name = fm["title"] ?: return null
        val status = when (fm["status"]) {
            "developing" -> ConceptStatus.DEVELOPING
            "settled" -> ConceptStatus.SETTLED
            else -> ConceptStatus.SEED
        }
        return ConceptNote(
            id = "concept-${sanitiseConceptFileName(name)}",
            name = name,
            aliases = emptyList(),
            status = status,
            practiceDepth = null,
            filePath = filePath,
            territoryId = territoryId,
            crossReferences = emptyList(),
            createdAt = 0L,
            lastModifiedAt = 0L
        )
    }

    private fun parseFrontmatter(content: String): Map<String, String> {
        if (!content.startsWith("---")) return emptyMap()
        val end = content.indexOf("---", 3).takeIf { it > 0 } ?: return emptyMap()
        val result = mutableMapOf<String, String>()
        content.substring(3, end).lines().forEach { line ->
            val colonIdx = line.indexOf(":")
            if (colonIdx > 0) {
                val key = line.substring(0, colonIdx).trim()
                val value = line.substring(colonIdx + 1).trim().removeSurrounding("\"")
                result[key] = value
            }
        }
        return result
    }

    private fun buildConceptNoteContent(
        conceptName: String,
        highlight: Highlight,
        sourceBook: Book,
        today: String
    ): String = buildString {
        val escapedName = conceptName.replace("\"", "\\\"")
        append("---\n")
        append("title: \"$escapedName\"\n")
        append("aliases: []\n")
        append("type: \"concept\"\n")
        append("status: \"seed\"\n")
        append("firstEncountered: \"$today\"\n")
        append("lastRevisedAt: \"$today\"\n")
        append("relatedConcepts: []\n")
        append("traditions: []\n")
        append("tags: []\n")
        append("---\n\n")
        append("# $conceptName\n\n")
        append("## My Understanding\n\n\n\n")
        append("## Cross-Text References\n\n")
        append("### ${sourceBook.title} — ${sourceBook.author}\n")
        append("> ${highlight.text}\n")
        if (!highlight.annotation.isNullOrEmpty()) {
            append("*${highlight.annotation}*\n")
        }
        append("\n")
        append("## Synthesis Notes\n\n\n\n")
        append("## Open Questions\n\n")
    }

    private fun generateConceptFileContent(concept: ConceptNote, highlights: List<Highlight>): String =
        buildString {
            val escaped = concept.name.replace("\"", "\\\"")
            val today = todayIso()
            append("---\ntitle: \"$escaped\"\naliases: []\ntype: \"concept\"\nstatus: \"seed\"\n")
            append("firstEncountered: \"$today\"\nlastRevisedAt: \"$today\"\n")
            append("relatedConcepts: []\ntraditions: []\ntags: []\n---\n\n")
            append("# ${concept.name}\n\n## My Understanding\n\n\n\n")
            append("## Cross-Text References\n\n## Synthesis Notes\n\n\n\n## Open Questions\n\n")
        }

    private fun mergeFrontmatterUpdate(existing: String, concept: ConceptNote): String {
        // Replace only the status line in the frontmatter, preserve all user body content
        val statusLine = "status: \"${concept.status.name.lowercase()}\""
        return existing.replace(Regex("""status: ".*""""), statusLine)
    }
}
