package com.voiceflow.ai

import com.voiceflow.ai.domain.model.AppSettings
import com.voiceflow.ai.domain.repository.SettingsRepository
import com.voiceflow.ai.domain.usecase.ProcessTextUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProcessTextUseCaseTest {

    private class FakeSettingsRepository(private val currentSettings: AppSettings) : SettingsRepository {
        override val settings: Flow<AppSettings> = flowOf(currentSettings)
        override suspend fun updateSettings(update: (AppSettings) -> AppSettings) {}
        override suspend fun getSettings(): AppSettings = currentSettings
    }

    @Test
    fun testFillerRemoval() = runTest {
        val repo = FakeSettingsRepository(
            AppSettings(
                enableFillerRemoval = true,
                enablePunctuation = false,
                enableCapitalization = false
            )
        )
        val useCase = ProcessTextUseCase(repo)
        val input = "umm I wanted to say uhh that this is great"
        val result = useCase(input)
        assertTrue(!result.contains("umm"))
        assertTrue(!result.contains("uhh"))
    }

    @Test
    fun testPunctuationAddition() = runTest {
        val repo = FakeSettingsRepository(
            AppSettings(
                enableFillerRemoval = false,
                enablePunctuation = true,
                enableCapitalization = false
            )
        )
        val useCase = ProcessTextUseCase(repo)
        val input = "Hello world"
        val result = useCase(input)
        assertTrue(result.endsWith("."))
    }

    @Test
    fun testSmartCapitalization() = runTest {
        val repo = FakeSettingsRepository(
            AppSettings(
                enableFillerRemoval = false,
                enablePunctuation = false,
                enableCapitalization = true
            )
        )
        val useCase = ProcessTextUseCase(repo)
        val input = "hello world"
        val result = useCase(input)
        assertEquals("Hello world", result)
    }

    @Test
    fun testTechnicalTermsPreservation() = runTest {
        val repo = FakeSettingsRepository(
            AppSettings(
                enableFillerRemoval = false,
                enablePunctuation = false,
                enableCapitalization = false
            )
        )
        val useCase = ProcessTextUseCase(repo)
        val input = "check the api and dashboard on github"
        val result = useCase(input)
        assertTrue(result.contains("API"))
        assertTrue(result.contains("GitHub"))
    }
}
