package com.marginalia.ai

import com.marginalia.model.Result

class FakeAIProvider(
    override val providerId: String = "fake",
    override val displayName: String = "Fake AI",
    override val supportsVision: Boolean = true,
    override val requiresNetwork: Boolean = false
) : AIProvider {

    val callsRecorded = mutableListOf<String>()
    var completeResponse: Result<String, AIError> = Result.Success("fake response")
    var transcribeResponse: Result<String, AIError> = Result.Success("fake transcription")
    var validateResponse: Result<Unit, AIError> = Result.Success(Unit)

    override suspend fun complete(
        prompt: String,
        systemPrompt: String?,
        maxTokens: Int
    ): Result<String, AIError> {
        callsRecorded.add("complete")
        return completeResponse
    }

    override suspend fun transcribe(
        imageBytes: ByteArray,
        prompt: String,
        systemPrompt: String?,
        maxTokens: Int
    ): Result<String, AIError> {
        callsRecorded.add("transcribe")
        return transcribeResponse
    }

    override suspend fun transcribeBatch(
        images: List<ByteArray>,
        prompt: String,
        systemPrompt: String?,
        maxTokens: Int
    ): List<Result<String, AIError>> {
        callsRecorded.add("transcribeBatch:${images.size}")
        return images.map { transcribeResponse }
    }

    override suspend fun validateConfiguration(): Result<Unit, AIError> {
        callsRecorded.add("validateConfiguration")
        return validateResponse
    }
}
