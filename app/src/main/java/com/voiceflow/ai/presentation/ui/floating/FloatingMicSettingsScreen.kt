package com.voiceflow.ai.presentation.ui.floating

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.voiceflow.ai.presentation.ui.components.SectionHeader
import com.voiceflow.ai.presentation.ui.components.VoiceFlowTopBar
import com.voiceflow.ai.presentation.viewmodel.SettingsViewModel

@Composable
fun FloatingMicSettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit
) {
    val settings by viewModel.settings.collectAsState()

    Scaffold(
        topBar = {
            VoiceFlowTopBar(
                title = "Floating Microphone UI",
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
            SectionHeader(title = "FLOATING OVERLAY CONTROLS")

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Microphone Button Size: ${settings.floatingMicSize} dp",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "Adjust the size of the floating circular microphone overlay.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Slider(
                        value = settings.floatingMicSize.toFloat(),
                        onValueChange = { viewModel.setFloatingMicSize(it.toInt()) },
                        valueRange = 48f..72f,
                        steps = 5
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            SectionHeader(title = "BEHAVIOR & POSITION")

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Drag & Drop Repositioning",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "• Touch and drag the floating microphone anywhere on your screen.\n" +
                                "• Its position is automatically remembered across app switches.\n" +
                                "• It automatically appears only when an editable text field is focused.\n" +
                                "• It never covers your keyboard (Gboard) or text cursor.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
