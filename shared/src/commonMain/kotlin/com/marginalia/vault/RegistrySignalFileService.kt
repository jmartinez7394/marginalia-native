package com.marginalia.vault

import com.marginalia.model.RegistrySignal
import com.marginalia.model.SignalSourceType
import com.marginalia.model.SignalUserAction
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Serializable
private data class SignalsJson(
    val schemaVersion: Int = 1,
    val signals: List<RegistrySignal>
)

class RegistrySignalFileService(
    private val fileSystem: VaultFileSystem,
    // Injected by platform — allows deterministic timestamps in tests
    private val clock: () -> Long = { 0L }
) : RegistrySignalService {

    private val json = Json { ignoreUnknownKeys = true }

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun recordSignal(
        conceptCandidate: String,
        sourceType: SignalSourceType,
        sourceId: String,
        sourcePath: String,
        territoryId: String
    ) {
        val signals = readSignals(territoryId).toMutableList()
        val now = clock()
        val existingIdx = signals.indexOfFirst {
            it.conceptCandidate.equals(conceptCandidate, ignoreCase = true) && !it.processed
        }
        if (existingIdx >= 0) {
            signals[existingIdx] = signals[existingIdx].copy(
                occurrenceCount = signals[existingIdx].occurrenceCount + 1,
                lastSeenAt = now
            )
        } else {
            signals.add(
                RegistrySignal(
                    signalId = "sig-${Uuid.random()}",
                    conceptCandidate = conceptCandidate,
                    sourceType = sourceType,
                    sourceId = sourceId,
                    sourcePath = sourcePath,
                    occurrenceCount = 1,
                    firstSeenAt = now,
                    lastSeenAt = now,
                    processed = false,
                    userAction = null
                )
            )
        }
        writeSignals(territoryId, signals)
    }

    override suspend fun getSignals(territoryId: String): List<RegistrySignal> =
        readSignals(territoryId)

    override suspend fun getPendingCandidates(
        territoryId: String,
        minOccurrences: Int
    ): List<RegistrySignal> =
        readSignals(territoryId).filter {
            !it.processed
                && it.userAction != SignalUserAction.DEFERRED
                && it.occurrenceCount >= minOccurrences
        }

    override suspend fun processUserAction(
        signalId: String,
        action: SignalUserAction,
        territoryId: String
    ) {
        val signals = readSignals(territoryId).toMutableList()
        val idx = signals.indexOfFirst { it.signalId == signalId }
        if (idx < 0) return
        // DEFERRED keeps processed=false so the signal remains visible after the defer window.
        // All other actions mark the signal as processed and won't surface again.
        val processed = action != SignalUserAction.DEFERRED
        signals[idx] = signals[idx].copy(processed = processed, userAction = action)
        writeSignals(territoryId, signals)
    }

    private suspend fun readSignals(territoryId: String): List<RegistrySignal> {
        val raw = fileSystem.readFile(signalsPath(territoryId)) ?: return emptyList()
        val content = if (raw.isNotEmpty() && raw[0].code == 0xFEFF) raw.drop(1) else raw
        return try {
            json.decodeFromString<SignalsJson>(content).signals
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun writeSignals(territoryId: String, signals: List<RegistrySignal>) {
        fileSystem.createDirectory("$territoryId/.marginalia")
        fileSystem.writeFile(signalsPath(territoryId), json.encodeToString(SignalsJson(signals = signals)))
    }

    private fun signalsPath(territoryId: String) =
        "$territoryId/.marginalia/registry-signals.json"

}
