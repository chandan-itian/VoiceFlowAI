package com.voiceflow.ai.presentation.ui.privacy

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.voiceflow.ai.presentation.ui.components.SectionHeader
import com.voiceflow.ai.presentation.ui.components.VoiceFlowTopBar

@Composable
fun PrivacyScreen(
    onNavigateBack: () -> Unit
) {
    Scaffold(
        topBar = {
            VoiceFlowTopBar(
                title = "Privacy & Security",
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
            SectionHeader(title = "CORE PRIVACY COMMITMENTS")

            PrivacyInfoCard(
                title = "100% Offline Processing in Default Mode",
                description = "When Offline or Hybrid mode is selected, your voice audio is transcribed locally on your Vivo T2 5G's Snapdragon 695 processor. Audio files are stored ephemerally in private app storage and cleaned up immediately after transcription."
            )

            PrivacyInfoCard(
                title = "No Hidden or Automatic Recording",
                description = "The microphone is ONLY activated when you explicitly tap the floating microphone button. Recording stops immediately when you tap it again or when transcription concludes."
            )

            PrivacyInfoCard(
                title = "Strict Protection Against Sensitive Fields",
                description = "VoiceFlow AI actively detects password fields, PIN inputs, OTP verifications, credit card numbers, and banking apps. The floating microphone is automatically suppressed to ensure complete security."
            )

            PrivacyInfoCard(
                title = "Accessibility Service Disclosure",
                description = "VoiceFlow AI uses the Android AccessibilityService API strictly to:\n" +
                        "1. Detect when an eligible editable text field gains focus.\n" +
                        "2. Insert transcribed text into your active message or document.\n\n" +
                        "VoiceFlow AI never reads your personal messages, never logs keystrokes, and never accesses sensitive credentials."
            )

            PrivacyInfoCard(
                title = "Hardware Keystore API Key Encryption",
                description = "When you configure custom cloud API providers, all API keys are encrypted using AES-256-GCM backed by the Android Hardware Keystore. No credentials are ever transmitted to third parties or logged."
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun PrivacyInfoCard(
    title: String,
    description: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
