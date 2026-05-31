package com.marginalia.ai

interface KeyRepository {
    suspend fun storeKey(providerId: String, key: String)
    suspend fun getKey(providerId: String): String?
    suspend fun deleteKey(providerId: String)
    suspend fun hasKey(providerId: String): Boolean
}
