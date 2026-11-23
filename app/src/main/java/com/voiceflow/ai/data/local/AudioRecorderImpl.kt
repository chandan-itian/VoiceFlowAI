@file:Suppress("MissingPermission")

package com.voiceflow.ai.data.local

import com.voiceflow.ai.data.local.whisper.WhisperJni
import com.voiceflow.ai.domain.model.RecordedAudio
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.voiceflow.ai.domain.repository.AudioRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.sqrt

@Singleton
class AudioRecorderImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val whisper: WhisperJni
) : AudioRepository {

    companion object {
        private const val TAG = "AudioRecorder"
        private const val SAMPLE_RATE = 16_000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val RECORDINGS_DIR = "recordings"
    }

    @Volatile
    private var audioRecord: AudioRecord? = null

    @Volatile
    private var recording = false

    @Volatile
    private var currentFile: File? = null

    @Volatile
    private var recordingThread: Thread? = null

    private val _amplitudeFlow = MutableSharedFlow<Float>(replay = 1, extraBufferCapacity = 64)
    override val amplitudeFlow: Flow<Float> = _amplitudeFlow.asSharedFlow()

    private val recordingsDir: File
        get() = File(context.cacheDir, RECORDINGS_DIR).also { it.mkdirs() }

    override suspend fun startRecording(): Boolean = withContext(Dispatchers.IO) {
        try {
            if (recording) {
                Log.w(TAG, "Already recording")
                return@withContext false
            }

            val minBufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT
            )
            if (minBufferSize == AudioRecord.ERROR_BAD_VALUE ||
                minBufferSize == AudioRecord.ERROR
            ) {
                Log.e(TAG, "Invalid buffer size: $minBufferSize")
                return@withContext false
            }

            val internalBufferSize = maxOf(minBufferSize, SAMPLE_RATE * 2)

            val recorder = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                internalBufferSize
            )

            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord failed to initialise")
                recorder.release()
                return@withContext false
            }

            val outFile = File(recordingsDir, "rec_${System.currentTimeMillis()}.wav")
            currentFile = outFile

            recorder.startRecording()
            audioRecord = recorder
            recording = true

            // Responsive read chunk of 1024 shorts (~64ms) for smooth dancing waveform
            val thread = Thread({
                val chunk = ShortArray(1024)
                val byteBuffer = ByteBuffer.allocate(chunk.size * 2).order(ByteOrder.LITTLE_ENDIAN)
                val fos = FileOutputStream(outFile)

                try {
                    // Write placeholder WAV header
                    fos.write(ByteArray(44))
                    var totalSamplesWritten = 0L

                    while (recording) {
                        val read = recorder.read(chunk, 0, chunk.size)
                        if (read > 0) {
                            byteBuffer.clear()
                            for (i in 0 until read) {
                                byteBuffer.putShort(chunk[i])
                            }
                            fos.write(byteBuffer.array(), 0, read * 2)
                            totalSamplesWritten += read

                            // Responsive RMS calculation reacting strictly to voice amplitude
                            val rms = computeRms(chunk, read)
                            _amplitudeFlow.tryEmit(rms)
                        }
                    }

                    fos.flush()
                    fos.close()

                    // Patch WAV header with final size
                    patchWavHeader(outFile, totalSamplesWritten)
                    Log.d(TAG, "WAV header patched with $totalSamplesWritten samples")
                } catch (e: Exception) {
                    Log.e(TAG, "Recording loop error", e)
                    try { fos.close() } catch (_: Exception) {}
                }
            }, "audio-recorder")

            recordingThread = thread
            thread.start()

            Log.d(TAG, "Recording started → ${outFile.name}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "startRecording error", e)
            releaseResources()
            false
        }
    }

    override suspend fun stopRecording(): RecordedAudio? = withContext(Dispatchers.IO) {
        try {
            if (!recording) return@withContext null
            recording = false

            try {
                audioRecord?.stop()
            } catch (e: Exception) {
                Log.w(TAG, "audioRecord stop warning: ${e.message}")
            }

            // Wait for writer thread to finish writing and patch WAV header
            recordingThread?.join()
            recordingThread = null

            try {
                audioRecord?.release()
            } catch (e: Exception) {
                Log.w(TAG, "audioRecord release warning: ${e.message}")
            }
            audioRecord = null

            val file = currentFile ?: return@withContext null
            currentFile = null
            _amplitudeFlow.tryEmit(0f)
            RecordedAudio(file.absolutePath, filterSpeech(file))
        } catch (e: Exception) {
            Log.e(TAG, "stopRecording error", e)
            releaseResources()
            null
        }
    }

    // On inference or I/O failure keep the complete dictation.
    private fun filterSpeech(file: File): Boolean {
        val modelName = "ggml-silero-v5.1.2.bin"
        val model = File(context.cacheDir, modelName)
        var filtered: File? = null
        try {
            if (!model.exists()) {
                val temp = File.createTempFile("vad-model", ".bin", context.cacheDir)
                try {
                    context.assets.open(modelName).use { input -> temp.outputStream().use { input.copyTo(it) } }
                    check(temp.renameTo(model))
                } finally {
                    temp.delete()
                }
            }
            val ranges = whisper.speechRanges(model.absolutePath, file.absolutePath) ?: return true
            if (ranges.isEmpty()) return false
            if (ranges.size == 2 && ranges[0] == 0L && ranges[1] * 2 + 44 == file.length()) return true

            val output = File.createTempFile("speech", ".wav", file.parentFile)
            filtered = output
            var written = 0L
            RandomAccessFile(file, "r").use { input ->
                output.outputStream().buffered().use { out ->
                    out.write(ByteArray(44))
                    val buffer = ByteArray(8192)
                    for (i in ranges.indices step 2) {
                        input.seek(44 + ranges[i] * 2)
                        var bytes = (ranges[i + 1] - ranges[i]) * 2
                        written += bytes / 2
                        while (bytes > 0) {
                            val size = minOf(bytes, buffer.size.toLong()).toInt()
                            input.readFully(buffer, 0, size)
                            out.write(buffer, 0, size)
                            bytes -= size
                        }
                    }
                }
            }
            patchWavHeader(output, written)
            check(output.renameTo(file))
        } catch (e: Exception) {
            Log.w(TAG, "Speech filtering unavailable", e)
        } finally {
            filtered?.delete()
        }
        return true
    }

    override fun isRecording(): Boolean = recording

    override fun cancelRecording() {
        recording = false
        try {
            audioRecord?.stop()
            recordingThread?.join(500)
            audioRecord?.release()
        } catch (_: Exception) {}
        recordingThread = null
        audioRecord = null

        currentFile?.delete()
        currentFile = null
        _amplitudeFlow.tryEmit(0f)
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun releaseResources() {
        recording = false
        try {
            audioRecord?.stop()
            recordingThread?.join(500)
            audioRecord?.release()
        } catch (_: Exception) {}
        recordingThread = null
        audioRecord = null
        currentFile = null
        _amplitudeFlow.tryEmit(0f)
    }

    private fun computeRms(buffer: ShortArray, length: Int): Float {
        if (length == 0) return 0f
        var sum = 0.0
        for (i in 0 until length) {
            val sample = buffer[i].toDouble() / Short.MAX_VALUE
            sum += sample * sample
        }
        val rawRms = sqrt(sum / length).toFloat()
        // Ambient silence threshold at ~0.006.
        // Speaking voice (0.02 - 0.25) maps smoothly and responsively to [0.15 .. 1.0]
        return if (rawRms < 0.006f) {
            0f
        } else {
            ((rawRms - 0.006f) * 5.5f).coerceIn(0f, 1f)
        }
    }

    /**
     * Writes a proper 16 kHz mono 16-bit PCM WAV header.
     */
    private fun patchWavHeader(file: File, totalSamples: Long) {
        val dataSize = totalSamples * 2  // 16-bit = 2 bytes per sample
        val byteRate = SAMPLE_RATE * 1 * 16 / 8  // sampleRate * channels * bitsPerSample / 8
        val blockAlign = 1 * 16 / 8               // channels * bitsPerSample / 8

        RandomAccessFile(file, "rw").use { raf ->
            raf.seek(0)
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
                // RIFF header
                put("RIFF".toByteArray())
                putInt((36 + dataSize).toInt())  // file size - 8
                put("WAVE".toByteArray())

                // fmt sub-chunk
                put("fmt ".toByteArray())
                putInt(16)                       // sub-chunk size
                putShort(1)                      // PCM format
                putShort(1)                      // mono
                putInt(SAMPLE_RATE)
                putInt(byteRate)
                putShort(blockAlign.toShort())
                putShort(16)                     // bits per sample

                // data sub-chunk
                put("data".toByteArray())
                putInt(dataSize.toInt())
            }
            raf.write(header.array())
        }
    }
}
