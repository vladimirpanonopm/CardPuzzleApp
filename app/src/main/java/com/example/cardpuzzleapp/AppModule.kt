package com.example.cardpuzzleapp

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideGameProgressManager(@ApplicationContext context: Context): GameProgressManager {
        return GameProgressManager(context)
    }

    @Provides
    @Singleton
    fun provideAudioPlayer(@ApplicationContext context: Context): AudioPlayer {
        return AudioPlayer(context)
    }

    // --- ДОБАВЛЕНО ---
    @Provides
    @Singleton
    fun provideLevelRepository(@ApplicationContext context: Context): LevelRepository {
        // Теперь LevelRepository - это класс, которому мы сами передаем Context
        return LevelRepository(context)
    }
    // -----------------
}