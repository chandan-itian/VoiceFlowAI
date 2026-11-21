package com.voiceflow.ai.data.remote.providers

import com.voiceflow.ai.data.model.OpenRouterModelsResponse
import com.voiceflow.ai.domain.model.AiProvider
import com.voiceflow.ai.domain.model.LanguageMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OpenRouterProvider @Inject constructor(
    private val openAIProvider: OpenAIProvider,
    private val okHttpClient: OkHttpClient,
    private val json: Json
) {
    companion object {
        const val OPENROUTER_BASE_URL = "https://openrouter.ai/api/v1"
        const val DEFAULT_MODEL = "meta-llama/llama-3.3-70b-instruct"
    }

    private fun normalizeProvider(provider: AiProvider): AiProvider {
        return provider.copy(
            baseUrl = provider.baseUrl.ifBlank { OPENROUTER_BASE_URL },
            modelId = provider.modelId.ifBlank { DEFAULT_MODEL }
        )
    }

    suspend fun refineText(
        text: String,
        provider: AiProvider,
        languageMode: LanguageMode
    ): String {
        return openAIProvider.refineText(text, normalizeProvider(provider), languageMode)
    }

    suspend fun testConnection(provider: AiProvider): Result<String> {
        return openAIProvider.testConnection(normalizeProvider(provider))
    }

    suspend fun fetchModels(provider: AiProvider): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val url = "${provider.baseUrl.ifBlank { OPENROUTER_BASE_URL }.trimEnd('/')}/models"
            val requestBuilder = Request.Builder().url(url).get()
            if (provider.apiKey.isNotBlank()) {
                requestBuilder.header("Authorization", "Bearer ${provider.apiKey}")
            }

            val response = okHttpClient.newCall(requestBuilder.build()).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
            }

            val body = response.body?.string() ?: ""
            val parsed = json.decodeFromString(OpenRouterModelsResponse.serializer(), body)
            Result.success(parsed.data.map { it.id }.sorted())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
