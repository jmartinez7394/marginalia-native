package com.marginalia.ai

import com.marginalia.model.Result
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AIProviderTest {

    @Test
    fun `FakeAIProvider records complete call`() = runTest {
        val provider = FakeAIProvider()
        provider.complete("hello")
        assertEquals(listOf("complete"), provider.callsRecorded)
    }

    @Test
    fun `FakeAIProvider returns configured complete response`() = runTest {
        val provider = FakeAIProvider()
        provider.completeResponse = Result.Success("custom response")
        val result = provider.complete("hello")
        assertTrue(result is Result.Success)
        assertEquals("custom response", result.value)
    }

    @Test
    fun `FakeAIProvider records transcribe call`() = runTest {
        val provider = FakeAIProvider()
        provider.transcribe(ByteArray(10), "describe this")
        assertEquals(listOf("transcribe"), provider.callsRecorded)
    }

    @Test
    fun `FakeAIProvider records transcribeBatch call with image count`() = runTest {
        val provider = FakeAIProvider()
        provider.transcribeBatch(listOf(ByteArray(10), ByteArray(10)), "describe")
        assertEquals(listOf("transcribeBatch:2"), provider.callsRecorded)
    }

    @Test
    fun `FakeAIProvider returns transcribe response for each batch image`() = runTest {
        val provider = FakeAIProvider()
        provider.transcribeResponse = Result.Success("batch result")
        val results = provider.transcribeBatch(listOf(ByteArray(5), ByteArray(5)), "describe")
        assertEquals(2, results.size)
        results.forEach { result ->
            assertTrue(result is Result.Success)
            assertEquals("batch result", result.value)
        }
    }

    @Test
    fun `FakeAIProvider returns configured failure response`() = runTest {
        val provider = FakeAIProvider()
        provider.completeResponse = Result.Failure(AIError.NetworkError("no connection"))
        val result = provider.complete("hello")
        assertTrue(result is Result.Failure)
        val error = result.error
        assertTrue(error is AIError.NetworkError)
        assertEquals("no connection", error.message)
    }

    @Test
    fun `AIError RateLimitError carries retryAfterSeconds`() {
        val error = AIError.RateLimitError("too many requests", retryAfterSeconds = 30)
        assertEquals(30, error.retryAfterSeconds)
    }

    @Test
    fun `AIError RateLimitError retryAfterSeconds can be null`() {
        val error = AIError.RateLimitError("rate limited", retryAfterSeconds = null)
        assertNull(error.retryAfterSeconds)
    }

    @Test
    fun `AIError sealed variants are distinct`() {
        val network: AIError = AIError.NetworkError("err")
        val auth: AIError = AIError.AuthenticationError("err")
        val rateLimit: AIError = AIError.RateLimitError("err", null)
        val notFound: AIError = AIError.ModelNotFoundError("model-x")
        val noVision: AIError = AIError.VisionNotSupported("fake")
        val filter: AIError = AIError.ContentFilterError("blocked")
        val unknown: AIError = AIError.UnknownError("err", null)

        assertTrue(network is AIError.NetworkError)
        assertTrue(auth is AIError.AuthenticationError)
        assertTrue(rateLimit is AIError.RateLimitError)
        assertTrue(notFound is AIError.ModelNotFoundError)
        assertTrue(noVision is AIError.VisionNotSupported)
        assertTrue(filter is AIError.ContentFilterError)
        assertTrue(unknown is AIError.UnknownError)
    }
}

class KeyRepositoryTest {

    @Test
    fun `FakeKeyRepository stores and retrieves a key`() = runTest {
        val repo = FakeKeyRepository()
        repo.storeKey("anthropic", "sk-test-1234")
        assertEquals("sk-test-1234", repo.getKey("anthropic"))
    }

    @Test
    fun `FakeKeyRepository hasKey returns true after storing`() = runTest {
        val repo = FakeKeyRepository()
        assertFalse(repo.hasKey("openai"))
        repo.storeKey("openai", "key")
        assertTrue(repo.hasKey("openai"))
    }

    @Test
    fun `FakeKeyRepository deleteKey removes the key`() = runTest {
        val repo = FakeKeyRepository()
        repo.storeKey("gemini", "key")
        repo.deleteKey("gemini")
        assertFalse(repo.hasKey("gemini"))
        assertNull(repo.getKey("gemini"))
    }

    @Test
    fun `FakeKeyRepository returns null for missing key`() = runTest {
        val repo = FakeKeyRepository()
        assertNull(repo.getKey("ollama"))
    }

    @Test
    fun `FakeKeyRepository stores multiple providers independently`() = runTest {
        val repo = FakeKeyRepository()
        repo.storeKey("anthropic", "key-a")
        repo.storeKey("openai", "key-b")
        assertEquals("key-a", repo.getKey("anthropic"))
        assertEquals("key-b", repo.getKey("openai"))
    }
}
