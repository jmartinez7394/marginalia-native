package com.marginalia.android.platform.ai

import android.util.Base64
import android.util.Log
import com.marginalia.ai.AIError
import com.marginalia.ai.AIProvider
import com.marginalia.ai.KeyRepository
import com.marginalia.model.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AnthropicAIProvider @Inject constructor(
    private val keyRepository: KeyRepository
) : AIProvider {

    override val providerId = "anthropic"
    override val displayName = "Anthropic (Claude)"
    override val supportsVision = true
    override val requiresNetwork = true

    override suspend fun complete(
        prompt: String,
        systemPrompt: String?,
        maxTokens: Int
    ): Result<String, AIError> = withContext(Dispatchers.IO) {
        val apiKey = keyRepository.getKey(providerId)
            ?: return@withContext Result.Failure(AIError.AuthenticationError("No API key configured"))
        val body = JSONObject().apply {
            put("model", "claude-sonnet-4-6")
            put("max_tokens", maxTokens)
            systemPrompt?.let { put("system", it) }
            put("messages", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("content", prompt)
            }))
        }.toString()
        callApi(apiKey, body)
    }

    override suspend fun transcribe(
        imageBytes: ByteArray,
        prompt: String,
        systemPrompt: String?,
        maxTokens: Int
    ): Result<String, AIError> = withContext(Dispatchers.IO) {
        val apiKey = keyRepository.getKey(providerId)
            ?: return@withContext Result.Failure(AIError.AuthenticationError("No API key configured"))
        val base64 = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
        val content = JSONArray().apply {
            put(JSONObject().apply {
                put("type", "image")
                put("source", JSONObject().apply {
                    put("type", "base64")
                    put("media_type", "image/png")
                    put("data", base64)
                })
            })
            put(JSONObject().apply {
                put("type", "text")
                put("text", prompt)
            })
        }
        val body = JSONObject().apply {
            put("model", "claude-sonnet-4-6")
            put("max_tokens", maxTokens)
            systemPrompt?.let { put("system", it) }
            put("messages", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("content", content)
            }))
        }.toString()
        callApi(apiKey, body)
    }

    override suspend fun transcribeBatch(
        images: List<ByteArray>,
        prompt: String,
        systemPrompt: String?,
        maxTokens: Int
    ): List<Result<String, AIError>> = images.map { transcribe(it, prompt, systemPrompt, maxTokens) }

    override suspend fun validateConfiguration(): Result<Unit, AIError> = withContext(Dispatchers.IO) {
        val apiKey = keyRepository.getKey(providerId)
            ?: return@withContext Result.Failure(AIError.AuthenticationError("No API key configured"))
        when (val result = complete("Hello", maxTokens = 10)) {
            is Result.Success -> Result.Success(Unit)
            is Result.Failure -> Result.Failure(result.error)
        }
    }

    private fun callApi(apiKey: String, body: String): Result<String, AIError> {
        return try {
            val url = URL("https://api.anthropic.com/v1/messages")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("content-type", "application/json")
            conn.setRequestProperty("x-api-key", apiKey)
            conn.setRequestProperty("anthropic-version", "2023-06-01")
            conn.doOutput = true
            conn.connectTimeout = 30_000
            conn.readTimeout = 90_000

            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            val responseText = if (code == 200) {
                conn.inputStream.bufferedReader().readText()
            } else {
                conn.errorStream?.bufferedReader()?.readText() ?: ""
            }
            conn.disconnect()

            Log.d(TAG, "API response: $code")
            when (code) {
                200 -> {
                    val json = JSONObject(responseText)
                    val text = json.getJSONArray("content").getJSONObject(0).getString("text")
                    Result.Success(text)
                }
                401 -> Result.Failure(AIError.AuthenticationError("Invalid API key"))
                429 -> Result.Failure(AIError.RateLimitError("Rate limit exceeded", null))
                else -> Result.Failure(AIError.UnknownError("HTTP $code", null))
            }
        } catch (e: Exception) {
            Log.e(TAG, "API call failed: ${e.message}")
            Result.Failure(AIError.NetworkError(e.message ?: "Network error"))
        }
    }

    companion object {
        private const val TAG = "AnthropicAIProvider"
    }
}
