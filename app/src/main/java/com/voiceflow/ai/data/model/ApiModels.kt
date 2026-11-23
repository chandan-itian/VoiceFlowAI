package com.voiceflow.ai.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ─── Gemini ──────────────────────────────────────────────────────────────────

@Serializable
data class GeminiRequest(
    val contents: List<GeminiContent>,
    @SerialName("generationConfig") val generationConfig: GeminiGenerationConfig? = null
)

@Serializable
data class GeminiContent(
    val parts: List<GeminiPart>,
    val role: String = "user"
)

@Serializable
data class GeminiPart(
    val text: String? = null,
    @SerialName("inlineData") val inlineData: GeminiInlineData? = null
)

@Serializable
data class GeminiInlineData(
    @SerialName("mimeType") val mimeType: String,
    val data: String // base64-encoded
)

@Serializable
data class GeminiGenerationConfig(
    val temperature: Float = 0.2f,
    @SerialName("maxOutputTokens") val maxOutputTokens: Int = 4096
)

@Serializable
data class GeminiResponse(
    val candidates: List<GeminiCandidate>? = null,
    val error: GeminiError? = null
)

@Serializable
data class GeminiCandidate(
    val content: GeminiContent? = null
)

@Serializable
data class GeminiError(
    val code: Int = 0,
    val message: String = ""
)

@Serializable
data class GeminiModelsResponse(
    val models: List<GeminiModelInfo> = emptyList()
)

@Serializable
data class GeminiModelInfo(
    val name: String = "",
    val displayName: String = "",
    val description: String = ""
)

// ─── OpenAI / Groq (compatible) ─────────────────────────────────────────────

@Serializable
data class OpenAiTranscriptionResponse(
    val text: String = ""
)

@Serializable
data class OpenAiChatRequest(
    val model: String,
    val messages: List<OpenAiMessage>,
    val temperature: Float = 0.3f,
    @SerialName("max_tokens") val maxTokens: Int = 2048
)

@Serializable
data class OpenAiMessage(
    val role: String,
    val content: String
)

@Serializable
data class OpenAiChatResponse(
    val choices: List<OpenAiChoice> = emptyList(),
    val error: OpenAiErrorWrapper? = null
)

@Serializable
data class OpenAiChoice(
    val message: OpenAiMessage? = null,
    @SerialName("finish_reason") val finishReason: String? = null
)

@Serializable
data class OpenAiErrorWrapper(
    val message: String = "",
    val type: String = "",
    val code: String? = null
)

@Serializable
data class OpenAiModelsResponse(
    val data: List<OpenAiModelInfo> = emptyList()
)

@Serializable
data class OpenAiModelInfo(
    val id: String = "",
    @SerialName("owned_by") val ownedBy: String = ""
)

// ─── OpenRouter ─────────────────────────────────────────────────────────────

@Serializable
data class OpenRouterModelsResponse(
    val data: List<OpenRouterModelInfo> = emptyList()
)

@Serializable
data class OpenRouterModelInfo(
    val id: String = "",
    val name: String = "",
    val pricing: OpenRouterPricing? = null
)

@Serializable
data class OpenRouterPricing(
    val prompt: String = "0",
    val completion: String = "0"
)

// ─── Provider persistence ───────────────────────────────────────────────────

@Serializable
data class ProviderListWrapper(
    val providers: List<com.voiceflow.ai.domain.model.AiProvider> = emptyList()
)
