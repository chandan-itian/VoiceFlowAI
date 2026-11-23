package com.voiceflow.ai.data.remote.providers

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.io.RandomAccessFile

/** Encodes the app recorder's 16 kHz mono PCM WAV using the platform AAC codec. */
internal object M4aUpload {
    private const val RATE = 16_000
    private const val BIT_RATE = 24_000
    private const val TIMEOUT_US = 10_000L

    fun encode(wav: File, output: File) {
        RandomAccessFile(wav, "r").use { pcm ->
            require(pcm.length() > 44)
            pcm.seek(44)
            val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, RATE, 1).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
            }
            val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            try {
                codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                codec.start()
                val muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                try {
                    drain(pcm, codec, muxer)
                    muxer.stop()
                } finally {
                    muxer.release()
                }
            } finally {
                codec.release()
            }
        }
    }

    private fun drain(pcm: RandomAccessFile, codec: MediaCodec, muxer: MediaMuxer) {
        val info = MediaCodec.BufferInfo()
        val buffer = ByteArray(8192)
        var sentEos = false
        var track = -1
        var lastProgress = System.nanoTime()
        while (true) {
            if (!sentEos) {
                val index = codec.dequeueInputBuffer(TIMEOUT_US)
                if (index >= 0) {
                    val input = requireNotNull(codec.getInputBuffer(index))
                    input.clear()
                    val timestamp = (pcm.filePointer - 44) / 2 * 1_000_000L / RATE
                    val count = pcm.read(buffer, 0, minOf(input.remaining(), buffer.size) and -2)
                    sentEos = count == -1
                    if (!sentEos) input.put(buffer, 0, count)
                    codec.queueInputBuffer(index, 0, maxOf(0, count), timestamp,
                        if (sentEos) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0)
                    lastProgress = System.nanoTime()
                }
            }
            val index = codec.dequeueOutputBuffer(info, if (sentEos) TIMEOUT_US else 0L)
            if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                track = muxer.addTrack(codec.outputFormat)
                muxer.start()
            } else if (index >= 0) {
                if (info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                    muxer.writeSampleData(track, requireNotNull(codec.getOutputBuffer(index)), info)
                }
                codec.releaseOutputBuffer(index, false)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                lastProgress = System.nanoTime()
            }
            check(System.nanoTime() - lastProgress < 5_000_000_000L) { "AAC encoder stalled" }
        }
    }
}
