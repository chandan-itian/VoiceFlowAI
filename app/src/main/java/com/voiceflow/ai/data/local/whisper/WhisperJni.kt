package com.voiceflow.ai.data.local.whisper

/**
 * JNI bridge to the whisper.cpp native library.
 *
 * The native library must be compiled separately and placed in the
 * appropriate jniLibs directories (armeabi-v7a, arm64-v8a, x86_64).
 */
class WhisperJni {

    /**
     * Initialises a Whisper context from the given GGML model file.
     * @return a native pointer handle (0 on failure)
     */
    external fun initContext(modelPath: String): Long

    /**
     * Runs full Whisper transcription on a WAV file.
     * @param contextPtr native context pointer obtained from [initContext]
     * @param audioPath  path to a 16 kHz mono 16-bit WAV file
     * @param language   ISO-639-1 language hint (empty = auto-detect)
     * @param threads    number of CPU threads to use
     * @param initialPrompt optional context/vocabulary biasing prompt
     * @return transcribed text
     */
    external fun transcribe(
        contextPtr: Long,
        audioPath: String,
        language: String,
        threads: Int,
        initialPrompt: String = ""
    ): String

    /**
     * Frees a Whisper context.
     */
    external fun freeContext(contextPtr: Long)

    /**
     * Checks if a context pointer is still valid.
     */
    external fun isContextValid(contextPtr: Long): Boolean

    companion object {
        init {
            System.loadLibrary("whisper_jni")
        }
    }
}
