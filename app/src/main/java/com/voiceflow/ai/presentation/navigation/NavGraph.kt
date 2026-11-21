package com.voiceflow.ai.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.voiceflow.ai.presentation.ui.diagnostics.DiagnosticsScreen
import com.voiceflow.ai.presentation.ui.floating.FloatingMicSettingsScreen
import com.voiceflow.ai.presentation.ui.home.HomeScreen
import com.voiceflow.ai.presentation.ui.models.ModelManagerScreen
import com.voiceflow.ai.presentation.ui.privacy.PrivacyScreen
import com.voiceflow.ai.presentation.ui.providers.ProviderManagerScreen
import com.voiceflow.ai.presentation.ui.settings.AISettingsScreen
import com.voiceflow.ai.presentation.ui.settings.TranscriptionSettingsScreen
import com.voiceflow.ai.presentation.viewmodel.HomeViewModel
import com.voiceflow.ai.presentation.viewmodel.ModelViewModel
import com.voiceflow.ai.presentation.viewmodel.ProviderViewModel
import com.voiceflow.ai.presentation.viewmodel.SettingsViewModel

object Destinations {
    const val HOME = "home"
    const val AI_SETTINGS = "ai_settings"
    const val MODEL_MANAGER = "model_manager"
    const val PROVIDERS = "providers"
    const val TRANSCRIPTION = "transcription"
    const val FLOATING = "floating"
    const val PRIVACY = "privacy"
    const val DIAGNOSTICS = "diagnostics"
}

@Composable
fun VoiceFlowNavGraph(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Destinations.HOME
    ) {
        composable(Destinations.HOME) {
            val homeViewModel: HomeViewModel = hiltViewModel()
            HomeScreen(
                viewModel = homeViewModel,
                onNavigateToAISettings = { navController.navigate(Destinations.AI_SETTINGS) },
                onNavigateToModelManager = { navController.navigate(Destinations.MODEL_MANAGER) },
                onNavigateToProviders = { navController.navigate(Destinations.PROVIDERS) },
                onNavigateToTranscription = { navController.navigate(Destinations.TRANSCRIPTION) },
                onNavigateToFloatingSettings = { navController.navigate(Destinations.FLOATING) },
                onNavigateToPrivacy = { navController.navigate(Destinations.PRIVACY) },
                onNavigateToDiagnostics = { navController.navigate(Destinations.DIAGNOSTICS) }
            )
        }

        composable(Destinations.AI_SETTINGS) {
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            AISettingsScreen(
                viewModel = settingsViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Destinations.MODEL_MANAGER) {
            val modelViewModel: ModelViewModel = hiltViewModel()
            ModelManagerScreen(
                viewModel = modelViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Destinations.PROVIDERS) {
            val providerViewModel: ProviderViewModel = hiltViewModel()
            ProviderManagerScreen(
                viewModel = providerViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Destinations.TRANSCRIPTION) {
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            TranscriptionSettingsScreen(
                viewModel = settingsViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Destinations.FLOATING) {
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            FloatingMicSettingsScreen(
                viewModel = settingsViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Destinations.PRIVACY) {
            PrivacyScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Destinations.DIAGNOSTICS) {
            DiagnosticsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
