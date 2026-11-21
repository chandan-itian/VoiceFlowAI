package com.voiceflow.ai.presentation.viewmodel

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voiceflow.ai.domain.model.WhisperModel
import com.voiceflow.ai.domain.repository.SettingsRepository
import com.voiceflow.ai.domain.repository.WhisperRepository
import com.voiceflow.ai.presentation.service.ModelDownloadService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ModelItemUiState(
    val model: WhisperModel,
    val isDownloaded: Boolean = false,
    val isActive: Boolean = false,
    val isDownloading: Boolean = false,
    val downloadProgress: Float = 0f
)

data class ModelManagerUiState(
    val models: List<ModelItemUiState> = emptyList(),
    val activeModelId: String = "base",
    val isLoading: Boolean = false,
    val infoMessage: String? = null
)

@HiltViewModel
class ModelViewModel @Inject constructor(
    private val whisperRepository: WhisperRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ModelManagerUiState())
    val uiState: StateFlow<ModelManagerUiState> = _uiState.asStateFlow()

    init {
        refreshModels()
    }

    fun refreshModels() {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                val activeId = settings.selectedWhisperModel
                val items = WhisperModel.ALL.map { m ->
                    val downloaded = whisperRepository.isModelDownloaded(m)
                    ModelItemUiState(
                        model = m,
                        isDownloaded = downloaded,
                        isActive = (m.id == activeId)
                    )
                }
                _uiState.update {
                    it.copy(models = items, activeModelId = activeId)
                }
            }
        }
    }

    fun setActiveModel(model: WhisperModel) {
        viewModelScope.launch {
            if (!whisperRepository.isModelDownloaded(model)) {
                _uiState.update { it.copy(infoMessage = "Please download ${model.name} first") }
                return@launch
            }
            settingsRepository.updateSettings { it.copy(selectedWhisperModel = model.id) }
            val path = whisperRepository.getModelPath(model)
            whisperRepository.loadModel(path)
            _uiState.update { it.copy(infoMessage = "${model.name} activated for offline dictation") }
        }
    }

    fun downloadModel(context: Context, model: WhisperModel) {
        val intent = Intent(context, ModelDownloadService::class.java).apply {
            action = ModelDownloadService.ACTION_START_DOWNLOAD
            putExtra(ModelDownloadService.EXTRA_MODEL_ID, model.id)
        }
        context.startForegroundService(intent)
        _uiState.update { it.copy(infoMessage = "Download started for ${model.name} in background") }
    }

    fun deleteModel(model: WhisperModel) {
        viewModelScope.launch {
            val deleted = whisperRepository.deleteModel(model)
            if (deleted) {
                _uiState.update { it.copy(infoMessage = "${model.name} deleted") }
                refreshModels()
            } else {
                _uiState.update { it.copy(infoMessage = "Could not delete ${model.name}") }
            }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(infoMessage = null) }
    }
}
