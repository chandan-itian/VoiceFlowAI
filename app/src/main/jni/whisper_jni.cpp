#include <jni.h>
#include <android/log.h>
#include <string>
#include <vector>
#include <algorithm>
#include <memory>
#include <fstream>
#include <cstring>
#include <cstdint>
#include "whisper.h"

#define TAG "WhisperJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static bool read_wav_file(const std::string &fname, std::vector<float> &pcmf32, std::vector<std::vector<float>> &pcmf32s, bool stereo) {
    std::ifstream wav_file(fname, std::ios::binary);
    if (!wav_file.is_open()) {
        LOGE("Failed to open WAV file: %s", fname.c_str());
        return false;
    }

    // RIFF chunk
    char riff[4];
    wav_file.read(riff, 4);
    if (std::memcmp(riff, "RIFF", 4) != 0) {
        LOGE("WAV file is missing RIFF header");
        return false;
    }

    uint32_t file_size;
    wav_file.read(reinterpret_cast<char *>(&file_size), 4);

    char wave[4];
    wav_file.read(wave, 4);
    if (std::memcmp(wave, "WAVE", 4) != 0) {
        LOGE("WAV file is missing WAVE header");
        return false;
    }

    // Read chunks until "data"
    uint16_t channels = 1;
    uint32_t sample_rate = 16000;
    uint16_t bits_per_sample = 16;
    uint32_t data_size = 0;

    while (wav_file.good()) {
        char chunk_id[4];
        wav_file.read(chunk_id, 4);
        if (wav_file.gcount() < 4) break;

        uint32_t chunk_size = 0;
        wav_file.read(reinterpret_cast<char *>(&chunk_size), 4);
        if (wav_file.gcount() < 4) break;

        if (std::memcmp(chunk_id, "fmt ", 4) == 0) {
            uint16_t audio_format = 0;
            wav_file.read(reinterpret_cast<char *>(&audio_format), 2);
            wav_file.read(reinterpret_cast<char *>(&channels), 2);
            wav_file.read(reinterpret_cast<char *>(&sample_rate), 4);

            uint32_t byte_rate = 0;
            wav_file.read(reinterpret_cast<char *>(&byte_rate), 4);
            uint16_t block_align = 0;
            wav_file.read(reinterpret_cast<char *>(&block_align), 2);
            wav_file.read(reinterpret_cast<char *>(&bits_per_sample), 2);

            // Skip any extra format bytes
            if (chunk_size > 16) {
                wav_file.seekg(chunk_size - 16, std::ios::cur);
            }
        } else if (std::memcmp(chunk_id, "data", 4) == 0) {
            data_size = chunk_size;
            break;
        } else {
            // Skip unknown chunk
            wav_file.seekg(chunk_size, std::ios::cur);
        }
    }

    if (data_size == 0) {
        LOGE("WAV file contains no data chunk");
        return false;
    }

    if (sample_rate != 16000) {
        LOGE("WAV sample rate %u is not 16000 Hz", sample_rate);
    }

    if (bits_per_sample != 16) {
        LOGE("WAV bits per sample %u is not 16", bits_per_sample);
        return false;
    }

    const size_t total_samples = data_size / sizeof(int16_t);
    std::vector<int16_t> pcm16(total_samples);
    wav_file.read(reinterpret_cast<char *>(pcm16.data()), data_size);

    if (channels == 1) {
        pcmf32.resize(total_samples);
        for (size_t i = 0; i < total_samples; ++i) {
            pcmf32[i] = static_cast<float>(pcm16[i]) / 32768.0f;
        }
    } else {
        // Multi-channel downmix to mono
        const size_t samples_per_channel = total_samples / channels;
        pcmf32.resize(samples_per_channel);
        for (size_t i = 0; i < samples_per_channel; ++i) {
            float sum = 0.0f;
            for (size_t c = 0; c < channels; ++c) {
                sum += static_cast<float>(pcm16[i * channels + c]) / 32768.0f;
            }
            pcmf32[i] = sum / static_cast<float>(channels);
        }
    }

    return true;
}

extern "C" {

// Input is the recorder's canonical 44-byte-header, 16 kHz mono PCM WAV.
JNIEXPORT jlongArray JNICALL
Java_com_voiceflow_ai_data_local_whisper_WhisperJni_speechRanges(
        JNIEnv *env, jobject, jstring model_path, jstring wav_path) {
    const char *path = env->GetStringUTFChars(wav_path, nullptr);
    std::ifstream wav(path, std::ios::binary);
    env->ReleaseStringUTFChars(wav_path, path);
    if (!wav.seekg(44)) return nullptr;

    auto params = whisper_vad_default_context_params();
    params.use_gpu = false;
    params.n_threads = 1;
    const char *model = env->GetStringUTFChars(model_path, nullptr);
    std::unique_ptr<whisper_vad_context, decltype(&whisper_vad_free)> ctx(
        whisper_vad_init_from_file_with_params(model, params), whisper_vad_free);
    env->ReleaseStringUTFChars(model_path, model);
    if (!ctx) return nullptr;
    whisper_vad_reset_state(ctx.get());

    constexpr int frame = 512;
    constexpr int batch = 32 * frame;
    constexpr int padding = 3200; // 200 ms either side protects quiet syllables.
    std::vector<int16_t> pcm(batch);
    std::vector<float> samples(batch);
    std::vector<jlong> ranges;
    jlong offset = 0;
    while (wav) {
        wav.read(reinterpret_cast<char *>(pcm.data()), batch * sizeof(int16_t));
        const int count = static_cast<int>(wav.gcount() / sizeof(int16_t));
        if (!count) break;
        for (int i = 0; i < count; ++i) samples[i] = pcm[i] / 32768.0f;
        if (!whisper_vad_detect_speech_no_reset(ctx.get(), samples.data(), count)) return nullptr;
        const auto probs = whisper_vad_probs(ctx.get());
        for (int i = 0; i < whisper_vad_n_probs(ctx.get()); ++i) {
            if (probs[i] < 0.45f) continue;
            const jlong start = std::max<jlong>(0, offset + i * frame - padding);
            const jlong end = offset + (i + 1) * frame + padding;
            if (ranges.empty() || ranges.back() < start) {
                ranges.push_back(start);
                ranges.push_back(end);
            } else {
                ranges.back() = end;
            }
        }
        offset += count;
    }
    if (wav.bad()) return nullptr;
    if (!ranges.empty()) ranges.back() = std::min(ranges.back(), offset);
    auto result = env->NewLongArray(static_cast<jsize>(ranges.size()));
    if (result && !ranges.empty()) env->SetLongArrayRegion(result, 0, ranges.size(), ranges.data());
    return result;
}

JNIEXPORT jlong JNICALL
Java_com_voiceflow_ai_data_local_whisper_WhisperJni_initContext(
        JNIEnv *env,
        jobject /* thiz */,
        jstring model_path) {
    if (model_path == nullptr) {
        LOGE("initContext: model_path is null");
        return 0;
    }

    const char *native_model_path = env->GetStringUTFChars(model_path, nullptr);
    LOGI("Loading Whisper model from: %s", native_model_path);

    struct whisper_context_params cparams = whisper_context_default_params();
    cparams.use_gpu = false; // CPU inference optimized for Snapdragon 695

    struct whisper_context *ctx = whisper_init_from_file_with_params(native_model_path, cparams);
    env->ReleaseStringUTFChars(model_path, native_model_path);

    if (ctx == nullptr) {
        LOGE("Failed to initialize Whisper context from model");
        return 0;
    }

    LOGI("Whisper context initialized successfully: %p", ctx);
    return reinterpret_cast<jlong>(ctx);
}

JNIEXPORT jstring JNICALL
Java_com_voiceflow_ai_data_local_whisper_WhisperJni_transcribe(
        JNIEnv *env,
        jobject /* thiz */,
        jlong context_ptr,
        jstring audio_path,
        jstring language,
        jint threads,
        jstring initial_prompt) {
    if (context_ptr == 0) {
        LOGE("transcribe: context_ptr is null");
        return env->NewStringUTF("");
    }

    auto *ctx = reinterpret_cast<struct whisper_context *>(context_ptr);

    const char *native_audio_path = env->GetStringUTFChars(audio_path, nullptr);
    const char *native_language = language != nullptr ? env->GetStringUTFChars(language, nullptr) : nullptr;
    const char *native_prompt = initial_prompt != nullptr ? env->GetStringUTFChars(initial_prompt, nullptr) : nullptr;

    std::vector<float> pcmf32;
    std::vector<std::vector<float>> pcmf32s;
    if (!read_wav_file(native_audio_path, pcmf32, pcmf32s, false)) {
        LOGE("Failed to read audio file: %s", native_audio_path);
        env->ReleaseStringUTFChars(audio_path, native_audio_path);
        if (native_language) env->ReleaseStringUTFChars(language, native_language);
        if (native_prompt) env->ReleaseStringUTFChars(initial_prompt, native_prompt);
        return env->NewStringUTF("");
    }

    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    // 2-3 threads pinned to Cortex-A78 Big cores on Snapdragon 695 eliminates CPU barrier stalls
    params.n_threads = (threads > 0) ? threads : 2;
    params.temperature = 0.0f;
    params.temperature_inc = 0.0f;
    params.print_special = false;
    params.print_progress = false;
    params.print_realtime = false;
    params.print_timestamps = false;
    params.no_context = true;
    params.single_segment = false;
    params.detect_language = false;
    params.suppress_blank = true;

    if (native_prompt != nullptr && strlen(native_prompt) > 0) {
        params.initial_prompt = native_prompt;
        LOGI("Whisper initial_prompt set: '%s'", native_prompt);
    }

    if (native_language != nullptr && strlen(native_language) > 0 && strcmp(native_language, "auto") != 0) {
        if (strcmp(native_language, "translate_to_en") == 0) {
            params.language = "auto";
            params.translate = true;
        } else {
            params.language = native_language;
            params.translate = false;
        }
    } else {
        params.language = "auto";
        params.translate = false;
    }

    LOGI("Starting Whisper transcription with %d threads, %zu samples, lang=%s, translate=%d",
         params.n_threads, pcmf32.size(), params.language, params.translate ? 1 : 0);

    int ret = whisper_full(ctx, params, pcmf32.data(), pcmf32.size());

    env->ReleaseStringUTFChars(audio_path, native_audio_path);
    if (native_language) env->ReleaseStringUTFChars(language, native_language);
    if (native_prompt) env->ReleaseStringUTFChars(initial_prompt, native_prompt);

    if (ret != 0) {
        LOGE("whisper_full failed with return code %d", ret);
        return env->NewStringUTF("");
    }

    const int n_segments = whisper_full_n_segments(ctx);
    std::string result_text;

    for (int i = 0; i < n_segments; ++i) {
        const char *text = whisper_full_get_segment_text(ctx, i);
        if (text != nullptr) {
            LOGI("Whisper segment %d: '%s'", i, text);
            result_text += text;
        }
    }

    LOGI("Transcription completed (%d segments, length %zu): '%s'",
         n_segments, result_text.length(), result_text.c_str());

    return env->NewStringUTF(result_text.c_str());
}

JNIEXPORT void JNICALL
Java_com_voiceflow_ai_data_local_whisper_WhisperJni_freeContext(
        JNIEnv * /* env */,
        jobject /* thiz */,
        jlong context_ptr) {
    if (context_ptr != 0) {
        auto *ctx = reinterpret_cast<struct whisper_context *>(context_ptr);
        LOGI("Freeing Whisper context: %p", ctx);
        whisper_free(ctx);
    }
}

JNIEXPORT jboolean JNICALL
Java_com_voiceflow_ai_data_local_whisper_WhisperJni_isContextValid(
        JNIEnv * /* env */,
        jobject /* thiz */,
        jlong context_ptr) {
    return context_ptr != 0 ? JNI_TRUE : JNI_FALSE;
}

} // extern "C"
