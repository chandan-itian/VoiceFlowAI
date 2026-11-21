## VoiceFlow AI v1.0.2 — Hinglish Model + Insertion Fix

### ✨ New: Hinglish Q5_1 offline model

The Model Manager now includes **Hinglish Quantized Q5_1 (Best for Hinglish)** — a GGML Q5_1 conversion (~57 MB) of Oriserve's **Whisper-Hindi2Hinglish-Swift**, a Whisper Base fine-tune trained specifically on Hindi/Hinglish speech.

- Outputs **Roman Hinglish directly** (the way people type it), no transliteration step
- Runs at **Whisper Base speed** — much faster than Small Q5_1 on Snapdragon 695-class chips
- On Hinglish benchmarks it scores far better than stock Whisper (Common Voice Hinglish WER 38.7 vs 61.9 for stock Whisper Large V3)
- Download it in **Model Manager**, activate it, and use it with **Hinglish** language mode
- Model credit: Oriserve (weights, Apache-2.0); GGML conversion by rohitag13

### 🐛 Bug fix: field placeholder was prepended to your dictation

When you dictated into an empty text field, the field's placeholder text (for example **"Message"** in WhatsApp) was treated as existing text and prepended to your transcription, producing results like *"Message hello hello..."*. The insertion engine now detects hint/placeholder text (`isShowingHintText`) and treats an empty field as empty.

### 📲 How to install

Download the APK below and install it **over your existing app** (as an update). Do **not** uninstall first — downloaded Whisper models live in the app's internal storage and would be deleted with an uninstall.

### ✅ Build info

- Built by GitHub Actions (JDK 17, Android SDK 35, NDK 28.2.13676358, CMake 3.31.6)
- Changes: `Models.kt` (new Hinglish model), `TextInsertionEngine.kt` + `VoiceFlowAccessibilityService.kt` (hint fix)
