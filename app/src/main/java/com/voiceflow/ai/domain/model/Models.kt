package com.voiceflow.ai.domain.model

import kotlinx.serialization.Serializable

enum class AiMode {
    OFFLINE, ONLINE, SMART_HYBRID
}

enum class LanguageMode {
    HINDI, ENGLISH, HINGLISH, AUTO, ENGLISH_TRANSLATION
}

enum class TranscriptionState {
    IDLE, RECORDING, PROCESSING, SUCCESS, ERROR
}

@Serializable
data class WhisperModel(
    val id: String,
    val name: String,
    val fileName: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val isMultilingual: Boolean = true,
    val description: String = ""
) {
    companion object {
        val TINY = WhisperModel(
            id = "tiny",
            name = "Tiny (Fast)",
            fileName = "ggml-tiny.bin",
            downloadUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.bin",
            sizeBytes = 75_000_000L,
            description = "Fastest, lowest accuracy. ~75 MB"
        )
        val BASE = WhisperModel(
            id = "base",
            name = "Base (Default)",
            fileName = "ggml-base.bin",
            downloadUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.bin",
            sizeBytes = 142_000_000L,
            description = "Good balance of speed and accuracy. ~142 MB"
        )
        val BASE_Q5_1 = WhisperModel(
            id = "base_q5_1",
            name = "Base Quantized Q5_1 (Fast)",
            fileName = "ggml-base-q5_1.bin",
            downloadUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base-q5_1.bin",
            sizeBytes = 57_000_000L,
            description = "Lightweight 5-bit model. 2.5x faster on Snapdragon. ~57 MB"
        )
        val HINGLISH_Q5_1 = WhisperModel(
            id = "hinglish_q5_1",
            name = "Hinglish Quantized Q5_1 (Best for Hinglish)",
            fileName = "ggml-hinglish-swift-q5_1.bin",
            downloadUrl = "https://huggingface.co/rohitag13/whisper-hindi2hinglish-swift-GGUF/resolve/main/ggml-hinglish-swift-q5_1.bin",
            sizeBytes = 59_707_625L,
            description = "Oriserve Hindi2Hinglish-Swift fine-tune of Whisper Base: Roman Hinglish output at Base speed. Use with Hinglish mode. ~57 MB"
        )
        val SMALL_Q5_1 = WhisperModel(
            id = "small_q5_1",
            name = "Small Quantized Q5_1 (Best Hindi/English)",
            fileName = "ggml-small-q5_1.bin",
            downloadUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small-q5_1.bin",
            sizeBytes = 182_000_000L,
            description = "4x higher accuracy for Hindi & English speech. ~182 MB"
        )
        val SMALL = WhisperModel(
            id = "small",
            name = "Small (Accurate)",
            fileName = "ggml-small.bin",
            downloadUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small.bin",
            sizeBytes = 466_000_000L,
            description = "Higher accuracy, slower. ~466 MB"
        )
        val ALL = listOf(BASE_Q5_1, HINGLISH_Q5_1, BASE, SMALL_Q5_1, TINY, SMALL)
    }
}

@Serializable
data class AiProvider(
    val id: String,
    val name: String,
    val type: ProviderType,
    val apiKey: String = "",
    val baseUrl: String = "",
    val modelId: String = "",
    val isDefault: Boolean = false,
    val supportsAudioInput: Boolean = false,
    val supportsTextRefinement: Boolean = true
)

@Serializable
enum class ProviderType {
    LOCAL_WHISPER, GEMINI, OPENROUTER, GROQ, OPENAI, CUSTOM_OPENAI
}

data class RecordedAudio(val path: String, val hasSpeech: Boolean)

data class TranscriptionResult(
    val text: String,
    val rawText: String = text,
    val language: String = "",
    val confidence: Float = 0f,
    val durationMs: Long = 0,
    val provider: String = "",
    val isOffline: Boolean = true
)

data class FieldInfo(
    val packageName: String = "",
    val className: String = "",
    val viewId: String = "",
    val isEditable: Boolean = false,
    val isSensitive: Boolean = false,
    val isPassword: Boolean = false,
    val currentText: String = "",
    val selectionStart: Int = -1,
    val selectionEnd: Int = -1
)

@Serializable
data class AppSettings(
    val aiMode: AiMode = AiMode.OFFLINE,
    val languageMode: LanguageMode = LanguageMode.HINGLISH,
    val selectedWhisperModel: String = "base",
    val defaultProviderId: String = "",
    val enablePunctuation: Boolean = true,
    val enableCapitalization: Boolean = true,
    val enableFillerRemoval: Boolean = true,
    val enableGrammarCorrection: Boolean = false,
    val enableRawTranscript: Boolean = false,
    val floatingMicSize: Int = 56,
    val cpuThreads: Int = 4,
    val isAppEnabled: Boolean = true
)
