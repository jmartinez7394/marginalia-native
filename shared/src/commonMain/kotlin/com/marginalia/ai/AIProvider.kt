package com.marginalia.ai

import com.marginalia.model.Result

interface AIProvider {
    val providerId: String
    val displayName: String
    val supportsVision: Boolean
    val requiresNetwork: Boolean

    suspend fun complete(
        prompt: String,
        systemPrompt: String? = null,
        maxTokens: Int = 1000
    ): Result<String, AIError>

    suspend fun transcribe(
        imageBytes: ByteArray,
        prompt: String,
        systemPrompt: String? = null,
        maxTokens: Int = 2000
    ): Result<String, AIError>

    suspend fun transcribeBatch(
        images: List<ByteArray>,
        prompt: String,
        systemPrompt: String? = null,
        maxTokens: Int = 2000
    ): List<Result<String, AIError>>

    suspend fun validateConfiguration(): Result<Unit, AIError>
}

sealed class AIError {
    data class NetworkError(val message: String) : AIError()
    data class AuthenticationError(val message: String) : AIError()
    data class RateLimitError(val message: String, val retryAfterSeconds: Int?) : AIError()
    data class ModelNotFoundError(val modelId: String) : AIError()
    data class VisionNotSupported(val providerId: String) : AIError()
    data class ContentFilterError(val message: String) : AIError()
    data class UnknownError(val message: String, val cause: Throwable?) : AIError()
}
