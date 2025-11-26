package com.example.cardpuzzleapp

import android.content.Context
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "AUDIO_DEBUG"

class AudioPlayer(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private var playbackJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)
    private val mainHandler = Handler(Looper.getMainLooper())

    // --- ИЗМЕНЕНИЕ: Флаг для предотвращения работы после уничтожения ---
    @Volatile
    private var isDestroyed = false

    fun play(filename: String, speed: Float = 1.0f) {
        if (isDestroyed) return
        stop()
        startPlayback(filename, 0, -1, speed)
    }

    fun playSegment(filename: String, startMs: Long, endMs: Long, speed: Float = 1.0f) {
        if (isDestroyed) return
        stop()
        startPlayback(filename, startMs.toInt(), endMs.toInt(), speed)
    }

    suspend fun playAndAwaitCompletion(filename: String, speed: Float = 1.0f) {
        if (isDestroyed) return
        stop()
        play(filename, speed)
        delay(100)
        while (_isPlaying.value && !isDestroyed) {
            delay(100)
        }
    }

    private fun startPlayback(filename: String, startMs: Int, endMs: Int, speed: Float) {
        try {
            releaseMediaPlayer()

            // Если плеер уже уничтожен пока мы тут думали — выход
            if (isDestroyed) return

            Log.d(TAG, "Player: Creating MediaPlayer for $filename")
            val assetFileDescriptor = context.assets.openFd("audio/$filename")

            mediaPlayer = MediaPlayer().apply {
                setDataSource(assetFileDescriptor.fileDescriptor, assetFileDescriptor.startOffset, assetFileDescriptor.length)

                setOnCompletionListener {
                    _isPlaying.value = false
                    // Используем post, чтобы не блокировать UI поток листенера
                    mainHandler.post { stop() }
                }

                setOnErrorListener { _, _, _ ->
                    _isPlaying.value = false
                    mainHandler.post { stop() }
                    true
                }

                prepare()

                // Еще одна проверка перед стартом
                if (isDestroyed) {
                    release()
                    return@apply
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    try {
                        val params = playbackParams
                        params.speed = speed
                        playbackParams = params
                    } catch (e: Exception) { }
                }

                if (startMs > 0) {
                    seekTo(startMs)
                }

                start()
            }
            assetFileDescriptor.close()

            _isPlaying.value = true

            if (endMs > startMs) {
                val durationMs = endMs - startMs
                val timeToWait = (durationMs / speed).toLong()

                playbackJob = scope.launch {
                    delay(timeToWait)
                    if (_isPlaying.value && !isDestroyed) {
                        try { mediaPlayer?.pause() } catch (e: Exception) { }
                        stop()
                    }
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Player: EXCEPTION: $e")
            stop()
        }
    }

    fun stop() {
        mainHandler.removeCallbacksAndMessages(null)
        playbackJob?.cancel()
        playbackJob = null

        releaseMediaPlayer()

        if (_isPlaying.value) {
            _isPlaying.value = false
        }
    }

    private fun releaseMediaPlayer() {
        try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.stop()
            }
        } catch (e: Exception) { } finally {
            mediaPlayer?.release()
            mediaPlayer = null
        }
    }

    fun release() {
        isDestroyed = true // Блокируем любые новые запуски
        stop()
    }
}