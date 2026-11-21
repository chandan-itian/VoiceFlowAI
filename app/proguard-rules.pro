# Add project specific ProGuard rules here.

# Keep VoiceFlow AI app code & reflection
-keep class com.voiceflow.ai.** { *; }
-keepclassmembers class com.voiceflow.ai.** { *; }
-dontwarn com.voiceflow.ai.**

# Keep Whisper JNI
-keep class com.voiceflow.ai.data.local.whisper.WhisperJni { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep serialization
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keep class kotlinx.serialization.** { *; }
-keepclassmembers class * {
    @kotlinx.serialization.Serializable *;
}

# Keep Hilt & Dagger
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# Keep Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Keep Lifecycle and ViewModel
-keep class androidx.lifecycle.** { *; }

# Keep DataStore
-keep class androidx.datastore.** { *; }

# OkHttp & Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# Google errorprone & Tink warnings
-dontwarn com.google.errorprone.annotations.**
-dontwarn com.google.crypto.tink.**
-keep class com.google.crypto.tink.** { *; }
