package com.example.cardpuzzleapp

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt-модуль для предоставления (providing) сервисов,
 * которые требуют ApplicationContext.
 */
@Module
@InstallIn(SingletonComponent::class)
object TtsModule {

    /**
     * Эта функция сообщает Hilt, как создать экземпляр TtsPlayer.
     * Hilt автоматически предоставит @ApplicationContext.
     */
    @Provides
    @Singleton
    fun provideTtsPlayer(@ApplicationContext context: Context): TtsPlayer {
        return TtsPlayer(context)
    }
}