package com.example.cardpuzzleapp

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Сервис, управляющий Text-To-Speech (TTS) движком Android.
 * Отвечает за инициализацию, установку языка (Иврит) и воспроизведение.
 */
@Singleton
class TtsPlayer @Inject constructor(
    @ApplicationContext private val context: Context
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private val targetLocale = Locale("he") // Указываем Иврит

    init {
        try {
            Log.d(AppDebug.TAG, "TtsPlayer: Инициализация TextToSpeech...")
            tts = TextToSpeech(context, this)
        } catch (e: Exception) {
            Log.e(AppDebug.TAG, "TtsPlayer: Крэш при инициализации TTS. $e")
        }
    }

    /**
     * Вызывается, когда TTS-движок готов к работе.
     */
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(targetLocale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e(AppDebug.TAG, "TtsPlayer: ОШИБКА. Язык (Иврит) не поддерживается или отсутствуют данные.")
                isInitialized = false
            } else {
                Log.d(AppDebug.TAG, "TtsPlayer: Успешно инициализирован. Язык: Иврит.")
                isInitialized = true
            }
        } else {
            Log.e(AppDebug.TAG, "TtsPlayer: ОШИБКА. Не удалось инициализировать TTS-движок.")
            isInitialized = false
        }
    }

    /**
     * Воспроизводит указанный текст на иврите.
     */
    fun speak(text: String) {
        if (!isInitialized || tts == null) {
            Log.w(AppDebug.TAG, "TtsPlayer: Попытка воспроизвести '$text', но TTS не готов.")
            return
        }

        // QUEUE_FLUSH: Прерывает текущее воспроизведение и ставит новое в очередь
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    /**
     * Должен вызываться при закрытии приложения (например, в MainActivity.onDestroy),
     * чтобы освободить ресурсы.
     */
    fun shutdown() {
        Log.d(AppDebug.TAG, "TtsPlayer: Выключение TTS-сервиса...")
        isInitialized = false
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}