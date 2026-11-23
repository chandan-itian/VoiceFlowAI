package com.voiceflow.ai.presentation.ui.home

import androidx.lifecycle.compose.LifecycleResumeEffect
import com.voiceflow.ai.domain.model.AiMode
import com.voiceflow.ai.presentation.ui.components.SectionHeader
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.voiceflow.ai.R
import com.voiceflow.ai.presentation.ui.components.SettingsClickableItem
import com.voiceflow.ai.presentation.ui.components.VoiceFlowTopBar
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

    LifecycleResumeEffect(context) {
        viewModel.refreshPermissions(context)
        onPauseOrDispose { }
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
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (state.isAppEnabled) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                )
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .toggleable(state.isAppEnabled, role = Role.Switch) { viewModel.toggleAppEnabled() }
                        .padding(24.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.size(44.dp).clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_wispr_flow),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Switch(checked = state.isAppEnabled, onCheckedChange = null)
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = if (state.isAppEnabled) "Dictation enabled" else "Dictation paused",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = when {
                            !state.isAppEnabled -> "Turn on to use the floating mic"
                            !state.hasRequiredPermissions -> "Complete microphone setup below"
                            else -> "Open a text field to dictate"
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            // Permissions Checklist if any permission is missing
            if (!state.hasRequiredPermissions) {
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

            SectionHeader("Dictation engine")
            Card(
                onClick = onNavigateToAISettings,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            when (state.currentAiMode) {
                                AiMode.OFFLINE -> "On device"
                                AiMode.ONLINE -> "Online"
                                AiMode.SMART_HYBRID -> "Smart hybrid"
                            },
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(state.defaultProviderName, style = MaterialTheme.typography.bodyMedium)
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null)
                }
            }

            SectionHeader("Models and providers")
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

            SectionHeader("Dictation preferences")
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

            // Footer
            Text(
                text = "Made with ❤️ by Chandan Singh",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
            )
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
