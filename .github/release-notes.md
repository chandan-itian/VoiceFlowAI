## VoiceFlow AI v1.0.3 — Stronger Placeholder Fix + Footer

### 🐛 Bug fix (stronger): WhatsApp "Message" placeholder still prepended

v1.0.2 checked `isShowingHintText`, but WhatsApp does not always set that flag, so its **"Message"** placeholder could still be treated as existing text and prepended to your dictation. The insertion engine now uses a stronger check in all three places that read field text:

- Text that exactly matches the field's **hint** (`hintText`) is also treated as empty — unless the cursor sits inside it, which means you actually typed it
- The cached field text gets the same hint check, so a stored placeholder can no longer slip back in as a prefix

### ✨ New: footer

The Home screen now ends with a footer: **Made with ❤️ by Chandan Singh**

### 📲 How to install

Download the APK below and install it **over your existing app** (as an update). Do **not** uninstall first — downloaded Whisper models live in the app's internal storage and would be deleted with an uninstall. After updating, toggle the VoiceFlow Accessibility Service off and on once so it restarts with the new code.

### ✅ Build info

- Built by GitHub Actions (JDK 17, Android SDK 35, NDK 28.2.13676358, CMake 3.31.6)
- Changes: `TextInsertionEngine.kt` + `VoiceFlowAccessibilityService.kt` (stronger hint fix), `HomeScreen.kt` (footer)
