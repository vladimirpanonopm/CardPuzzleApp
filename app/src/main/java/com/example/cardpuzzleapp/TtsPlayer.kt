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
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Сервис, управляющий Text-To-Speech (TTS) движком Android.
 */
@Singleton
class TtsPlayer @Inject constructor(
    @ApplicationContext private val context: Context
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private val targetLocale = Locale("he")

    // Очередь для coroutines, ожидающих завершения речи
    private var continuationQueue = ArrayDeque<CancellableContinuation<Unit>>()

    init {
        try {
            Log.d(AppDebug.TAG, "TtsPlayer: Инициализация TextToSpeech...")
            tts = TextToSpeech(context, this)
        } catch (e: Exception) {
            Log.e(AppDebug.TAG, "TtsPlayer: Крэш при инициализации TTS. $e")
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(targetLocale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e(AppDebug.TAG, "TtsPlayer: ОШИБКА. Язык не поддерживается.")
                isInitialized = false
            } else {
                Log.d(AppDebug.TAG, "TtsPlayer: Успешно инициализирован.")
                isInitialized = true

                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}

                    override fun onDone(utteranceId: String?) {
                        // Речь завершена, возобновляем coroutine
                        synchronized(continuationQueue) {
                            if (continuationQueue.isNotEmpty()) {
                                continuationQueue.removeFirst().resume(Unit)
                            }
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        Log.e(AppDebug.TAG, "TtsPlayer: Ошибка воспроизведения $utteranceId")
                        synchronized(continuationQueue) {
                            if (continuationQueue.isNotEmpty()) {
                                continuationQueue.removeFirst().resume(Unit)
                            }
                        }
                    }
                })
            }
        } else {
            Log.e(AppDebug.TAG, "TtsPlayer: ОШИБКА инициализации.")
            isInitialized = false
        }
    }

    fun speak(text: String) {
        if (!isInitialized || tts == null) return
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    /**
     * Воспроизводит текст и ждет завершения.
     * --- ИЗМЕНЕНИЕ: Добавлен таймаут 5 секунд ---
     */
    suspend fun speakAndAwait(text: String) {
        if (!isInitialized || tts == null) return

        // Оборачиваем в withTimeoutOrNull. Если пройдет 5 секунд, блок отменится.
        withTimeoutOrNull(5000) {
            suspendCancellableCoroutine { continuation ->
                synchronized(continuationQueue) {
                    continuationQueue.addLast(continuation)
                }

                val utteranceId = "word_${System.currentTimeMillis()}"
                val bundle = Bundle().apply {
                    putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
                }

                tts?.speak(text, TextToSpeech.QUEUE_ADD, bundle, utteranceId)

                continuation.invokeOnCancellation {
                    // Если сработал таймаут или юзер ушел с экрана
                    Log.d(AppDebug.TAG, "TtsPlayer: Отмена/Таймаут. Очистка очереди.")
                    synchronized(continuationQueue) {
                        continuationQueue.clear()
                    }
                    stop()
                }
            }
        }
    }

    fun stop() {
        if (isInitialized) {
            tts?.stop()
        }
        synchronized(continuationQueue) {
            continuationQueue.forEach { if (it.isActive) it.resume(Unit) }
            continuationQueue.clear()
        }
    }

    fun shutdown() {
        isInitialized = false
        stop()
        tts?.shutdown()
        tts = null
    }
}