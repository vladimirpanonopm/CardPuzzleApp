package com.example.cardpuzzleapp

import android.content.Context
import android.media.MediaPlayer
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class AudioPlayer(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null

    // --- ДОБАВЛЕНО: StateFlow для отслеживания состояния ---
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()
    // --- КОНЕЦ ДОБАВЛЕНИЯ ---

    // ИЗМЕНЕНИЕ: Новая suspend-функция, которая ждет окончания проигрывания
    suspend fun playAndAwaitCompletion(filename: String, playbackSpeed: Float = 1.0f) {
        // Убедимся, что предыдущее воспроизведение остановлено
        stop()

        try {
            val assetFileDescriptor = context.assets.openFd("audio/$filename")
            mediaPlayer = MediaPlayer().apply {
                setDataSource(assetFileDescriptor.fileDescriptor, assetFileDescriptor.startOffset, assetFileDescriptor.length)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    playbackParams = playbackParams.setSpeed(playbackSpeed)
                }
                prepare()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            release()
            return
        }

        // --- ДОБАВЛЕНО: Обновляем состояние ---
        _isPlaying.value = true
        // --- КОНЕЦ ДОБАВЛЕНИЯ ---

        // Эта магия позволяет "превратить" callback в suspend-функцию
        suspendCancellableCoroutine<Unit> { continuation ->
            mediaPlayer?.setOnCompletionListener {
                // --- ДОБАВЛЕНО: Обновляем состояние ---
                _isPlaying.value = false
                // --- КОНЕЦ ДОБАВЛЕНИЯ ---
                if (continuation.isActive) {
                    continuation.resume(Unit)
                }
            }
            mediaPlayer?.start()
            continuation.invokeOnCancellation {
                stop() // 'stop()' также установит _isPlaying.value = false
            }
        }
    }

    // Старая функция остается для простых случаев, где не нужно ждать
    fun play(filename: String, playbackSpeed: Float = 1.0f) {
        stop()
        try {
            val assetFileDescriptor = context.assets.openFd("audio/$filename")
            mediaPlayer = MediaPlayer().apply {
                setDataSource(assetFileDescriptor.fileDescriptor, assetFileDescriptor.startOffset, assetFileDescriptor.length)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    playbackParams = playbackParams.setSpeed(playbackSpeed)
                }
                prepare()
                start()
                // --- ДОБАВЛЕНО: Обновляем состояние ---
                _isPlaying.value = true
                // --- КОНЕЦ ДОБАВЛЕНИЯ ---
                setOnCompletionListener {
                    // --- ДОБАВЛЕНО: Обновляем состояние ---
                    _isPlaying.value = false
                    // --- КОНЕЦ ДОБАВЛЕНИЯ ---
                    it.release()
                    mediaPlayer = null
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            release() // 'release()' вызовет 'stop()', который обновит состояние
        }
    }

    fun stop() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        // --- ДОБАВЛЕНО: Обновляем состояние ---
        _isPlaying.value = false
        // --- КОНЕЦ ДОБАВЛЕНИЯ ---
    }

    fun release() {
        stop()
    }
}