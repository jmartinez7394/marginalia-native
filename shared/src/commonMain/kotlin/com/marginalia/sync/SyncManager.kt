package com.marginalia.sync

import kotlinx.coroutines.flow.Flow

interface SyncManager {
    suspend fun sync(): SyncResult
    suspend fun push(): SyncResult
    suspend fun pull(): SyncResult
    val syncState: Flow<SyncState>
    fun isConfigured(): Boolean
}

sealed class SyncResult {
    object Success : SyncResult()
    data class Conflict(val conflictedFiles: List<String>) : SyncResult()
    data class Error(val message: String) : SyncResult()
    object NotConfigured : SyncResult()
}

sealed class SyncState {
    object Idle : SyncState()
    object Syncing : SyncState()
    data class LastSync(val at: Long, val result: SyncResult) : SyncState()
}
