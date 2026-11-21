package com.voiceflow.ai.presentation.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.voiceflow.ai.R
import com.voiceflow.ai.VoiceFlowApp
import com.voiceflow.ai.domain.model.WhisperModel
import com.voiceflow.ai.domain.repository.WhisperRepository
import com.voiceflow.ai.presentation.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject

@AndroidEntryPoint
class ModelDownloadService : Service() {

    companion object {
        private const val TAG = "ModelDownloadService"
        private const val NOTIFICATION_ID = 2002

        const val ACTION_START_DOWNLOAD = "com.voiceflow.ai.ACTION_START_DOWNLOAD"
        const val ACTION_CANCEL_DOWNLOAD = "com.voiceflow.ai.ACTION_CANCEL_DOWNLOAD"
        const val EXTRA_MODEL_ID = "extra_model_id"
    }

    @Inject
    lateinit var whisperRepository: WhisperRepository

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var downloadJob: Job? = null
    private var currentModelName: String = "Whisper Model"

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_DOWNLOAD -> {
                val modelId = intent.getStringExtra(EXTRA_MODEL_ID) ?: "base"
                val model = WhisperModel.ALL.find { it.id == modelId } ?: WhisperModel.BASE
                currentModelName = model.name
                startForeground(NOTIFICATION_ID, createNotification(0, "Starting download of ${model.name}..."))
                startDownload(model)
            }
            ACTION_CANCEL_DOWNLOAD -> {
                downloadJob?.cancel()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun startDownload(model: WhisperModel) {
        downloadJob?.cancel()
        downloadJob = serviceScope.launch {
            try {
                val success = whisperRepository.downloadModel(model) { progress ->
                    val percent = (progress * 100).toInt()
                    updateNotification(percent, "Downloading ${model.name}: $percent%")
                }

                if (success) {
                    val path = whisperRepository.getModelPath(model)
                    whisperRepository.loadModel(path)
                    Log.i(TAG, "Model downloaded and automatically loaded: $path")
                    showCompletionNotification("Download complete: ${model.name}")
                } else {
                    showCompletionNotification("Download failed for ${model.name}")
                }
            } catch (e: CancellationException) {
                Log.i(TAG, "Download cancelled for ${model.name}")
            } catch (e: Exception) {
                Log.e(TAG, "Download error", e)
                showCompletionNotification("Download error: ${e.localizedMessage}")
            } finally {
                stopForeground(STOP_FOREGROUND_DETACH)
                stopSelf()
            }
        }
    }

    private fun updateNotification(progress: Int, text: String) {
        val notification = createNotification(progress, text)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotification(progress: Int, text: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cancelIntent = Intent(this, ModelDownloadService::class.java).apply {
            action = ACTION_CANCEL_DOWNLOAD
        }
        val cancelPendingIntent = PendingIntent.getService(
            this, 1, cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, VoiceFlowApp.CHANNEL_DOWNLOAD)
            .setContentTitle("VoiceFlow AI Model Download")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_mic)
            .setContentIntent(pendingIntent)
            .addAction(0, "Cancel", cancelPendingIntent)
            .setProgress(100, progress, false)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun showCompletionNotification(text: String) {
        val notification = NotificationCompat.Builder(this, VoiceFlowApp.CHANNEL_DOWNLOAD)
            .setContentTitle("VoiceFlow AI")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_mic)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID + 1, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        downloadJob?.cancel()
        serviceScope.cancel()
        Log.i(TAG, "ModelDownloadService destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
