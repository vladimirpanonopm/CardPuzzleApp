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

class AudioPlayer(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private var playbackJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)
    private val mainHandler = Handler(Looper.getMainLooper())

    fun play(filename: String, speed: Float = 1.0f) {
        Log.d(AppDebug.TAG, "AudioPlayer: play($filename) called")
        stop()
        startPlayback(filename, 0, -1, speed)
    }

    fun playSegment(filename: String, startMs: Long, endMs: Long, speed: Float = 1.0f) {
        Log.d(AppDebug.TAG, "AudioPlayer: playSegment($filename, $startMs, $endMs) called")
        stop()
        startPlayback(filename, startMs.toInt(), endMs.toInt(), speed)
    }

    suspend fun playAndAwaitCompletion(filename: String, speed: Float = 1.0f) {
        stop()
        play(filename, speed)
        delay(100)
        while (_isPlaying.value) {
            delay(100)
        }
    }

    private fun startPlayback(filename: String, startMs: Int, endMs: Int, speed: Float) {
        try {
            releaseMediaPlayer()
            Log.d(AppDebug.TAG, "AudioPlayer: startPlayback creating MediaPlayer...")
            val assetFileDescriptor = context.assets.openFd("audio/$filename")

            mediaPlayer = MediaPlayer().apply {
                setDataSource(assetFileDescriptor.fileDescriptor, assetFileDescriptor.startOffset, assetFileDescriptor.length)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    try {
                        playbackParams = playbackParams.setSpeed(speed)
                    } catch (e: Exception) { e.printStackTrace() }
                }

                setOnCompletionListener {
                    Log.d(AppDebug.TAG, "AudioPlayer: onCompletionListener FIRED.")
                    // Сразу сбрасываем флаг, чтобы UI разморозился
                    _isPlaying.value = false
                    Log.d(AppDebug.TAG, "AudioPlayer: _isPlaying set to false immediately.")

                    // А очистку ресурсов делаем через Handler
                    mainHandler.post {
                        Log.d(AppDebug.TAG, "AudioPlayer: Handler executing stop().")
                        stop()
                    }
                }

                setOnErrorListener { _, what, extra ->
                    Log.e(AppDebug.TAG, "AudioPlayer: onError FIRED ($what, $extra)")
                    _isPlaying.value = false
                    mainHandler.post { stop() }
                    true
                }

                prepare()
                if (startMs > 0) seekTo(startMs)
                start()
            }
            assetFileDescriptor.close()

            _isPlaying.value = true
            Log.d(AppDebug.TAG, "AudioPlayer: MediaPlayer STARTED. isPlaying = true")

            if (endMs > startMs) {
                val durationMs = endMs - startMs
                val timeToWait = (durationMs / speed).toLong()
                playbackJob = scope.launch {
                    delay(timeToWait)
                    if (_isPlaying.value) {
                        Log.d(AppDebug.TAG, "AudioPlayer: Timer FINISHED. Pausing.")
                        try { mediaPlayer?.pause() } catch (e: Exception) { }
                        stop()
                    }
                }
            }

        } catch (e: Exception) {
            Log.e(AppDebug.TAG, "AudioPlayer: EXCEPTION: $e")
            e.printStackTrace()
            stop()
        }
    }

    fun stop() {
        Log.d(AppDebug.TAG, "AudioPlayer: stop() called.")

        mainHandler.removeCallbacksAndMessages(null)
        playbackJob?.cancel()
        playbackJob = null

        releaseMediaPlayer()

        // Дублируем сброс флага на всякий случай
        if (_isPlaying.value) {
            _isPlaying.value = false
            Log.d(AppDebug.TAG, "AudioPlayer: stop() reset isPlaying to false.")
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
        stop()
    }
}