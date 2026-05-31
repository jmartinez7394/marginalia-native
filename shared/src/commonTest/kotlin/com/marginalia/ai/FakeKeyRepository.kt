package com.marginalia.ai

class FakeKeyRepository : KeyRepository {
    private val keys = mutableMapOf<String, String>()

    override suspend fun storeKey(providerId: String, key: String) {
        keys[providerId] = key
    }

    override suspend fun getKey(providerId: String): String? = keys[providerId]

    override suspend fun deleteKey(providerId: String) {
        keys.remove(providerId)
    }

    override suspend fun hasKey(providerId: String): Boolean = keys.containsKey(providerId)
}
