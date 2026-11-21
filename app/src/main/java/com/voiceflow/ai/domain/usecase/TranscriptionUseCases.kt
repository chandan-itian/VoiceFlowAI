package com.voiceflow.ai.domain.usecase

import com.voiceflow.ai.domain.model.*
import com.voiceflow.ai.domain.repository.*
import com.voiceflow.ai.utils.HinglishNormalizer
import javax.inject.Inject

class StartRecordingUseCase @Inject constructor(
    private val audioRepository: AudioRepository
) {
    suspend operator fun invoke(): Boolean = audioRepository.startRecording()
}

class StopRecordingAndTranscribeUseCase @Inject constructor(
    private val audioRepository: AudioRepository,
    private val whisperRepository: WhisperRepository,
    private val providerRepository: AiProviderRepository,
    private val settingsRepository: SettingsRepository,
    private val processTextUseCase: ProcessTextUseCase
) {
    suspend operator fun invoke(): Result<TranscriptionResult> {
        val audioPath = audioRepository.stopRecording()
            ?: return Result.failure(Exception("No audio recorded"))

        val settings = settingsRepository.getSettings()

        return try {
            val (defaultLangHint, defaultPromptHint) = when (settings.languageMode) {
                LanguageMode.HINDI -> "hi" to "नमस्ते, आप कैसे हैं, क्या हाल है, धन्यवाद, ठीक है, हाँ, नहीं, मैं, हम, आप, यह, वह, अच्छा, बहुत बढ़िया"
                LanguageMode.ENGLISH -> "en" to "Hello, thank you, please, could you, how are you, alright, sounds good, yes, no, what, where, when, why"
                LanguageMode.HINGLISH -> "hi" to "Hello, kya haal hai, kaise ho, theek hai, please, thanks, chalo chalte hain, kya kar rahe ho, main theek hoon, accha, done, alright"
                LanguageMode.ENGLISH_TRANSLATION -> "translate_to_en" to "Translate Indian speech to English: Hello, how are you, thank you, yes, alright"
                LanguageMode.AUTO -> "" to "Hello, नमस्ते, thank you, शुक्रिया, okay, ठीक है, please, धन्यवाद, क्या हाल है"
            }

            val result = when (settings.aiMode) {
                AiMode.OFFLINE -> {
                    val rawResult = whisperRepository.transcribe(audioPath, defaultLangHint, defaultPromptHint)
                    val processed = processTextUseCase(rawResult.text)
                    rawResult.copy(text = processed)
                }
                AiMode.ONLINE -> {
                    val provider = providerRepository.getDefaultProvider()
                        ?: return Result.failure(Exception("No default online AI provider configured. Please configure an API key in Provider Manager."))
                    val rawResult = try {
                        providerRepository.transcribeOnline(audioPath, provider)
                    } catch (e: UnsupportedOperationException) {
                        // For text-only LLM providers like OpenRouter, transcribe with Whisper first, then refine with the LLM
                        val localResult = whisperRepository.transcribe(audioPath, defaultLangHint, defaultPromptHint)
                        val refined = try {
                            providerRepository.refineText(localResult.text, provider, settings.languageMode)
                        } catch (refineError: Exception) {
                            localResult.text
                        }
                        localResult.copy(
                            text = refined,
                            provider = "${provider.name} (Whisper + ${provider.type.name})",
                            isOffline = false
                        )
                    }
                    val processed = processTextUseCase(rawResult.text)
                    rawResult.copy(text = processed)
                }
                AiMode.SMART_HYBRID -> {
                    // Step 1: Transcribe locally using fast Whisper with vocabulary biasing prompt
                    val offlineResult = whisperRepository.transcribe(audioPath, defaultLangHint, defaultPromptHint)
                    val provider = providerRepository.getDefaultProvider()

                    // Step 2: If online provider is configured and available, perform refinement
                    val textToUse = if (provider != null && provider.apiKey.isNotBlank() && provider.supportsTextRefinement) {
                        try {
                            providerRepository.refineText(
                                offlineResult.text,
                                provider,
                                settings.languageMode
                            )
                        } catch (e: Exception) {
                            // Fallback to local offline result
                            offlineResult.text
                        }
                    } else {
                        offlineResult.text
                    }
                    val processed = processTextUseCase(textToUse)
                    offlineResult.copy(text = processed)
                }
            }
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

class InsertTranscriptionUseCase @Inject constructor(
    private val textInsertionRepository: TextInsertionRepository
) {
    suspend operator fun invoke(text: String, fieldInfo: FieldInfo): Boolean {
        return if (textInsertionRepository.isFieldAvailable()) {
            val success = textInsertionRepository.insertText(text, fieldInfo)
            if (!success) {
                textInsertionRepository.copyToClipboard(text)
            }
            success
        } else {
            textInsertionRepository.copyToClipboard(text)
            false
        }
    }
}

class ProcessTextUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository
) {
    suspend operator fun invoke(rawText: String): String {
        val settings = settingsRepository.getSettings()
        return HinglishNormalizer.normalize(
            rawText = rawText,
            isHinglishMode = settings.languageMode == LanguageMode.HINGLISH,
            removeFillers = settings.enableFillerRemoval,
            fixPunctuation = settings.enablePunctuation,
            fixCapitalization = settings.enableCapitalization
        )
    }
}
