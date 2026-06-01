package com.marginalia.android.platform.ai

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.marginalia.ai.KeyRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidKeyRepository @Inject constructor(
    @ApplicationContext private val context: Context
) : KeyRepository {

    private val prefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "marginalia_ai_keys",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    override suspend fun storeKey(providerId: String, key: String) = withContext(Dispatchers.IO) {
        prefs.edit().putString(providerId, key).apply()
    }

    override suspend fun getKey(providerId: String): String? = withContext(Dispatchers.IO) {
        prefs.getString(providerId, null)
    }

    override suspend fun deleteKey(providerId: String) = withContext(Dispatchers.IO) {
        prefs.edit().remove(providerId).apply()
    }

    override suspend fun hasKey(providerId: String): Boolean = withContext(Dispatchers.IO) {
        prefs.contains(providerId)
    }
}
