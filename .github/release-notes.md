## VoiceFlow AI v1.0.1 — Bug Fix Release

This release fixes three bugs found in a code review of v1.0.0. No new features — the app now behaves the way v1.0.0 was supposed to.

### 🐛 Bug fixes

1. **Selected Whisper model was ignored after restart**
   The app always tried to load Base → Small → Tiny in a fixed order, so the model you selected in the Model Manager (for example Small Q5_1, 182 MB) was silently replaced by Base (142 MB) after a restart. The app now loads your selected model first.

2. **CPU Threads setting did nothing**
   The Settings slider (1–8 threads, default 4) was saved but never used — transcription always ran on a hardcoded 2 threads. It now uses your saved setting, so Threads = 4 actually speeds up transcription on Snapdragon 695-class devices.

3. **Hinglish output and Groq routing**
   - Hinglish mode forced Whisper to output Devanagari, which was then transliterated back to Roman — a lossy round-trip that hurt accuracy. Hinglish now produces Roman script directly.
   - On fresh installs, **Groq Fast Whisper** is now the default online provider (previously Gemini, so ONLINE mode ignored your Groq key). Provider selection also prefers a provider that has an API key saved, instead of failing on a default provider with a blank key.
   - Note for existing users: your saved default provider is kept. If needed, tap **Set Default** on Groq in Provider Manager once.

### 📲 How to install

Download **VoiceFlowAI-v1.0.1-release.apk** below and install it **over your existing app** (as an update). Do **not** uninstall first — downloaded Whisper models live in the app's internal storage and would be deleted with an uninstall.

### ✅ Build info

- Built by GitHub Actions (JDK 17, Android SDK 35, NDK 28.2.13676358, CMake 3.31.6)
- Unit tests: passed
- Changes: `WhisperRepositoryImpl.kt`, `TranscriptionUseCases.kt`, `AiProviderRepositoryImpl.kt` (61 insertions, 17 deletions)
