package com.example.cardpuzzleapp

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Сервис, управляющий Text-To-Speech (TTS) движком Android.
 * Отвечает за инициализацию, установку языка (Иврит) и воспроизведение.
 *
 * --- ИЗМЕНЕНО ---
 * Добавлена поддержка UtteranceProgressListener для suspend-функций.
 */
@Singleton
class TtsPlayer @Inject constructor(
    @ApplicationContext private val context: Context
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private val targetLocale = Locale("he") // Указываем Иврит

    // --- ИЗМЕНЕНИЕ 1: Очередь для coroutines, ожидающих завершения речи ---
    private var continuationQueue = ArrayDeque<CancellableContinuation<Unit>>()

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

                // --- ИЗМЕНЕНИЕ 2: Устанавливаем Progress Listener ---
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        // Не используется
                    }

                    override fun onDone(utteranceId: String?) {
                        // Речь завершена, возобновляем coroutine, если она ждет
                        if (continuationQueue.isNotEmpty()) {
                            continuationQueue.removeFirst().resume(Unit)
                        }
                    }

                    // --- ИЗМЕНЕНИЕ: Добавлена аннотация ---
                    @Deprecated("Deprecated in Java")
                    // --- КОНЕЦ ---
                    override fun onError(utteranceId: String?) {
                        Log.e(AppDebug.TAG, "TtsPlayer: Ошибка воспроизведения $utteranceId")
                        // Возобновляем, чтобы не блокировать цикл
                        if (continuationQueue.isNotEmpty()) {
                            continuationQueue.removeFirst().resume(Unit)
                        }
                    }
                })
                // ------------------------------------------------
            }
        } else {
            Log.e(AppDebug.TAG, "TtsPlayer: ОШИБКА. Не удалось инициализировать TTS-движок.")
            isInitialized = false
        }
    }

    /**
     * Воспроизводит указанный текст на иврите (быстрый вызов, "fire-and-forget").
     * Используется в MatchingGameScreen.
     */
    fun speak(text: String) {
        if (!isInitialized || tts == null) {
            Log.w(AppDebug.TAG, "TtsPlayer: Попытка 'speak' ('$text'), но TTS не готов.")
            return
        }

        // QUEUE_FLUSH: Прерывает текущее и ставит новое
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    /**
     * --- ИЗМЕНЕНИЕ 3: НОВАЯ SUSPEND-ФУНКЦИЯ ---
     * Воспроизводит текст и ждет, пока TTS не закончит говорить.
     * Используется для цикла A/B в JournalViewModel.
     */
    suspend fun speakAndAwait(text: String) {
        if (!isInitialized || tts == null) {
            Log.w(AppDebug.TAG, "TtsPlayer: Попытка 'speakAndAwait' ('$text'), но TTS не готов.")
            return
        }

        // Мы должны использовать suspendCancellableCoroutine, чтобы
        // A/B цикл мог быть прерван.
        return suspendCancellableCoroutine { continuation ->
            // Добавляем continuation в очередь
            continuationQueue.addLast(continuation)

            // Уникальный ID, чтобы listener знал, что мы закончили
            val utteranceId = "journal_word_${System.currentTimeMillis()}"
            val bundle = Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
            }

            // QUEUE_ADD: Добавляем в очередь (не прерываем, если что-то уже говорится)
            tts?.speak(text, TextToSpeech.QUEUE_ADD, bundle, utteranceId)

            // Если coroutine отменена (например, пользователь нажал Stop)
            continuation.invokeOnCancellation {
                Log.d(AppDebug.TAG, "TtsPlayer: Coroutine отменена. Очищаем очередь.")
                continuationQueue.clear() // Очищаем очередь, чтобы избежать утечек
                stop() // Немедленно останавливаем речь
            }
        }
    }

    /**
     * Немедленно останавливает воспроизведение.
     */
    fun stop() {
        if (isInitialized) {
            tts?.stop()
        }
        // Очищаем очередь ожидания
        continuationQueue.forEach { it.resume(Unit) }
        continuationQueue.clear()
    }
    // ------------------------------------------------

    /**
     * Должен вызываться при закрытии приложения (например, в MainActivity.onDestroy),
     * чтобы освободить ресурсы.
     */
    fun shutdown() {
        Log.d(AppDebug.TAG, "TtsPlayer: Выключение TTS-сервиса...")
        isInitialized = false
        stop() // Очищаем очередь и останавливаем речь
        tts?.shutdown()
        tts = null
    }
}