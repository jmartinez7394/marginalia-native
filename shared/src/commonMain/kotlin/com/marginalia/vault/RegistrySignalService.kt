package com.marginalia.vault

import com.marginalia.model.RegistrySignal
import com.marginalia.model.SignalSourceType
import com.marginalia.model.SignalUserAction

interface RegistrySignalService {
    suspend fun recordSignal(
        conceptCandidate: String,
        sourceType: SignalSourceType,
        sourceId: String,
        sourcePath: String,
        territoryId: String
    )
    suspend fun getSignals(territoryId: String): List<RegistrySignal>
    suspend fun getPendingCandidates(
        territoryId: String,
        minOccurrences: Int = 3
    ): List<RegistrySignal>
    suspend fun processUserAction(
        signalId: String,
        action: SignalUserAction,
        territoryId: String
    )
}
