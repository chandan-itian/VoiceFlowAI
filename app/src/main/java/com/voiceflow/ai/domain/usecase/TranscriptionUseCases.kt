package com.voiceflow.ai.domain.usecase

import com.voiceflow.ai.domain.model.*
import com.voiceflow.ai.domain.repository.*
import com.voiceflow.ai.utils.HinglishNormalizer
import kotlinx.coroutines.CancellationException
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
        val recording = audioRepository.stopRecording()
            ?: return Result.failure(Exception("No audio recorded"))

        val audioPath = recording.path
        return try {
            if (!recording.hasSpeech) return Result.success(TranscriptionResult(""))
            val settings = settingsRepository.getSettings()
            val (defaultLangHint, defaultPromptHint) = when (settings.languageMode) {
                LanguageMode.HINDI -> "hi" to "नमस्ते, आप कैसे हैं, क्या हाल है, धन्यवाद, ठीक है, हाँ, नहीं, मैं, हम, आप, यह, वह, अच्छा, बहुत बढ़िया"
                LanguageMode.ENGLISH -> "en" to "Hello, thank you, please, could you, how are you, alright, sounds good, yes, no, what, where, when, why"
                // Bilingual audio may contain whole English clauses. Let Whisper detect the source.
                LanguageMode.HINGLISH -> "" to "Kal ki meeting mein kaunsa model use karein? Please share the update."
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
                        providerRepository.transcribeOnline(audioPath, provider, settings.languageMode)
                    } catch (e: UnsupportedOperationException) {
                        // For text-only LLM providers like OpenRouter, transcribe with Whisper first, then refine with the LLM
                        val localResult = whisperRepository.transcribe(audioPath, defaultLangHint, defaultPromptHint)
                        localResult.copy(
                            provider = "${provider.name} (Whisper + ${provider.type.name})",
                            isOffline = false
                        )
                    }
                    val outputSettings = if (provider.type == ProviderType.GROQ && settings.languageMode == LanguageMode.AUTO &&
                        rawResult.language.lowercase() in setOf("hi", "hindi", "ur", "urdu")) {
                        settings.copy(languageMode = LanguageMode.HINGLISH)
                    } else settings
                    val needsFormatter = provider.type == ProviderType.GROQ || provider.type == ProviderType.OPENROUTER
                    val text = if (needsFormatter) refineOrKeep(rawResult.text, provider, outputSettings) else rawResult.text
                    val output = if (provider.type == ProviderType.GROQ && needsScriptRepair(text, outputSettings.languageMode)) {
                        if (!needsScriptRepair(rawResult.text, outputSettings.languageMode)) rawResult.text
                        else {
                            // Re-decode the audio once with a Hindi hint if both stages returned the wrong script.
                            val retry = providerRepository.transcribeOnline(audioPath, provider, LanguageMode.HINDI)
                            refineOrKeep(retry.text, provider, outputSettings).let { refined ->
                                if (needsScriptRepair(refined, outputSettings.languageMode)) retry.text else refined
                            }
                        }
                    } else text
                    check(provider.type != ProviderType.GROQ || !needsScriptRepair(output, outputSettings.languageMode)) {
                        "Could not produce the selected writing script. Please dictate again."
                    }
                    rawResult.copy(text = processTextUseCase(output, outputSettings))
                }
                AiMode.SMART_HYBRID -> {
                    // Step 1: Transcribe locally using fast Whisper with vocabulary biasing prompt
                    val offlineResult = whisperRepository.transcribe(audioPath, defaultLangHint, defaultPromptHint)
                    val provider = providerRepository.getDefaultProvider()

                    // Step 2: If online provider is configured and available, perform refinement
                    val textToUse = if (provider != null && provider.apiKey.isNotBlank()) {
                        refineOrKeep(offlineResult.text, provider, settings)
                    } else {
                        offlineResult.text
                    }
                    val output = if (provider?.type == ProviderType.GROQ && needsScriptRepair(textToUse, settings.languageMode)) {
                        check(!needsScriptRepair(offlineResult.text, settings.languageMode)) {
                            "Could not produce the selected writing script. Please dictate again."
                        }
                        offlineResult.text
                    } else textToUse
                    val processed = processTextUseCase(output, settings)
                    offlineResult.copy(text = processed)
                }
            }
            Result.success(result)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            java.io.File(audioPath).delete()
        }
    }

    private fun needsScriptRepair(text: String, mode: LanguageMode): Boolean = when (mode) {
        // The local Hinglish normalizer can also recover Devanagari from Whisper.
        LanguageMode.HINGLISH -> text.codePoints().anyMatch { cp ->
            val script = Character.UnicodeScript.of(cp)
            Character.isLetter(cp) && script != Character.UnicodeScript.LATIN && script != Character.UnicodeScript.DEVANAGARI
        }
        LanguageMode.HINDI -> text.codePoints().anyMatch { cp ->
            Character.isLetter(cp) && Character.UnicodeScript.of(cp) == Character.UnicodeScript.ARABIC
        }
        else -> false
    }

    private suspend fun refineOrKeep(text: String, provider: AiProvider, settings: AppSettings): String {
        if (text.isBlank() || !provider.supportsTextRefinement) return text
        return try {
            providerRepository.refineText(text, provider, settings).ifBlank { text }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            text
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
    suspend operator fun invoke(rawText: String): String = invoke(rawText, settingsRepository.getSettings())

    operator fun invoke(rawText: String, settings: AppSettings): String {
        return HinglishNormalizer.normalize(
            rawText = rawText,
            isHinglishMode = settings.languageMode == LanguageMode.HINGLISH,
            removeFillers = settings.enableFillerRemoval,
            fixPunctuation = settings.enablePunctuation,
            fixCapitalization = settings.enableCapitalization
        )
    }
}
