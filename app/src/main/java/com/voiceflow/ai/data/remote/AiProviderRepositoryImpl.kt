package com.voiceflow.ai.data.remote

import android.content.Context
import android.util.Log
import com.voiceflow.ai.data.model.ProviderListWrapper
import com.voiceflow.ai.data.remote.providers.GeminiProvider
import com.voiceflow.ai.data.remote.providers.GroqProvider
import com.voiceflow.ai.data.remote.providers.OpenAIProvider
import com.voiceflow.ai.data.remote.providers.OpenRouterProvider
import com.voiceflow.ai.domain.model.AiProvider
import com.voiceflow.ai.domain.model.AppSettings
import com.voiceflow.ai.domain.model.ProviderType
import com.voiceflow.ai.domain.model.TranscriptionResult
import com.voiceflow.ai.domain.repository.AiProviderRepository
import com.voiceflow.ai.domain.repository.SecureStorageRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiProviderRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val geminiProvider: GeminiProvider,
    private val openAIProvider: OpenAIProvider,
    private val groqProvider: GroqProvider,
    private val openRouterProvider: OpenRouterProvider,
    private val secureStorage: SecureStorageRepository,
    private val json: Json
) : AiProviderRepository {

    companion object {
        private const val TAG = "AiProviderRepo"
        private const val PROVIDERS_FILE = "providers.json"
    }

    private val providersFile = File(context.filesDir, PROVIDERS_FILE)
    private val _providersFlow = MutableStateFlow<List<AiProvider>>(emptyList())

    init {
        loadProviders()
    }

    private fun loadProviders() {
        try {
            val list = if (providersFile.exists()) {
                val content = providersFile.readText()
                if (content.isNotBlank()) {
                    json.decodeFromString(ProviderListWrapper.serializer(), content).providers
                } else {
                    getDefaultInitialProviders()
                }
            } else {
                val defaults = getDefaultInitialProviders()
                saveProvidersToDisk(defaults)
                defaults
            }
            _providersFlow.value = list
        } catch (e: Exception) {
            Log.e(TAG, "Error loading providers", e)
            _providersFlow.value = getDefaultInitialProviders()
        }
    }

    private fun getDefaultInitialProviders(): List<AiProvider> {
        // Fresh installs default to Groq Fast Whisper: it is this app's
        // primary online transcription provider. Previously Gemini was the
        // default, so ONLINE mode silently used Gemini even after the user
        // added a Groq key. Existing installs keep the default saved in
        // providers.json — change it in Provider Manager if needed.
        return listOf(
            AiProvider(
                id = "gemini_default",
                name = "Google Gemini",
                type = ProviderType.GEMINI,
                baseUrl = "https://generativelanguage.googleapis.com",
                modelId = "gemini-1.5-flash",
                isDefault = false,
                supportsAudioInput = true,
                supportsTextRefinement = true
            ),
            AiProvider(
                id = "groq_default",
                name = "Groq Fast Whisper",
                type = ProviderType.GROQ,
                baseUrl = "https://api.groq.com/openai/v1",
                modelId = "whisper-large-v3",
                isDefault = true,
                supportsAudioInput = true,
                supportsTextRefinement = true
            ),
            AiProvider(
                id = "openai_default",
                name = "OpenAI",
                type = ProviderType.OPENAI,
                baseUrl = "https://api.openai.com/v1",
                modelId = "whisper-1",
                isDefault = false,
                supportsAudioInput = true,
                supportsTextRefinement = true
            ),
            AiProvider(
                id = "openrouter_default",
                name = "OpenRouter",
                type = ProviderType.OPENROUTER,
                baseUrl = "https://openrouter.ai/api/v1",
                modelId = "meta-llama/llama-3.3-70b-instruct",
                isDefault = false,
                supportsAudioInput = false,
                supportsTextRefinement = true
            )
        )
    }

    private fun saveProvidersToDisk(list: List<AiProvider>) {
        try {
            // Strip out API keys before saving to plain file
            val stripped = list.map { it.copy(apiKey = "") }
            val wrapper = ProviderListWrapper(stripped)
            providersFile.writeText(json.encodeToString(ProviderListWrapper.serializer(), wrapper))
        } catch (e: Exception) {
            Log.e(TAG, "Error saving providers to disk", e)
        }
    }

    override fun getProviders(): Flow<List<AiProvider>> = _providersFlow.asStateFlow()

    override suspend fun addProvider(provider: AiProvider) = withContext(Dispatchers.IO) {
        if (provider.apiKey.isNotBlank()) {
            secureStorage.saveApiKey(provider.id, provider.apiKey)
        }
        val current = _providersFlow.value.toMutableList()
        val index = current.indexOfFirst { it.id == provider.id }
        if (index >= 0) {
            current[index] = provider
        } else {
            current.add(provider)
        }
        saveProvidersToDisk(current)
        _providersFlow.value = current
    }

    override suspend fun updateProvider(provider: AiProvider) = withContext(Dispatchers.IO) {
        if (provider.apiKey.isNotBlank()) {
            secureStorage.saveApiKey(provider.id, provider.apiKey)
        }
        val current = _providersFlow.value.toMutableList()
        val index = current.indexOfFirst { it.id == provider.id }
        if (index >= 0) {
            current[index] = provider
            saveProvidersToDisk(current)
            _providersFlow.value = current
        }
    }

    override suspend fun deleteProvider(id: String) = withContext(Dispatchers.IO) {
        secureStorage.deleteApiKey(id)
        val current = _providersFlow.value.filter { it.id != id }
        saveProvidersToDisk(current)
        _providersFlow.value = current
    }

    override suspend fun getProvider(id: String): AiProvider? = withContext(Dispatchers.IO) {
        val provider = _providersFlow.value.find { it.id == id } ?: return@withContext null
        val storedKey = secureStorage.getApiKey(id) ?: ""
        provider.copy(apiKey = storedKey)
    }

    override suspend fun getDefaultProvider(): AiProvider? = withContext(Dispatchers.IO) {
        val providers = _providersFlow.value
        if (providers.isEmpty()) return@withContext null

        // Attach stored keys up front so selection can take them into account.
        val withKeys = providers.map { provider ->
            provider.copy(apiKey = secureStorage.getApiKey(provider.id) ?: "")
        }
        val markedDefault = withKeys.find { it.isDefault }

        // Priority:
        // 1. The marked default, if it has a usable API key.
        // 2. Any other provider that has a usable API key — previously the
        //    marked default was returned even with a blank key, so adding a
        //    Groq key (without also tapping "Set Default") still left ONLINE
        //    mode calling Gemini with no key and failing.
        // 3. The marked default / first provider, key or not, so callers can
        //    surface a clear "API key is required" error.
        markedDefault?.takeIf { it.apiKey.isNotBlank() }?.let { return@withContext it }
        withKeys.find { it.apiKey.isNotBlank() }?.let { return@withContext it }
        markedDefault ?: withKeys.first()
    }

    override suspend fun setDefaultProvider(id: String) = withContext(Dispatchers.IO) {
        val updated = _providersFlow.value.map {
            it.copy(isDefault = (it.id == id))
        }
        saveProvidersToDisk(updated)
        _providersFlow.value = updated
    }

    private suspend fun attachApiKey(provider: AiProvider): AiProvider {
        return if (provider.apiKey.isBlank()) {
            val key = secureStorage.getApiKey(provider.id) ?: ""
            provider.copy(apiKey = key)
        } else {
            provider
        }
    }

    override suspend fun testConnection(provider: AiProvider): Result<String> {
        val p = attachApiKey(provider)
        return when (p.type) {
            ProviderType.GEMINI -> geminiProvider.testConnection(p)
            ProviderType.GROQ -> groqProvider.testConnection(p)
            ProviderType.OPENAI, ProviderType.CUSTOM_OPENAI -> openAIProvider.testConnection(p)
            ProviderType.OPENROUTER -> openRouterProvider.testConnection(p)
            ProviderType.LOCAL_WHISPER -> Result.success("Local Whisper is ready")
        }
    }

    override suspend fun fetchModels(provider: AiProvider): Result<List<String>> {
        val p = attachApiKey(provider)
        return when (p.type) {
            ProviderType.GEMINI -> geminiProvider.fetchModels(p)
            ProviderType.GROQ -> groqProvider.fetchModels(p)
            ProviderType.OPENAI, ProviderType.CUSTOM_OPENAI -> openAIProvider.fetchModels(p)
            ProviderType.OPENROUTER -> openRouterProvider.fetchModels(p)
            ProviderType.LOCAL_WHISPER -> Result.success(listOf("ggml-tiny.bin", "ggml-base.bin", "ggml-small.bin"))
        }
    }

    override suspend fun transcribeOnline(audioPath: String, provider: AiProvider): TranscriptionResult {
        val p = attachApiKey(provider)
        return when (p.type) {
            ProviderType.GEMINI -> geminiProvider.transcribeAudio(audioPath, p)
            ProviderType.GROQ -> groqProvider.transcribeAudio(audioPath, p)
            ProviderType.OPENAI, ProviderType.CUSTOM_OPENAI -> openAIProvider.transcribeAudio(audioPath, p)
            ProviderType.OPENROUTER -> {
                throw UnsupportedOperationException("OpenRouter does not support direct audio transcription")
            }
            ProviderType.LOCAL_WHISPER -> {
                throw UnsupportedOperationException("Use WhisperRepository for local Whisper")
            }
        }
    }

    override suspend fun refineText(text: String, provider: AiProvider, settings: AppSettings): String {
        val p = attachApiKey(provider)
        return when (p.type) {
            ProviderType.GEMINI -> geminiProvider.refineText(text, p, settings.languageMode)
            ProviderType.GROQ -> groqProvider.refineText(text, p, settings)
            ProviderType.OPENAI, ProviderType.CUSTOM_OPENAI -> openAIProvider.refineText(text, p, settings.languageMode)
            ProviderType.OPENROUTER -> openRouterProvider.refineText(text, p, settings.languageMode)
            ProviderType.LOCAL_WHISPER -> text
        }
    }
}
