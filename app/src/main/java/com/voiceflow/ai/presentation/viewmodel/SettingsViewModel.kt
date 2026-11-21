package com.voiceflow.ai.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voiceflow.ai.domain.model.AiMode
import com.voiceflow.ai.domain.model.AppSettings
import com.voiceflow.ai.domain.model.LanguageMode
import com.voiceflow.ai.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AppSettings()
    )

    fun setAiMode(mode: AiMode) {
        viewModelScope.launch {
            settingsRepository.updateSettings { it.copy(aiMode = mode) }
        }
    }

    fun setLanguageMode(mode: LanguageMode) {
        viewModelScope.launch {
            settingsRepository.updateSettings { it.copy(languageMode = mode) }
        }
    }

    fun togglePunctuation() {
        viewModelScope.launch {
            settingsRepository.updateSettings { it.copy(enablePunctuation = !it.enablePunctuation) }
        }
    }

    fun toggleCapitalization() {
        viewModelScope.launch {
            settingsRepository.updateSettings { it.copy(enableCapitalization = !it.enableCapitalization) }
        }
    }

    fun toggleFillerRemoval() {
        viewModelScope.launch {
            settingsRepository.updateSettings { it.copy(enableFillerRemoval = !it.enableFillerRemoval) }
        }
    }

    fun toggleGrammarCorrection() {
        viewModelScope.launch {
            settingsRepository.updateSettings { it.copy(enableGrammarCorrection = !it.enableGrammarCorrection) }
        }
    }

    fun toggleRawTranscript() {
        viewModelScope.launch {
            settingsRepository.updateSettings { it.copy(enableRawTranscript = !it.enableRawTranscript) }
        }
    }

    fun setFloatingMicSize(size: Int) {
        viewModelScope.launch {
            settingsRepository.updateSettings { it.copy(floatingMicSize = size) }
        }
    }

    fun setCpuThreads(threads: Int) {
        viewModelScope.launch {
            settingsRepository.updateSettings { it.copy(cpuThreads = threads.coerceIn(1, 8)) }
        }
    }
}
