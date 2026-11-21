package com.voiceflow.ai.presentation.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.voiceflow.ai.domain.model.AiMode
import com.voiceflow.ai.presentation.ui.components.SectionHeader
import com.voiceflow.ai.presentation.ui.components.VoiceFlowTopBar
import com.voiceflow.ai.presentation.viewmodel.SettingsViewModel

@Composable
fun AISettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit
) {
    val settings by viewModel.settings.collectAsState()

    Scaffold(
        topBar = {
            VoiceFlowTopBar(
                title = "AI Engine Settings",
                canNavigateBack = true,
                onNavigateBack = onNavigateBack
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            SectionHeader(title = "PROCESSING MODE")

            ModeSelectionCard(
                title = "Offline Only (Whisper.cpp)",
                description = "100% on-device inference using Snapdragon 695 CPU. Complete privacy, works without internet. No audio ever leaves your phone.",
                selected = settings.aiMode == AiMode.OFFLINE,
                onSelect = { viewModel.setAiMode(AiMode.OFFLINE) }
            )

            ModeSelectionCard(
                title = "Online AI",
                description = "Send audio directly to high-accuracy cloud models (Google Gemini, Groq Whisper, OpenAI). Requires internet and API key.",
                selected = settings.aiMode == AiMode.ONLINE,
                onSelect = { viewModel.setAiMode(AiMode.ONLINE) }
            )

            ModeSelectionCard(
                title = "Smart Hybrid (Recommended)",
                description = "Instant offline Whisper transcription, followed by optional intelligent online text refinement for perfect punctuation and formatting.",
                selected = settings.aiMode == AiMode.SMART_HYBRID,
                onSelect = { viewModel.setAiMode(AiMode.SMART_HYBRID) }
            )

            Spacer(modifier = Modifier.height(16.dp))
            SectionHeader(title = "PERFORMANCE TUNING (SNAPDRAGON 695)")

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "CPU Inference Threads: ${settings.cpuThreads}",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "Snapdragon 695 has 2 Gold cores + 6 Silver cores. 4 threads offers the best balance of speed and battery efficiency.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Slider(
                        value = settings.cpuThreads.toFloat(),
                        onValueChange = { viewModel.setCpuThreads(it.toInt()) },
                        valueRange = 1f..6f,
                        steps = 4
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ModeSelectionCard(
    title: String,
    description: String,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            }
        ),
        onClick = onSelect
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = selected,
                onClick = onSelect
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
