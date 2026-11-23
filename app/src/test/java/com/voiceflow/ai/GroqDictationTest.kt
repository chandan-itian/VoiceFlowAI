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
        coEvery { providers.transcribeOnline("recording.wav", provider) } returns TranscriptionResult(raw)
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
            providers.transcribeOnline("recording.wav", provider)
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
