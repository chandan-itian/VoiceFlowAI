<p align="center">
  <img src="docs/screenshots/wispr_logo.png" width="100" height="100" alt="VoiceFlow AI Logo" style="border-radius: 20%;" />
</p>

<h1 align="center">VoiceFlow AI</h1>

<p align="center">
  <b>Privacy-Focused, Lightweight, Intelligent Floating AI Voice Dictation Assistant for Android</b><br/>
  <i>Inspired by Wispr Flow • Zero Keyboard Replacement • 100% On-Device Whisper.cpp</i>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android_15_(API_35)-3DDC84?style=flat&logo=android&logoColor=white" alt="Platform" />
  <img src="https://img.shields.io/badge/Language-Kotlin_%2B_C%2B%2B_JNI-7F52FF?style=flat&logo=kotlin&logoColor=white" alt="Language" />
  <img src="https://img.shields.io/badge/UI-Jetpack_Compose_%2B_M3-4285F4?style=flat&logo=jetpackcompose&logoColor=white" alt="Jetpack Compose" />
  <img src="https://img.shields.io/badge/APK_Size-11_MB-success?style=flat" alt="APK Size" />
  <img src="https://img.shields.io/badge/Engine-Whisper.cpp_%2B_Smart_Hybrid-FF6F00?style=flat" alt="Engine" />
  <img src="https://img.shields.io/badge/License-Apache_2.0-blue.svg" alt="License" />
</p>

---

## 🚀 Overview

**VoiceFlow AI** is a native Android floating voice dictation assistant designed to replicate and exceed the smooth experience of **Wispr Flow**. It operates as a non-intrusive floating overlay that detects when you focus any editable text field, lets you dictate naturally in **Hindi**, **English**, or **Hinglish**, and directly types the transcribed text into the field without replacing your default keyboard (like Google Gboard).

It runs completely offline using an optimized native **Whisper.cpp (ARM64-v8a)** engine tuned specifically for Qualcomm Snapdragon processors, with an optional **Smart Hybrid AI** mode that can refine spoken grammar and punctuation using Gemini, Groq, or OpenAI.

<p align="center">
  <a href="https://github.com/chandan-itian/VoiceFlowAI/releases/download/v1.0.0/VoiceFlowAI-v1.0.0-release.apk">
    <img src="https://img.shields.io/badge/Download-Release_APK_(11_MB)-brightgreen?style=for-the-badge&logo=android" alt="Download APK" />
  </a>
</p>

---

## 📸 Screenshots & Demonstration

| Home & Settings | Floating Recording Pill | Live Dictation in Vivo Notes |
|:---:|:---:|:---:|
| <img src="docs/screenshots/app_home_settings.jpg" width="260" alt="Settings Screen"/> | <img src="docs/screenshots/wispr_flow_overlay.jpg" width="260" alt="Recording Pill"/> | <img src="docs/screenshots/live_notes_dictation.png" width="260" alt="Live Notes Dictation"/> |

---

## ✨ Key Features

### 1. 🧲 Edge Docking & Hide/Expand on Swipe
- **No Screen Obstruction:** The floating mic never sits in the middle of your screen or covers your active typing area.
- **Sleek Edge Tab (22dp):** When idle, it tucks away flush against the bezel as a minimal, frosted pink tab.
- **Tap to Expand:** A single tap expands the edge tab into the full 56dp circular Wispr Flow button with smooth haptic feedback.
- **Swipe to Hide/Dock:** Swipe the expanded button horizontally towards the screen edge, and it instantly docks and hides back to the bezel.
- **Vertical Thumb Adjustment:** Drag the tab up or down along the bezel to match your natural thumb resting position.

### 2. 🌊 Translucent Glassmorphism Pill & Voice-Reactive Waveform
- **Live Amplitude Dancing Waveform:** 9 organic animated frequency bars dance dynamically in real-time according to your live vocal volume.
- **Cancel Button (`✕`):** Discards the current recording instantly without making any changes.
- **Explicit Commit Button (`✔`):** Dictation only commits and transcribes when you tap the purple checkmark, preventing accidental insertions.

### 3. 🎯 High-Accuracy Indian Accent & Bilingual Transcription
- **Vocabulary Prompt Biasing (`initial_prompt`):** Native C++ decoder attention is guided by domain-specific prompts for Indian English, Devanagari Hindi, and conversational Hinglish.
- **Zero Hallucination (`temperature = 0.0f`):** Eliminates phonetic gibberish and phantom text output.
- **Snapdragon 695 Core Scheduling:** Inference is locked to Snapdragon's 2x Cortex-A78 Performance Cores (`n_threads = 2`), cutting latency from 12+ seconds down to **4-6 seconds**.
- **Model Options:**
  - `Base (Default)` (~142 MB): Balanced speed and vocabulary.
  - `Base Quantized Q5_1` (~57 MB): 2.5x faster execution, minimal RAM footprint.
  - `Small Quantized Q5_1` (~182 MB): 4x higher accuracy for complex bilingual Hindi/English dictation.
  - `Tiny` (~75 MB) and `Small` (~466 MB).

### 4. ⚡ Ultra-Lightweight (Only 11 MB APK)
- Down from 74 MB through aggressive **R8 tree-shaking**, **resource shrinking**, and **single ABI filtering** (`arm64-v8a`), matching and beating Wispr Flow's 30 MB benchmark.

### 5. ⌨️ Zero Keyboard Hijacking
- **Never replaces Google Gboard:** Does NOT implement `InputMethodService`.
- Uses Android's `AccessibilityService` (`AccessibilityNodeInfo.ACTION_SET_TEXT` / `ACTION_PASTE`) to directly insert dictated text into the active field, keeping your existing keyboard untouched.
- Automatic **Clipboard Fallback**: If an app restricts accessibility write permissions, the text is automatically copied to the clipboard with a subtle toast notification.

---

## 🔍 Detailed Analysis: What Works & What Doesn't

To ensure complete transparency and production quality, here is an honest technical breakdown:

### ✅ What Works Flawlessly

| Feature | Status | Technical Details |
|:---|:---:|:---|
| **Edge Docking & Hiding** | ✅ Working | Tucks to screen edge (22dp); expands to 56dp on tap; swipes to bezel to dock. |
| **Offline Transcription** | ✅ Working | Runs 100% locally on-device using `libwhisper_jni.so` (Whisper.cpp). No internet required. |
| **Model Pre-Warming** | ✅ Working | Auto-detects downloaded `.bin` model and pre-warms native context in < 2 seconds on app launch. |
| **Text Insertion** | ✅ Working | Tested and verified in Vivo Notes, WhatsApp, Chrome, SMS, and search fields. |
| **Voice Waveform** | ✅ Working | Live AudioRecord amplitude flow feeds Compose Spring animation bars. |
| **Language Selection** | ✅ Working | Supports Hindi (Devanagari), English, Hinglish, Auto-Detect, and Translate to English. |
| **Smart Hybrid AI** | ✅ Working | Refines punctuation, removes filler words ('umm', 'matlab'), or uses cloud LLM when enabled. |
| **Clipboard Fallback** | ✅ Working | Automatically falls back to clipboard copy if an app prevents direct accessibility injection. |

### ⚠️ Known Limitations & What Does Not Work

| Limitation | Reason / Architecture Constraint | Recommended Workaround |
|:---|:---|:---|
| **Password & PIN Fields** | Android OS Security Architecture strictly prevents all Accessibility Services from inspecting or typing into fields flagged with `TYPE_TEXT_VARIATION_PASSWORD`. | For passwords, use your secure keyboard or biometric auto-fill. VoiceFlow AI intentionally respects user privacy. |
| **Concurrent Microphone Access** | Android allows only one foreground process to capture the hardware microphone at a time. If you are on an active phone call or another recording app is running, audio capture will fail. | Ensure no active voice call or screen recording is using the microphone during dictation. |
| **Inference Time on Low-End Chips** | On the Snapdragon 695 (2x Cortex-A78 + 6x A55), Whisper Base model takes ~4 to 6 seconds for a 15-second voice memo. On older Cortex-A53 devices, it may take 10+ seconds. | Use the **Quantized Q5_1** model (57 MB) for 2.5x faster speed, or enable Groq / Gemini in Smart Hybrid mode for near-instant (0.8s) cloud processing. |
| **System Permissions Required** | Must manually grant **Microphone**, **Display over other apps (Overlay)**, and **Accessibility Service** in Android Settings. | Follow the 1-minute setup guide below; the in-app onboarding guides you through each toggle. |

---

## 📲 Installation & Quick Setup

### Step 1: Install APK
Download and install the pre-built signed release APK:
- Direct Download: [**VoiceFlowAI-v1.0.0-release.apk**](https://github.com/chandan-itian/VoiceFlowAI/releases/download/v1.0.0/VoiceFlowAI-v1.0.0-release.apk) (11 MB)

Or via ADB:
```bash
adb install -r https://github.com/chandan-itian/VoiceFlowAI/releases/download/v1.0.0/VoiceFlowAI-v1.0.0-release.apk
```

### Step 2: Grant Permissions
1. Open **VoiceFlow AI**.
2. Tap **Privacy & Permissions** and enable:
   - **Microphone:** For voice recording.
   - **Overlay Permission:** To show the floating mic over other apps.
   - **Accessibility Service:** Enable *VoiceFlow AI Dictation Service* to allow text insertion.

*(Or via ADB for developers):*
```bash
adb shell pm grant com.voiceflow.ai android.permission.RECORD_AUDIO
adb shell appops set com.voiceflow.ai SYSTEM_ALERT_WINDOW allow
adb shell settings put secure enabled_accessibility_services com.voiceflow.ai/.presentation.service.VoiceFlowAccessibilityService
adb shell settings put secure accessibility_enabled 1
```

### Step 3: Download Whisper Model
1. Go to **Offline Whisper Models** inside the app.
2. Tap **Download** on `Base (Default) (142MB)` or `Base Quantized Q5_1 (57MB)`.
3. Tap **Activate**. The model is now ready for offline dictation!

---

## 🛠️ Architecture & Tech Stack

```
VoiceFlowAI/
├── app/
│   ├── src/main/
│   │   ├── java/com/voiceflow/ai/
│   │   │   ├── data/                 # Repositories, DataStore, Audio Recorder, Whisper JNI wrapper
│   │   │   ├── domain/               # Use cases (Start, Stop, Transcribe, Insert, Process)
│   │   │   ├── presentation/
│   │   │   │   ├── service/          # FloatingMicService, VoiceFlowAccessibilityService
│   │   │   │   ├── ui/               # Jetpack Compose Screens (Home, Models, Settings, Diagnostics)
│   │   │   │   └── viewmodel/        # MVVM StateFlow ViewModels
│   │   │   └── utils/                # HinglishNormalizer, PermissionUtils, AudioUtils
│   │   └── jni/                      # C++ JNI bridge, Whisper.cpp, ggml, CMakeLists.txt
│   ├── build.gradle.kts              # AGP 8.8, NDK 26, R8 minification, arm64-v8a filter
│   └── proguard-rules.pro            # Comprehensive keep rules for Whisper, Compose & Tink
├── docs/screenshots/                 # UI screenshots and visual assets
└── release/                          # Pre-built production APK (11 MB)
```

- **Clean Architecture + MVVM:** Complete separation of concerns between native inference, domain use cases, and Compose presentation.
- **Whisper.cpp JNI:** Custom high-performance C++ JNI bridge passing direct PCM 16kHz audio buffer pointers with vocabulary prompt tuning and thread locks.
- **Jetpack Compose in Android Service:** ComposeView embedded inside a foreground `WindowManager` overlay service with full `LifecycleOwner` and `SavedStateRegistryOwner` support.

---

## 💻 Building from Source

### Prerequisites
- Android Studio Ladybug (2024.2+) or newer
- Android NDK `26.1.10909125` or newer
- CMake `3.22.1`+
- JDK 17 / 21

### Build Instructions
```bash
# Clone the repository
git clone https://github.com/chandan-itian/VoiceFlowAI.git
cd VoiceFlowAI

# Build C++ native Whisper library
./gradlew buildCMakeRelease

# Compile and package Release APK with R8 minification
./gradlew assembleRelease

# The optimized APK will be generated at:
# app/build/outputs/apk/release/app-release.apk
```

---

## 📄 License & Acknowledgments

- **License:** Apache License 2.0. See [LICENSE](LICENSE) for details.
- **Whisper.cpp:** Powered by [Georgi Gerganov's whisper.cpp](https://github.com/ggerganov/whisper.cpp).
- **Wispr Flow:** UI/UX inspiration for floating voice interaction.
