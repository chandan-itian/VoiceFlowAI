package com.voiceflow.ai.data.local.whisper

import android.content.Context
import android.util.Log
import com.voiceflow.ai.domain.model.TranscriptionResult
import com.voiceflow.ai.domain.model.WhisperModel
import com.voiceflow.ai.domain.repository.WhisperRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WhisperRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : WhisperRepository {

    companion object {
        private const val TAG = "WhisperRepo"
        private const val MODELS_DIR = "whisper_models"
        private const val DOWNLOAD_BUFFER = 8 * 1024
    }

    private val jni = WhisperJni()
    private val mutex = Mutex()
    private var contextPtr: Long = 0L
    private var loadedModelPath: String? = null

    private val modelsDir: File
        get() = File(context.filesDir, MODELS_DIR).also { it.mkdirs() }

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.MINUTES)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    // ── Model loading ────────────────────────────────────────────────────────

    override suspend fun loadModel(modelPath: String, threads: Int): Boolean =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                try {
                    // Check if already loaded with the same path and valid
                    if (contextPtr != 0L && loadedModelPath == modelPath && jni.isContextValid(contextPtr)) {
                        Log.d(TAG, "Model already loaded and valid: $modelPath")
                        return@withContext true
                    }
                    // Unload current context if any
                    releaseContextUnsafe()
                    val file = File(modelPath)
                    if (!file.exists() || file.length() < 1024 * 1024) {
                        Log.e(TAG, "Model file does not exist or is too small: $modelPath")
                        return@withContext false
                    }
                    val ptr = jni.initContext(modelPath)
                    if (ptr == 0L) {
                        Log.e(TAG, "Failed to init whisper context for $modelPath")
                        return@withContext false
                    }
                    contextPtr = ptr
                    loadedModelPath = modelPath
                    Log.i(TAG, "Whisper model loaded successfully: $modelPath (ptr=$ptr)")
                    true
                } catch (e: Exception) {
                    Log.e(TAG, "loadModel error", e)
                    false
                }
            }
        }

    override suspend fun ensureModelLoaded(): Boolean = withContext(Dispatchers.IO) {
        if (isModelLoaded()) return@withContext true

        mutex.withLock {
            if (contextPtr != 0L && jni.isContextValid(contextPtr)) {
                return@withContext true
            }

            // Look for downloaded models in priority: Base -> Small -> Tiny -> any .bin > 10MB
            val candidateFiles = listOf(
                File(modelsDir, WhisperModel.BASE.fileName),
                File(modelsDir, WhisperModel.SMALL.fileName),
                File(modelsDir, WhisperModel.TINY.fileName)
            ) + (modelsDir.listFiles { f -> f.extension == "bin" && f.length() > 10 * 1024 * 1024 }?.toList() ?: emptyList())

            val modelToLoad = candidateFiles.firstOrNull { it.exists() && it.length() > 10 * 1024 * 1024 }
            if (modelToLoad != null) {
                Log.i(TAG, "Auto-loading detected Whisper model: ${modelToLoad.absolutePath} (${modelToLoad.length()} bytes)")
                releaseContextUnsafe()
                val ptr = jni.initContext(modelToLoad.absolutePath)
                if (ptr != 0L) {
                    contextPtr = ptr
                    loadedModelPath = modelToLoad.absolutePath
                    Log.i(TAG, "Whisper model auto-loaded successfully: ${modelToLoad.name} (ptr=$ptr)")
                    return@withContext true
                } else {
                    Log.e(TAG, "Failed to initialize whisper context for auto-loaded model: ${modelToLoad.name}")
                }
            } else {
                Log.w(TAG, "No downloaded Whisper model found in ${modelsDir.absolutePath}")
            }
            return@withContext false
        }
    }

    override suspend fun transcribe(
        audioPath: String,
        language: String,
        initialPrompt: String
    ): TranscriptionResult = withContext(Dispatchers.IO) {
        // Auto-load if not already in memory
        if (!isModelLoaded()) {
            ensureModelLoaded()
        }

        mutex.withLock {
            require(contextPtr != 0L && jni.isContextValid(contextPtr)) {
                "No Whisper model loaded. Please download a model from Offline Whisper Models in Settings."
            }

            val start = System.currentTimeMillis()
            // 2 threads pinned to Cortex-A78 Big cores on Snapdragon 695 for optimal latency
            val text = jni.transcribe(contextPtr, audioPath, language, 2, initialPrompt)
            val durationMs = System.currentTimeMillis() - start

            TranscriptionResult(
                text = text.trim(),
                rawText = text.trim(),
                language = language.ifEmpty { "auto" },
                durationMs = durationMs,
                provider = "whisper.cpp",
                isOffline = true
            )
        }
    }

    override fun isModelLoaded(): Boolean = contextPtr != 0L && jni.isContextValid(contextPtr)

    override fun unloadModel() {
        // Best-effort synchronous unload; mutex not needed here because
        // callers should not be transcribing concurrently.
        releaseContextUnsafe()
    }

    private fun releaseContextUnsafe() {
        if (contextPtr != 0L) {
            try {
                jni.freeContext(contextPtr)
            } catch (e: Exception) {
                Log.w(TAG, "freeContext error", e)
            }
            contextPtr = 0L
            loadedModelPath = null
        }
    }

    // ── Model management ─────────────────────────────────────────────────────

    override fun getAvailableModels(): List<WhisperModel> = WhisperModel.ALL

    override fun isModelDownloaded(model: WhisperModel): Boolean {
        val file = File(modelsDir, model.fileName)
        return file.exists() && file.length() > 0
    }

    override fun getModelPath(model: WhisperModel): String =
        File(modelsDir, model.fileName).absolutePath

    override suspend fun downloadModel(
        model: WhisperModel,
        onProgress: (Float) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val targetFile = File(modelsDir, model.fileName)
        val tempFile = File(modelsDir, "${model.fileName}.tmp")

        try {
            val request = Request.Builder().url(model.downloadUrl).build()
            val response = httpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                Log.e(TAG, "Download failed: HTTP ${response.code}")
                return@withContext false
            }

            val body = response.body ?: return@withContext false
            val totalBytes = body.contentLength().coerceAtLeast(model.sizeBytes)

            body.byteStream().use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(DOWNLOAD_BUFFER)
                    var bytesRead: Long = 0
                    var len: Int

                    while (input.read(buffer).also { len = it } != -1) {
                        output.write(buffer, 0, len)
                        bytesRead += len
                        val progress = (bytesRead.toFloat() / totalBytes).coerceIn(0f, 1f)
                        onProgress(progress)
                    }
                    output.flush()
                }
            }

            // Atomic rename
            if (targetFile.exists()) targetFile.delete()
            tempFile.renameTo(targetFile)
            onProgress(1f)
            Log.d(TAG, "Model downloaded: ${model.fileName}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Download error for ${model.fileName}", e)
            tempFile.delete()
            false
        }
    }

    override suspend fun deleteModel(model: WhisperModel): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val file = File(modelsDir, model.fileName)
                // Unload if currently loaded
                if (loadedModelPath == file.absolutePath) {
                    mutex.withLock { releaseContextUnsafe() }
                }
                file.delete()
            } catch (e: Exception) {
                Log.e(TAG, "deleteModel error", e)
                false
            }
        }

    override suspend fun verifyModelIntegrity(model: WhisperModel): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val file = File(modelsDir, model.fileName)
                if (!file.exists()) return@withContext false
                // Size tolerance: ±5 %
                val lowerBound = (model.sizeBytes * 0.95).toLong()
                val upperBound = (model.sizeBytes * 1.05).toLong()
                file.length() in lowerBound..upperBound
            } catch (e: Exception) {
                Log.e(TAG, "verifyModelIntegrity error", e)
                false
            }
        }
}
