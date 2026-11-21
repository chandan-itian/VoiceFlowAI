package com.voiceflow.ai.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.voiceflow.ai.domain.repository.SecureStorageRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecureStorageImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : SecureStorageRepository {

    private val prefs: SharedPreferences by lazy {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            "voiceflow_secure_prefs",
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private fun keyFor(providerId: String): String = "api_key_$providerId"

    override suspend fun saveApiKey(providerId: String, key: String) {
        withContext(Dispatchers.IO) {
            prefs.edit().putString(keyFor(providerId), key).apply()
        }
    }

    override suspend fun getApiKey(providerId: String): String? {
        return withContext(Dispatchers.IO) {
            prefs.getString(keyFor(providerId), null)
        }
    }

    override suspend fun deleteApiKey(providerId: String) {
        withContext(Dispatchers.IO) {
            prefs.edit().remove(keyFor(providerId)).apply()
        }
    }
}
