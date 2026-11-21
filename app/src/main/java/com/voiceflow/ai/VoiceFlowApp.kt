package com.voiceflow.ai

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.util.Log
import com.voiceflow.ai.domain.repository.WhisperRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class VoiceFlowApp : Application() {

    @Inject
    lateinit var whisperRepository: WhisperRepository

    companion object {
        const val CHANNEL_FLOATING_MIC = "floating_mic_channel"
        const val CHANNEL_DOWNLOAD = "model_download_channel"
        const val CHANNEL_TRANSCRIPTION = "transcription_channel"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()

        // Pre-warm and auto-load offline Whisper model in background
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val loaded = whisperRepository.ensureModelLoaded()
                Log.i("VoiceFlowApp", "Initial whisper model pre-warm loaded=$loaded")
            } catch (e: Exception) {
                Log.w("VoiceFlowApp", "Initial whisper model load check error: ${e.message}")
            }
        }
    }

    private fun createNotificationChannels() {
        val floatingChannel = NotificationChannel(
            CHANNEL_FLOATING_MIC,
            "Floating Microphone",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows when the floating microphone service is active"
            setShowBadge(false)
        }

        val downloadChannel = NotificationChannel(
            CHANNEL_DOWNLOAD,
            "Model Downloads",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows AI model download progress"
        }

        val transcriptionChannel = NotificationChannel(
            CHANNEL_TRANSCRIPTION,
            "Transcription",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Transcription notifications"
        }

        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(floatingChannel)
        manager.createNotificationChannel(downloadChannel)
        manager.createNotificationChannel(transcriptionChannel)
    }
}
