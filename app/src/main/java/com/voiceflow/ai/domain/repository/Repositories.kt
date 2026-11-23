package com.voiceflow.ai.domain.repository

import com.voiceflow.ai.domain.model.*
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun updateSettings(update: (AppSettings) -> AppSettings)
    suspend fun getSettings(): AppSettings
}

interface WhisperRepository {
    suspend fun loadModel(modelPath: String, threads: Int = 4): Boolean
    suspend fun ensureModelLoaded(): Boolean
    suspend fun transcribe(audioPath: String, language: String = "", initialPrompt: String = ""): TranscriptionResult
    fun isModelLoaded(): Boolean
    fun unloadModel()
    fun getAvailableModels(): List<WhisperModel>
    fun isModelDownloaded(model: WhisperModel): Boolean
    fun getModelPath(model: WhisperModel): String
    suspend fun downloadModel(model: WhisperModel, onProgress: (Float) -> Unit): Boolean
    suspend fun deleteModel(model: WhisperModel): Boolean
    suspend fun verifyModelIntegrity(model: WhisperModel): Boolean
}

interface AudioRepository {
    suspend fun startRecording(): Boolean
    suspend fun stopRecording(): RecordedAudio?
    fun isRecording(): Boolean
    fun cancelRecording()
    val amplitudeFlow: Flow<Float>
}

interface AiProviderRepository {
    fun getProviders(): Flow<List<AiProvider>>
    suspend fun addProvider(provider: AiProvider)
    suspend fun updateProvider(provider: AiProvider)
    suspend fun deleteProvider(id: String)
    suspend fun getProvider(id: String): AiProvider?
    suspend fun getDefaultProvider(): AiProvider?
    suspend fun setDefaultProvider(id: String)
    suspend fun testConnection(provider: AiProvider): Result<String>
    suspend fun fetchModels(provider: AiProvider): Result<List<String>>
    suspend fun transcribeOnline(audioPath: String, provider: AiProvider): TranscriptionResult
    suspend fun refineText(text: String, provider: AiProvider, settings: AppSettings): String
}

interface TextInsertionRepository {
    suspend fun insertText(text: String, fieldInfo: FieldInfo): Boolean
    suspend fun copyToClipboard(text: String)
    fun getCurrentFieldInfo(): FieldInfo?
    fun isFieldAvailable(): Boolean
}

interface SecureStorageRepository {
    suspend fun saveApiKey(providerId: String, key: String)
    suspend fun getApiKey(providerId: String): String?
    suspend fun deleteApiKey(providerId: String)
}
