package com.voiceflow.ai

import com.voiceflow.ai.domain.model.*
import com.voiceflow.ai.domain.repository.*
import com.voiceflow.ai.domain.usecase.ProcessTextUseCase
import com.voiceflow.ai.domain.usecase.StopRecordingAndTranscribeUseCase
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class GroqDictationTest {
    private val audio = mockk<AudioRepository>()
    private val whisper = mockk<WhisperRepository>()
    private val providers = mockk<AiProviderRepository>()
    private val preferences = mockk<SettingsRepository>()
    private val settings = AppSettings(aiMode = AiMode.ONLINE, languageMode = LanguageMode.HINGLISH)
    private val provider = AiProvider("groq", "Groq", ProviderType.GROQ)
    private val raw = "meeting kal hai time bhejo"
    private val stop = StopRecordingAndTranscribeUseCase(audio, whisper, providers, preferences, ProcessTextUseCase(preferences))

    init {
        coEvery { preferences.getSettings() } returns settings
        coEvery { audio.stopRecording() } returns RecordedAudio("recording.wav", true)
        coEvery { providers.getDefaultProvider() } returns provider
        coEvery { providers.transcribeOnline("recording.wav", provider, settings.languageMode) } returns TranscriptionResult(raw)
    }

    @Test
    fun recoversHinglishWhenBothStagesProduceUrduScript() = runTest {
        val urdu = "میں بول رہا تھا کہ تم اردو میں کیوں لکھ رہے ہو"
        val hindi = "मैं बोल रहा था कि तुम उर्दू में क्यों लिख रहे हो"
        val roman = "Main bol raha tha ki tum Urdu mein kyun likh rahe ho?"
        coEvery { providers.transcribeOnline("recording.wav", provider, settings.languageMode) } returns TranscriptionResult(urdu)
        coEvery { providers.refineText(urdu, provider, settings) } returns urdu
        coEvery { providers.transcribeOnline("recording.wav", provider, LanguageMode.HINDI) } returns TranscriptionResult(hindi)
        coEvery { providers.refineText(hindi, provider, settings) } returns roman
        assertEquals(roman, stop().getOrThrow().text)
    }

    @Test
    fun preservesEnglishWhenFormatterChangesScript() = runTest {
        val english = "Please send the meeting link."
        coEvery { providers.transcribeOnline("recording.wav", provider, settings.languageMode) } returns TranscriptionResult(english)
        coEvery { providers.refineText(english, provider, settings) } returns "میٹنگ"
        assertEquals(english, stop().getOrThrow().text)
    }

    @Test
    fun silenceSkipsBothInferenceStages() = runTest {
        coEvery { audio.stopRecording() } returns RecordedAudio("recording.wav", false)
        assertEquals("", stop().getOrThrow().text)
        verify { providers wasNot Called }
        verify { whisper wasNot Called }
    }

    @Test
    fun formatsTranscribedSpeechAndPreservesParagraphs() = runTest {
        val formatted = "Meeting kal hai.\n\nTime bhejo."
        coEvery { providers.refineText(raw, provider, settings) } returns formatted
        val result = stop().getOrThrow()
        assertEquals(formatted, result.text)
        assertEquals(raw, result.rawText)
        coVerifyOrder {
            providers.transcribeOnline("recording.wav", provider, settings.languageMode)
            providers.refineText(raw, provider, settings)
        }
    }

    @Test
    fun keepsDictationWhenFormattingFails() = runTest {
        coEvery { providers.refineText(raw, provider, settings) } throws java.io.IOException()
        val result = stop().getOrThrow()
        assertEquals("Meeting kal hai time bhejo.", result.text)
        assertEquals(raw, result.rawText)
    }
}
