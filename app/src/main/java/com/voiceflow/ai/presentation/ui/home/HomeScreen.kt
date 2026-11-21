package com.voiceflow.ai.presentation.ui.home

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.voiceflow.ai.R
import com.voiceflow.ai.presentation.ui.components.SettingsClickableItem
import com.voiceflow.ai.presentation.ui.components.VoiceFlowTopBar
import com.voiceflow.ai.presentation.viewmodel.HomeUiState
import com.voiceflow.ai.presentation.viewmodel.HomeViewModel
import com.voiceflow.ai.utils.PermissionUtils

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToAISettings: () -> Unit,
    onNavigateToModelManager: () -> Unit,
    onNavigateToProviders: () -> Unit,
    onNavigateToTranscription: () -> Unit,
    onNavigateToFloatingSettings: () -> Unit,
    onNavigateToPrivacy: () -> Unit,
    onNavigateToDiagnostics: () -> Unit
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.refreshPermissions(context)
    }

    Scaffold(
        topBar = {
            VoiceFlowTopBar(title = "VoiceFlow AI")
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // Master App Toggle Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (state.isAppEnabled) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(
                                if (state.isAppEnabled) Color(0xFF8B5CF6) else Color.Gray
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_wispr_flow),
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (state.isAppEnabled) "VoiceFlow is Active" else "VoiceFlow is Paused",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = if (state.isReadyToDictate) {
                                "Ready for floating dictation alongside Gboard"
                            } else {
                                "Tap below to complete permission setup"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = state.isAppEnabled,
                        onCheckedChange = { viewModel.toggleAppEnabled() }
                    )
                }
            }

            // Permissions Checklist if any permission is missing
            if (!state.hasAudioPermission || !state.hasOverlayPermission || !state.hasAccessibilityPermission) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Required Permissions",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        if (!state.hasAudioPermission) {
                            PermissionRow(
                                title = "Microphone Access",
                                subtitle = "Needed for voice recording",
                                onClick = { PermissionUtils.openAppSettings(context) }
                            )
                        }
                        if (!state.hasAccessibilityPermission) {
                            PermissionRow(
                                title = "Accessibility Service",
                                subtitle = "Detects text field focus to show mic",
                                onClick = { PermissionUtils.openAccessibilitySettings(context) }
                            )
                        }
                        if (!state.hasOverlayPermission) {
                            PermissionRow(
                                title = "Display Over Other Apps",
                                subtitle = "Required for floating mic overlay",
                                onClick = { PermissionUtils.openOverlaySettings(context) }
                            )
                        }
                    }
                }
            }

            // Current Configuration Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Active Engine & Model",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Mode: ${state.currentAiMode.name}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Model: ${state.currentModelName}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Provider: ${state.defaultProviderName}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Navigation Items
            SettingsClickableItem(
                title = "AI Engine & Mode",
                subtitle = "Offline, Online API, or Smart Hybrid",
                icon = Icons.Default.Psychology,
                onClick = onNavigateToAISettings
            )

            SettingsClickableItem(
                title = "Offline Whisper Models",
                subtitle = "Download Base (142MB), Tiny (75MB), or Small",
                icon = Icons.Default.Download,
                onClick = onNavigateToModelManager
            )

            SettingsClickableItem(
                title = "API Providers Manager",
                subtitle = "Gemini, Groq, OpenAI, OpenRouter, Custom",
                icon = Icons.Default.Cloud,
                onClick = onNavigateToProviders
            )

            SettingsClickableItem(
                title = "Transcription & Language",
                subtitle = "Hindi, English, Hinglish, punctuation & fillers",
                icon = Icons.Default.Translate,
                onClick = onNavigateToTranscription
            )

            SettingsClickableItem(
                title = "Floating Microphone UI",
                subtitle = "Position, size, and appearance",
                icon = Icons.Default.Widgets,
                onClick = onNavigateToFloatingSettings
            )

            SettingsClickableItem(
                title = "Privacy & Permissions",
                subtitle = "Security controls and local processing info",
                icon = Icons.Default.Security,
                onClick = onNavigateToPrivacy
            )

            SettingsClickableItem(
                title = "Diagnostics & Status",
                subtitle = "Latency, service logs, and benchmarks",
                icon = Icons.Default.Speed,
                onClick = onNavigateToDiagnostics
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun PermissionRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = onClick) {
            Text("Grant")
        }
    }
}
