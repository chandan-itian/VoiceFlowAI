package com.voiceflow.ai.data.remote.providers

import com.voiceflow.ai.domain.model.AiProvider
import com.voiceflow.ai.domain.model.LanguageMode
import com.voiceflow.ai.domain.model.TranscriptionResult
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GroqProvider @Inject constructor(
    private val openAIProvider: OpenAIProvider
) {
    companion object {
        const val GROQ_BASE_URL = "https://api.groq.com/openai/v1"
        const val DEFAULT_WHISPER_MODEL = "whisper-large-v3"
        const val DEFAULT_REFINEMENT_MODEL = "llama-3.3-70b-versatile"
    }

    private fun normalizeProvider(provider: AiProvider): AiProvider {
        return provider.copy(
            baseUrl = provider.baseUrl.ifBlank { GROQ_BASE_URL },
            modelId = provider.modelId.ifBlank { DEFAULT_WHISPER_MODEL }
        )
    }

    suspend fun transcribeAudio(
        audioPath: String,
        provider: AiProvider
    ): TranscriptionResult {
        val groqProvider = normalizeProvider(provider)
        val result = openAIProvider.transcribeAudio(audioPath, groqProvider)
        return result.copy(provider = provider.name.ifBlank { "Groq (Whisper)" })
    }

    suspend fun refineText(
        text: String,
        provider: AiProvider,
        languageMode: LanguageMode
    ): String {
        val groqProvider = provider.copy(
            baseUrl = provider.baseUrl.ifBlank { GROQ_BASE_URL },
            modelId = if (provider.modelId.isBlank() || provider.modelId.contains("whisper")) {
                DEFAULT_REFINEMENT_MODEL
            } else {
                provider.modelId
            }
        )
        return openAIProvider.refineText(text, groqProvider, languageMode)
    }

    suspend fun testConnection(provider: AiProvider): Result<String> {
        return openAIProvider.testConnection(normalizeProvider(provider))
    }

    suspend fun fetchModels(provider: AiProvider): Result<List<String>> {
        return openAIProvider.fetchModels(normalizeProvider(provider))
    }
}
