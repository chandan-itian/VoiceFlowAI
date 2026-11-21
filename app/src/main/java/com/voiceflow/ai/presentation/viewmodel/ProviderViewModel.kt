package com.voiceflow.ai.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voiceflow.ai.domain.model.AiProvider
import com.voiceflow.ai.domain.repository.AiProviderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProviderUiState(
    val providers: List<AiProvider> = emptyList(),
    val selectedProvider: AiProvider? = null,
    val isLoading: Boolean = false,
    val testConnectionMessage: String? = null,
    val isTestSuccessful: Boolean? = null,
    val fetchedModels: List<String> = emptyList(),
    val errorMessage: String? = null
)

@HiltViewModel
class ProviderViewModel @Inject constructor(
    private val providerRepository: AiProviderRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProviderUiState())
    val uiState: StateFlow<ProviderUiState> = _uiState.asStateFlow()

    init {
        observeProviders()
    }

    private fun observeProviders() {
        viewModelScope.launch {
            providerRepository.getProviders().collect { list ->
                _uiState.update { it.copy(providers = list) }
            }
        }
    }

    fun saveProvider(provider: AiProvider) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val exists = _uiState.value.providers.any { it.id == provider.id }
                if (exists) {
                    providerRepository.updateProvider(provider)
                } else {
                    providerRepository.addProvider(provider)
                }
                _uiState.update { it.copy(isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = e.localizedMessage) }
            }
        }
    }

    fun deleteProvider(id: String) {
        viewModelScope.launch {
            try {
                providerRepository.deleteProvider(id)
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.localizedMessage) }
            }
        }
    }

    fun setDefaultProvider(id: String) {
        viewModelScope.launch {
            try {
                providerRepository.setDefaultProvider(id)
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.localizedMessage) }
            }
        }
    }

    fun testConnection(provider: AiProvider) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    testConnectionMessage = "Testing connection...",
                    isTestSuccessful = null
                )
            }
            val result = providerRepository.testConnection(provider)
            result.onSuccess { msg ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        testConnectionMessage = msg,
                        isTestSuccessful = true
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        testConnectionMessage = "Error: ${err.localizedMessage ?: "Connection failed"}",
                        isTestSuccessful = false
                    )
                }
            }
        }
    }

    fun fetchModels(provider: AiProvider) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = providerRepository.fetchModels(provider)
            result.onSuccess { models ->
                _uiState.update { it.copy(isLoading = false, fetchedModels = models) }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Failed to fetch models: ${err.localizedMessage}"
                    )
                }
            }
        }
    }

    fun clearTestResult() {
        _uiState.update {
            it.copy(
                testConnectionMessage = null,
                isTestSuccessful = null,
                errorMessage = null
            )
        }
    }
}
