package com.voiceflow.ai.data.remote.providers

import android.util.Base64
import android.util.Log
import com.voiceflow.ai.data.model.*
import com.voiceflow.ai.domain.model.AiProvider
import com.voiceflow.ai.domain.model.LanguageMode
import com.voiceflow.ai.domain.model.TranscriptionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeminiProvider @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json
) {
    companion object {
        private const val TAG = "GeminiProvider"
        private const val DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com"
        private const val DEFAULT_MODEL = "gemini-1.5-flash"
    }

    suspend fun transcribeAudio(
        audioPath: String,
        provider: AiProvider
    ): TranscriptionResult = withContext(Dispatchers.IO) {
        val audioFile = File(audioPath)
        require(audioFile.exists()) { "Audio file does not exist: $audioPath" }

        val apiKey = provider.apiKey
        require(apiKey.isNotBlank()) { "Gemini API key is required" }

        val baseUrl = provider.baseUrl.ifBlank { DEFAULT_BASE_URL }.trimEnd('/')
        val model = provider.modelId.ifBlank { DEFAULT_MODEL }

        val audioBytes = audioFile.readBytes()
        val base64Audio = Base64.encodeToString(audioBytes, Base64.NO_WRAP)

        val promptText = "Accurately transcribe the following audio recording verbatim. " +
                "Output ONLY the transcribed text. Do not add any commentary, prefixes, markdown formatting, or timestamps. " +
                "Support Hindi, English, and Hinglish faithfully as spoken."

        val requestPayload = GeminiRequest(
            contents = listOf(
                GeminiContent(
                    parts = listOf(
                        GeminiPart(text = promptText),
                        GeminiPart(
                            inlineData = GeminiInlineData(
                                mimeType = "audio/wav",
                                data = base64Audio
                            )
                        )
                    )
                )
            ),
            generationConfig = GeminiGenerationConfig(temperature = 0.1f)
        )

        val body = json.encodeToString(GeminiRequest.serializer(), requestPayload)
            .toRequestBody("application/json".toMediaType())

        val url = "$baseUrl/v1beta/models/$model:generateContent?key=$apiKey"
        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        val startTime = System.currentTimeMillis()
        val response = okHttpClient.newCall(request).execute()
        val duration = System.currentTimeMillis() - startTime

        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: ""
            Log.e(TAG, "Gemini transcription failed [${response.code}]: $errorBody")
            throw IllegalStateException("Gemini error (${response.code}): $errorBody")
        }

        val responseBody = response.body?.string() ?: ""
        val parsed = json.decodeFromString(GeminiResponse.serializer(), responseBody)

        val transcribedText = parsed.candidates?.firstOrNull()
            ?.content?.parts?.firstOrNull()?.text?.trim()
            ?: throw IllegalStateException("Empty transcription returned by Gemini")

        TranscriptionResult(
            text = transcribedText,
            rawText = transcribedText,
            durationMs = duration,
            provider = provider.name.ifBlank { "Gemini" },
            isOffline = false
        )
    }

    suspend fun refineText(
        text: String,
        provider: AiProvider,
        languageMode: LanguageMode
    ): String = withContext(Dispatchers.IO) {
        val apiKey = provider.apiKey
        require(apiKey.isNotBlank()) { "Gemini API key is required" }

        val baseUrl = provider.baseUrl.ifBlank { DEFAULT_BASE_URL }.trimEnd('/')
        val model = provider.modelId.ifBlank { DEFAULT_MODEL }

        val langInstruction = when (languageMode) {
            LanguageMode.HINDI -> "Ensure output is written in proper Hindi Devanagari script."
            LanguageMode.ENGLISH -> "Ensure output is written in clear English."
            LanguageMode.HINGLISH -> "Preserve Hinglish (Romanized Hindi mixed with English terms as commonly typed, e.g. 'Bhai meri website ka dashboard update kar do')."
            LanguageMode.ENGLISH_TRANSLATION -> "Translate the text into natural, fluent English."
            LanguageMode.AUTO -> "Preserve the original language mixture accurately."
        }

        val prompt = "You are a professional voice dictation post-processor. " +
                "Refine the following transcribed speech for proper capitalization, accurate punctuation, and flow, while strictly preserving the speaker's original meaning and technical terms. " +
                "$langInstruction " +
                "Return ONLY the refined text with no commentary or quotes.\n\nInput:\n$text"

        val requestPayload = GeminiRequest(
            contents = listOf(
                GeminiContent(parts = listOf(GeminiPart(text = prompt)))
            ),
            generationConfig = GeminiGenerationConfig(temperature = 0.2f)
        )

        val body = json.encodeToString(GeminiRequest.serializer(), requestPayload)
            .toRequestBody("application/json".toMediaType())

        val url = "$baseUrl/v1beta/models/$model:generateContent?key=$apiKey"
        val request = Request.Builder().url(url).post(body).build()

        val response = okHttpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            val err = response.body?.string() ?: ""
            throw IllegalStateException("Gemini refinement failed (${response.code}): $err")
        }

        val responseBody = response.body?.string() ?: ""
        val parsed = json.decodeFromString(GeminiResponse.serializer(), responseBody)

        parsed.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim() ?: text
    }

    suspend fun testConnection(provider: AiProvider): Result<String> = withContext(Dispatchers.IO) {
        try {
            val apiKey = provider.apiKey
            if (apiKey.isBlank()) return@withContext Result.failure(IllegalArgumentException("API key is blank"))
            val baseUrl = provider.baseUrl.ifBlank { DEFAULT_BASE_URL }.trimEnd('/')
            val url = "$baseUrl/v1beta/models?key=$apiKey"

            val request = Request.Builder().url(url).get().build()
            val response = okHttpClient.newCall(request).execute()

            if (response.isSuccessful) {
                Result.success("Connection successful! Gemini API is responsive.")
            } else {
                Result.failure(Exception("Gemini test failed with HTTP ${response.code}: ${response.message}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchModels(provider: AiProvider): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val apiKey = provider.apiKey
            val baseUrl = provider.baseUrl.ifBlank { DEFAULT_BASE_URL }.trimEnd('/')
            val url = "$baseUrl/v1beta/models?key=$apiKey"

            val request = Request.Builder().url(url).get().build()
            val response = okHttpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
            }

            val responseBody = response.body?.string() ?: ""
            val parsed = json.decodeFromString(GeminiModelsResponse.serializer(), responseBody)
            val modelNames = parsed.models.map { it.name.removePrefix("models/") }
            Result.success(modelNames)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
