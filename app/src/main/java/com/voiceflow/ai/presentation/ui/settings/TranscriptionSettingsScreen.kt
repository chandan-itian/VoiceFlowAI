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
import com.voiceflow.ai.domain.model.LanguageMode
import com.voiceflow.ai.presentation.ui.components.SectionHeader
import com.voiceflow.ai.presentation.ui.components.SettingsSwitchItem
import com.voiceflow.ai.presentation.ui.components.VoiceFlowTopBar
import com.voiceflow.ai.presentation.viewmodel.SettingsViewModel

@Composable
fun TranscriptionSettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit
) {
    val settings by viewModel.settings.collectAsState()

    Scaffold(
        topBar = {
            VoiceFlowTopBar(
                title = "Transcription & Language",
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
            SectionHeader(title = "LANGUAGE MODE")

            LanguageSelectionCard(
                title = "Hinglish (Recommended)",
                subtitle = "Hindi in Latin letters with English words preserved (e.g., 'Bhai dashboard update kar do')",
                selected = settings.languageMode == LanguageMode.HINGLISH,
                onSelect = { viewModel.setLanguageMode(LanguageMode.HINGLISH) }
            )

            LanguageSelectionCard(
                title = "Hindi (Devanagari)",
                subtitle = "Pure Hindi output in Devanagari script (e.g., 'भाई डैशबोर्ड अपडेट कर दो')",
                selected = settings.languageMode == LanguageMode.HINDI,
                onSelect = { viewModel.setLanguageMode(LanguageMode.HINDI) }
            )

            LanguageSelectionCard(
                title = "English",
                subtitle = "Dictate in standard English with full vocabulary preservation",
                selected = settings.languageMode == LanguageMode.ENGLISH,
                onSelect = { viewModel.setLanguageMode(LanguageMode.ENGLISH) }
            )

            LanguageSelectionCard(
                title = "Auto-Detect",
                subtitle = "Detect the spoken language. Groq writes detected Hindi or Urdu in Roman Hinglish.",
                selected = settings.languageMode == LanguageMode.AUTO,
                onSelect = { viewModel.setLanguageMode(LanguageMode.AUTO) }
            )

            LanguageSelectionCard(
                title = "Translate to English",
                subtitle = "Translate spoken Hindi or other languages into fluent English",
                selected = settings.languageMode == LanguageMode.ENGLISH_TRANSLATION,
                onSelect = { viewModel.setLanguageMode(LanguageMode.ENGLISH_TRANSLATION) }
            )

            Spacer(modifier = Modifier.height(16.dp))
            SectionHeader(title = "INTELLIGENT TEXT PROCESSING")

            SettingsSwitchItem(
                title = "Auto Punctuation",
                subtitle = "Add periods, commas, question marks automatically",
                checked = settings.enablePunctuation,
                onCheckedChange = { viewModel.togglePunctuation() }
            )

            SettingsSwitchItem(
                title = "Smart Capitalization",
                subtitle = "Capitalize first words and preserved brand names",
                checked = settings.enableCapitalization,
                onCheckedChange = { viewModel.toggleCapitalization() }
            )

            SettingsSwitchItem(
                title = "Remove Filler Words",
                subtitle = "Filters out 'umm', 'uh', 'matlab', 'arre', 'you know'",
                checked = settings.enableFillerRemoval,
                onCheckedChange = { viewModel.toggleFillerRemoval() }
            )

            SettingsSwitchItem(
                title = "Grammar & Flow Polishing",
                subtitle = "Light refinement of speech disfluencies",
                checked = settings.enableGrammarCorrection,
                onCheckedChange = { viewModel.toggleGrammarCorrection() }
            )

            SettingsSwitchItem(
                title = "Show Raw Transcript",
                subtitle = "Retain unmodified speech transcript in diagnostics",
                checked = settings.enableRawTranscript,
                onCheckedChange = { viewModel.toggleRawTranscript() }
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun LanguageSelectionCard(
    title: String,
    subtitle: String,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
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
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = selected, onClick = onSelect)
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.bodyLarge)
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
