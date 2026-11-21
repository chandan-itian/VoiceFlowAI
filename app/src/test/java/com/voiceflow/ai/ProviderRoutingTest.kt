package com.voiceflow.ai

import com.voiceflow.ai.domain.model.*
import org.junit.Assert.assertEquals
import org.junit.Test

class ProviderRoutingTest {

    @Test
    fun testProviderTypes() {
        val providers = listOf(
            AiProvider("1", "Gemini", ProviderType.GEMINI, supportsAudioInput = true),
            AiProvider("2", "Groq", ProviderType.GROQ, supportsAudioInput = true),
            AiProvider("3", "OpenAI", ProviderType.OPENAI, supportsAudioInput = true),
            AiProvider("4", "OpenRouter", ProviderType.OPENROUTER, supportsAudioInput = false)
        )

        val audioSupported = providers.filter { it.supportsAudioInput }
        assertEquals(3, audioSupported.size)
    }

    @Test
    fun testWhisperDefaultModelConfig() {
        val defaultModel = WhisperModel.BASE
        assertEquals("ggml-base.bin", defaultModel.fileName)
        assertEquals(true, defaultModel.isMultilingual)
        assertEquals(142_000_000L, defaultModel.sizeBytes)
    }
}
