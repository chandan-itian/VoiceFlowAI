package com.voiceflow.ai.di

import android.content.Context
import com.voiceflow.ai.data.local.AudioRecorderImpl
import com.voiceflow.ai.data.local.SecureStorageImpl
import com.voiceflow.ai.data.local.TextInsertionEngine
import com.voiceflow.ai.data.local.whisper.WhisperJni
import com.voiceflow.ai.data.local.whisper.WhisperRepositoryImpl
import com.voiceflow.ai.data.preferences.SettingsDataStore
import com.voiceflow.ai.data.remote.AiProviderRepositoryImpl
import com.voiceflow.ai.domain.repository.*
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        prettyPrint = false
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        return OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .build()
    }

    @Provides
    @Singleton
    fun provideWhisperJni(): WhisperJni = WhisperJni()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(
        impl: SettingsDataStore
    ): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindWhisperRepository(
        impl: WhisperRepositoryImpl
    ): WhisperRepository

    @Binds
    @Singleton
    abstract fun bindAudioRepository(
        impl: AudioRecorderImpl
    ): AudioRepository

    @Binds
    @Singleton
    abstract fun bindAiProviderRepository(
        impl: AiProviderRepositoryImpl
    ): AiProviderRepository

    @Binds
    @Singleton
    abstract fun bindTextInsertionRepository(
        impl: TextInsertionEngine
    ): TextInsertionRepository

    @Binds
    @Singleton
    abstract fun bindSecureStorageRepository(
        impl: SecureStorageImpl
    ): SecureStorageRepository
}
