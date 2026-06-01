package com.marginalia.model

import kotlinx.serialization.Serializable

@Serializable
data class RegistrySignal(
    val signalId: String,
    val conceptCandidate: String,
    val sourceType: SignalSourceType,
    val sourceId: String,
    val sourcePath: String,
    val occurrenceCount: Int,
    val firstSeenAt: Long,
    val lastSeenAt: Long,
    val processed: Boolean,
    val userAction: SignalUserAction?
)

@Serializable
enum class SignalSourceType { HIGHLIGHT, ANNOTATION, SCRIBE_OUTPUT }

@Serializable
enum class SignalUserAction { ACCEPTED, REJECTED, DEFERRED, MERGED }
