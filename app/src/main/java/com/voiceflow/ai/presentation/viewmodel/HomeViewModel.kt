package com.voiceflow.ai.presentation.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voiceflow.ai.domain.model.AiMode
import com.voiceflow.ai.domain.model.WhisperModel
import com.voiceflow.ai.domain.repository.AiProviderRepository
import com.voiceflow.ai.domain.repository.SettingsRepository
import com.voiceflow.ai.domain.repository.WhisperRepository
import com.voiceflow.ai.utils.PermissionUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val isAppEnabled: Boolean = true,
    val isFloatingMicActive: Boolean = false,
    val currentAiMode: AiMode = AiMode.OFFLINE,
    val currentModelName: String = "Base Multilingual",
    val defaultProviderName: String = "Local Whisper",
    val hasAudioPermission: Boolean = false,
    val hasOverlayPermission: Boolean = false,
    val hasAccessibilityPermission: Boolean = false,
    val hasNotificationPermission: Boolean = false,
    val isReadyToDictate: Boolean = false
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val whisperRepository: WhisperRepository,
    private val providerRepository: AiProviderRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        observeSettings()
    }

    private fun observeSettings() {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                val modelName = WhisperModel.ALL.find { it.id == settings.selectedWhisperModel }?.name
                    ?: "Base Multilingual"

                val provider = providerRepository.getDefaultProvider()
                val providerName = when (settings.aiMode) {
                    AiMode.OFFLINE -> "Local Whisper ($modelName)"
                    AiMode.ONLINE -> provider?.name ?: "Online AI"
                    AiMode.SMART_HYBRID -> "Hybrid (Whisper + ${provider?.name ?: "AI Refine"})"
                }

                _uiState.update { state ->
                    state.copy(
                        isAppEnabled = settings.isAppEnabled,
                        currentAiMode = settings.aiMode,
                        currentModelName = modelName,
                        defaultProviderName = providerName
                    )
                }
            }
        }
    }

    fun refreshPermissions(context: Context) {
        val audio = PermissionUtils.hasRecordAudioPermission(context)
        val overlay = PermissionUtils.hasOverlayPermission(context)
        val a11y = PermissionUtils.isAccessibilityServiceEnabled(context)
        val notif = PermissionUtils.hasNotificationPermission(context)

        _uiState.update { state ->
            state.copy(
                hasAudioPermission = audio,
                hasOverlayPermission = overlay,
                hasAccessibilityPermission = a11y,
                hasNotificationPermission = notif,
                isReadyToDictate = audio && overlay && a11y && state.isAppEnabled
            )
        }
    }

    fun toggleAppEnabled() {
        viewModelScope.launch {
            settingsRepository.updateSettings { current ->
                current.copy(isAppEnabled = !current.isAppEnabled)
            }
        }
    }
}
