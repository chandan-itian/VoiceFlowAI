package com.voiceflow.ai.utils

import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

object AudioUtils {

    const val SAMPLE_RATE = 16_000
    const val CHANNELS = 1
    const val BITS_PER_SAMPLE = 16
    const val BYTES_PER_SAMPLE = BITS_PER_SAMPLE / 8
    const val BYTE_RATE = SAMPLE_RATE * CHANNELS * BYTES_PER_SAMPLE

    /**
     * Writes standard 44-byte RIFF/WAVE header for linear PCM.
     */
    fun writeWavHeader(out: FileOutputStream, totalAudioLen: Long) {
        val totalDataLen = totalAudioLen + 36
        val channels = CHANNELS
        val byteRate = BYTE_RATE.toLong()

        val header = ByteArray(44)
        val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)

        // RIFF header
        buffer.put("RIFF".toByteArray())
        buffer.putInt(totalDataLen.toInt())
        buffer.put("WAVE".toByteArray())

        // fmt chunk
        buffer.put("fmt ".toByteArray())
        buffer.putInt(16) // Subchunk1Size for PCM
        buffer.putShort(1.toShort()) // AudioFormat 1 = PCM
        buffer.putShort(channels.toShort())
        buffer.putInt(SAMPLE_RATE)
        buffer.putInt(byteRate.toInt())
        buffer.putShort((channels * BYTES_PER_SAMPLE).toShort()) // BlockAlign
        buffer.putShort(BITS_PER_SAMPLE.toShort())

        // data chunk
        buffer.put("data".toByteArray())
        buffer.putInt(totalAudioLen.toInt())

        out.write(header, 0, 44)
    }

    /**
     * Updates header sizes in an existing WAV file once recording has finished.
     */
    fun updateWavHeader(wavFile: File) {
        if (!wavFile.exists()) return
        val totalAudioLen = wavFile.length() - 44
        if (totalAudioLen < 0) return
        val totalDataLen = totalAudioLen + 36

        RandomAccessFile(wavFile, "rw").use { raf ->
            raf.seek(4)
            raf.write(intToByteArray(totalDataLen.toInt()))
            raf.seek(40)
            raf.write(intToByteArray(totalAudioLen.toInt()))
        }
    }

    private fun intToByteArray(value: Int): ByteArray {
        return byteArrayOf(
            (value and 0xff).toByte(),
            ((value shr 8) and 0xff).toByte(),
            ((value shr 16) and 0xff).toByte(),
            ((value shr 24) and 0xff).toByte()
        )
    }

    /**
     * Reads 16-bit PCM WAV and returns normalized float array [-1.0f, 1.0f]
     */
    fun readWavToFloatArray(wavFile: File): FloatArray {
        val bytes = wavFile.readBytes()
        if (bytes.size < 44) return FloatArray(0)

        val pcmBytes = bytes.copyOfRange(44, bytes.size)
        val sampleCount = pcmBytes.size / 2
        val floatSamples = FloatArray(sampleCount)

        val buffer = ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until sampleCount) {
            val shortVal = buffer.short
            floatSamples[i] = shortVal / 32768.0f
        }
        return floatSamples
    }
}
