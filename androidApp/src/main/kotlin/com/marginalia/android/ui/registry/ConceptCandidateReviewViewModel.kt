package com.marginalia.android.ui.registry

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marginalia.model.ConceptNote
import com.marginalia.model.ConceptStatus
import com.marginalia.model.RegistrySignal
import com.marginalia.model.SignalUserAction
import com.marginalia.vault.ConceptRegistry
import com.marginalia.vault.RegistrySignalService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class ConceptCandidateReviewViewModel @Inject constructor(
    private val registrySignalService: RegistrySignalService,
    private val conceptRegistry: ConceptRegistry
) : ViewModel() {

    private val _candidates = MutableStateFlow<List<RegistrySignal>>(emptyList())
    val candidates: StateFlow<List<RegistrySignal>> = _candidates.asStateFlow()

    private var currentTerritoryId = "library"

    fun loadCandidates(territoryId: String) {
        currentTerritoryId = territoryId
        viewModelScope.launch {
            _candidates.value = registrySignalService.getPendingCandidates(territoryId)
        }
    }

    fun accept(signal: RegistrySignal) {
        viewModelScope.launch(Dispatchers.IO) {
            // Create a SEED concept note from the candidate term
            val today = LocalDate.now().toString()
            val fileName = signal.conceptCandidate.lowercase()
                .replace(Regex("""[^a-z0-9\s]"""), "")
                .trim()
                .replace(Regex("""\s+"""), "-")
                .ifEmpty { "concept" }
                .take(100)
            val concept = ConceptNote(
                id = "concept-$fileName",
                name = signal.conceptCandidate,
                aliases = emptyList(),
                status = ConceptStatus.SEED,
                practiceDepth = null,
                filePath = "$currentTerritoryId/notes/$fileName.md",
                territoryId = currentTerritoryId,
                crossReferences = emptyList(),
                createdAt = 0L,
                lastModifiedAt = 0L
            )
            when (val result = conceptRegistry.addConcept(concept)) {
                is com.marginalia.model.Result.Success -> {
                    Log.d(TAG, "Accepted concept: ${signal.conceptCandidate}")
                    registrySignalService.processUserAction(signal.signalId, SignalUserAction.ACCEPTED, currentTerritoryId)
                    removeCandidate(signal)
                }
                is com.marginalia.model.Result.Failure -> {
                    // Concept may already exist — still mark signal as accepted
                    Log.w(TAG, "Accept concept failed (may exist): ${result.error}")
                    registrySignalService.processUserAction(signal.signalId, SignalUserAction.ACCEPTED, currentTerritoryId)
                    removeCandidate(signal)
                }
            }
        }
    }

    fun defer(signal: RegistrySignal) {
        viewModelScope.launch(Dispatchers.IO) {
            registrySignalService.processUserAction(signal.signalId, SignalUserAction.DEFERRED, currentTerritoryId)
            removeCandidate(signal)
            Log.d(TAG, "Deferred: ${signal.conceptCandidate}")
        }
    }

    fun dismiss(signal: RegistrySignal) {
        viewModelScope.launch(Dispatchers.IO) {
            registrySignalService.processUserAction(signal.signalId, SignalUserAction.REJECTED, currentTerritoryId)
            removeCandidate(signal)
            Log.d(TAG, "Dismissed: ${signal.conceptCandidate}")
        }
    }

    private fun removeCandidate(signal: RegistrySignal) {
        _candidates.value = _candidates.value.filter { it.signalId != signal.signalId }
    }

    companion object {
        private const val TAG = "CandidateReviewVM"
    }
}
