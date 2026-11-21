package com.voiceflow.ai.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.voiceflow.ai.domain.model.AiMode
import com.voiceflow.ai.domain.model.AppSettings
import com.voiceflow.ai.domain.model.LanguageMode
import com.voiceflow.ai.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "voiceflow_settings")

@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) : SettingsRepository {

    private object Keys {
        val AI_MODE = stringPreferencesKey("ai_mode")
        val LANGUAGE_MODE = stringPreferencesKey("language_mode")
        val SELECTED_WHISPER_MODEL = stringPreferencesKey("selected_whisper_model")
        val DEFAULT_PROVIDER_ID = stringPreferencesKey("default_provider_id")
        val ENABLE_PUNCTUATION = booleanPreferencesKey("enable_punctuation")
        val ENABLE_CAPITALIZATION = booleanPreferencesKey("enable_capitalization")
        val ENABLE_FILLER_REMOVAL = booleanPreferencesKey("enable_filler_removal")
        val ENABLE_GRAMMAR_CORRECTION = booleanPreferencesKey("enable_grammar_correction")
        val ENABLE_RAW_TRANSCRIPT = booleanPreferencesKey("enable_raw_transcript")
        val FLOATING_MIC_SIZE = intPreferencesKey("floating_mic_size")
        val CPU_THREADS = intPreferencesKey("cpu_threads")
        val IS_APP_ENABLED = booleanPreferencesKey("is_app_enabled")
    }

    override val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        prefs.toAppSettings()
    }

    override suspend fun updateSettings(update: (AppSettings) -> AppSettings) {
        val current = getSettings()
        val updated = update(current)
        context.dataStore.edit { prefs ->
            prefs[Keys.AI_MODE] = updated.aiMode.name
            prefs[Keys.LANGUAGE_MODE] = updated.languageMode.name
            prefs[Keys.SELECTED_WHISPER_MODEL] = updated.selectedWhisperModel
            prefs[Keys.DEFAULT_PROVIDER_ID] = updated.defaultProviderId
            prefs[Keys.ENABLE_PUNCTUATION] = updated.enablePunctuation
            prefs[Keys.ENABLE_CAPITALIZATION] = updated.enableCapitalization
            prefs[Keys.ENABLE_FILLER_REMOVAL] = updated.enableFillerRemoval
            prefs[Keys.ENABLE_GRAMMAR_CORRECTION] = updated.enableGrammarCorrection
            prefs[Keys.ENABLE_RAW_TRANSCRIPT] = updated.enableRawTranscript
            prefs[Keys.FLOATING_MIC_SIZE] = updated.floatingMicSize
            prefs[Keys.CPU_THREADS] = updated.cpuThreads
            prefs[Keys.IS_APP_ENABLED] = updated.isAppEnabled
        }
    }

    override suspend fun getSettings(): AppSettings {
        return context.dataStore.data.first().toAppSettings()
    }

    private fun Preferences.toAppSettings(): AppSettings {
        return AppSettings(
            aiMode = this[Keys.AI_MODE]?.let {
                runCatching { AiMode.valueOf(it) }.getOrDefault(AiMode.OFFLINE)
            } ?: AiMode.OFFLINE,
            languageMode = this[Keys.LANGUAGE_MODE]?.let {
                runCatching { LanguageMode.valueOf(it) }.getOrDefault(LanguageMode.AUTO)
            } ?: LanguageMode.AUTO,
            selectedWhisperModel = this[Keys.SELECTED_WHISPER_MODEL] ?: "base",
            defaultProviderId = this[Keys.DEFAULT_PROVIDER_ID] ?: "",
            enablePunctuation = this[Keys.ENABLE_PUNCTUATION] ?: true,
            enableCapitalization = this[Keys.ENABLE_CAPITALIZATION] ?: true,
            enableFillerRemoval = this[Keys.ENABLE_FILLER_REMOVAL] ?: true,
            enableGrammarCorrection = this[Keys.ENABLE_GRAMMAR_CORRECTION] ?: false,
            enableRawTranscript = this[Keys.ENABLE_RAW_TRANSCRIPT] ?: false,
            floatingMicSize = this[Keys.FLOATING_MIC_SIZE] ?: 56,
            cpuThreads = this[Keys.CPU_THREADS] ?: 4,
            isAppEnabled = this[Keys.IS_APP_ENABLED] ?: true
        )
    }
}
