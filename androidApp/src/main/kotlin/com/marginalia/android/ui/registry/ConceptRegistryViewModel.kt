package com.marginalia.android.ui.registry

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marginalia.model.ConceptNote
import com.marginalia.model.ConceptStatus
import com.marginalia.vault.ConceptRegistry
import com.marginalia.vault.VaultFileSystem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class ConceptRegistryViewModel @Inject constructor(
    private val conceptRegistry: ConceptRegistry,
    private val fileSystem: VaultFileSystem
) : ViewModel() {

    private val _allConcepts = MutableStateFlow<List<ConceptNote>>(emptyList())
    private val _filterStatus = MutableStateFlow<ConceptStatus?>(null)
    private val _searchQuery = MutableStateFlow("")
    private var currentTerritoryId = "library"

    val filteredConcepts: StateFlow<List<ConceptNote>> = combine(
        _allConcepts, _filterStatus, _searchQuery
    ) { concepts, status, query ->
        concepts
            .filter { status == null || it.status == status }
            .filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
            .sortedWith(compareBy({ statusOrder(it.status) }, { it.name }))
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun loadConcepts(territoryId: String) {
        currentTerritoryId = territoryId
        viewModelScope.launch {
            _allConcepts.value = conceptRegistry.getAllConcepts(territoryId)
        }
    }

    fun setFilter(status: ConceptStatus?) { _filterStatus.value = status }
    fun setSearchQuery(query: String) { _searchQuery.value = query }

    // Returns the raw file content for the editor
    suspend fun loadConceptContent(concept: ConceptNote): String =
        fileSystem.readFile(concept.filePath) ?: ""

    // Save edited concept: update status in frontmatter + append Kintsugi if My Understanding changed
    fun saveConcept(
        concept: ConceptNote,
        newStatus: ConceptStatus,
        newBodyContent: String
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val existing = fileSystem.readFile(concept.filePath) ?: ""
                val oldUnderstanding = extractMyUnderstanding(existing)
                val newUnderstanding = extractSectionFromEditor(newBodyContent, "## My Understanding")
                var updated = updateFrontmatterStatus(existing, newStatus)
                updated = replaceBodySection(updated, newBodyContent)
                if (oldUnderstanding.isNotBlank() && oldUnderstanding != newUnderstanding) {
                    val today = LocalDate.now().toString()
                    updated += "\n---\n\n### Previous Understanding — $today\n\n$oldUnderstanding\n\n"
                }
                fileSystem.writeFile(concept.filePath, updated)
                Log.d(TAG, "Concept saved: ${concept.name} → ${newStatus.name.lowercase()}")
                val updatedConcept = concept.copy(status = newStatus, lastModifiedAt = System.currentTimeMillis())
                _allConcepts.value = _allConcepts.value.map { if (it.id == concept.id) updatedConcept else it }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save concept: ${e.message}")
            }
        }
    }

    private fun statusOrder(status: ConceptStatus) = when (status) {
        ConceptStatus.SETTLED -> 0
        ConceptStatus.DEVELOPING -> 1
        ConceptStatus.SEED -> 2
    }

    private fun extractMyUnderstanding(content: String): String {
        val header = "## My Understanding"
        val start = content.indexOf(header)
        if (start < 0) return ""
        val afterHeader = content.indexOf("\n", start).takeIf { it >= 0 }?.plus(1) ?: return ""
        val nextSection = content.indexOf("\n## ", afterHeader).takeIf { it >= 0 } ?: content.length
        return content.substring(afterHeader, nextSection).trim()
    }

    private fun extractSectionFromEditor(body: String, sectionHeader: String): String {
        val start = body.indexOf(sectionHeader)
        if (start < 0) return ""
        val afterHeader = body.indexOf("\n", start).takeIf { it >= 0 }?.plus(1) ?: return ""
        val nextSection = body.indexOf("\n## ", afterHeader).takeIf { it >= 0 } ?: body.length
        return body.substring(afterHeader, nextSection).trim()
    }

    private fun updateFrontmatterStatus(content: String, status: ConceptStatus): String =
        content.replace(Regex("""status: "[^"]*""""), "status: \"${status.name.lowercase()}\"")

    private fun replaceBodySection(content: String, newBody: String): String {
        // Replace everything after the closing frontmatter delimiter
        val endFm = content.indexOf("---\n", 3).takeIf { it >= 0 }?.let { it + 4 } ?: return newBody
        return content.substring(0, endFm) + "\n" + newBody
    }

    companion object {
        private const val TAG = "ConceptRegistryVM"
    }
}
