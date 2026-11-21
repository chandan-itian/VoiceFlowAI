package com.voiceflow.ai.presentation.ui.diagnostics

import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.voiceflow.ai.presentation.service.VoiceFlowAccessibilityService
import com.voiceflow.ai.presentation.ui.components.SectionHeader
import com.voiceflow.ai.presentation.ui.components.VoiceFlowTopBar
import com.voiceflow.ai.utils.PermissionUtils

@Composable
fun DiagnosticsScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    var sandboxText by remember { mutableStateOf("") }

    val isA11yConnected = VoiceFlowAccessibilityService.instance != null
    val hasAudio = PermissionUtils.hasRecordAudioPermission(context)
    val hasOverlay = PermissionUtils.hasOverlayPermission(context)

    val maxMemoryMb = Runtime.getRuntime().maxMemory() / (1024 * 1024)
    val totalMemoryMb = Runtime.getRuntime().totalMemory() / (1024 * 1024)
    val freeMemoryMb = Runtime.getRuntime().freeMemory() / (1024 * 1024)
    val availableProcessors = Runtime.getRuntime().availableProcessors()

    Scaffold(
        topBar = {
            VoiceFlowTopBar(
                title = "Diagnostics & System",
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
            SectionHeader(title = "SERVICES & PERMISSION STATUS")

            DiagnosticCard(
                title = "Accessibility Service Status",
                value = if (isA11yConnected) "CONNECTED & ACTIVE" else "NOT CONNECTED"
            )

            DiagnosticCard(
                title = "Overlay Window Permission",
                value = if (hasOverlay) "GRANTED" else "DENIED"
            )

            DiagnosticCard(
                title = "Microphone Permission",
                value = if (hasAudio) "GRANTED" else "DENIED"
            )

            Spacer(modifier = Modifier.height(12.dp))
            SectionHeader(title = "DEVICE & HARDWARE (VIVO T2 5G)")

            DiagnosticCard(
                title = "Device Model",
                value = "${Build.MANUFACTURER.uppercase()} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, API ${Build.VERSION.SDK_INT})"
            )

            DiagnosticCard(
                title = "Processor Architecture",
                value = "Snapdragon 695 (Qualcomm Kryo 660, $availableProcessors CPU Cores)"
            )

            DiagnosticCard(
                title = "JVM Memory Allocation",
                value = "Used: ${totalMemoryMb - freeMemoryMb} MB / Total: $totalMemoryMb MB (Max: $maxMemoryMb MB)"
            )

            Spacer(modifier = Modifier.height(12.dp))
            SectionHeader(title = "INTERACTIVE DICTATION SANDBOX")

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Tap the field below to verify text field detection. Gboard will appear, followed by VoiceFlow AI's floating mic.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = sandboxText,
                        onValueChange = { sandboxText = it },
                        label = { Text("Test Dictation Field") },
                        placeholder = { Text("Speak in Hindi, English, or Hinglish...") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun DiagnosticCard(
    title: String,
    value: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
