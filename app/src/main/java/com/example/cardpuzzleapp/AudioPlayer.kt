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

// TAG для фильтрации
private const val TAG = "AUDIO_DEBUG"

class AudioPlayer(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private var playbackJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)
    private val mainHandler = Handler(Looper.getMainLooper())

    fun play(filename: String, speed: Float = 1.0f) {
        Log.d(TAG, "Player: play($filename, speed=$speed)")
        stop()
        startPlayback(filename, 0, -1, speed)
    }

    fun playSegment(filename: String, startMs: Long, endMs: Long, speed: Float = 1.0f) {
        Log.d(TAG, "Player: playSegment($filename, $startMs-$endMs, speed=$speed)")
        stop()
        startPlayback(filename, startMs.toInt(), endMs.toInt(), speed)
    }

    suspend fun playAndAwaitCompletion(filename: String, speed: Float = 1.0f) {
        Log.d(TAG, "Player: playAndAwaitCompletion")
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

            Log.d(TAG, "Player: Creating MediaPlayer for $filename")
            val assetFileDescriptor = context.assets.openFd("audio/$filename")

            mediaPlayer = MediaPlayer().apply {
                setDataSource(assetFileDescriptor.fileDescriptor, assetFileDescriptor.startOffset, assetFileDescriptor.length)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    try {
                        playbackParams = playbackParams.setSpeed(speed)
                    } catch (e: Exception) { e.printStackTrace() }
                }

                setOnCompletionListener {
                    Log.d(TAG, "Player: onCompletionListener -> Stop immediately")
                    _isPlaying.value = false
                    mainHandler.post { stop() }
                }

                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "Player: onError ($what, $extra)")
                    _isPlaying.value = false
                    mainHandler.post { stop() }
                    true
                }

                prepare()
                if (startMs > 0) {
                    seekTo(startMs)
                    Log.d(TAG, "Player: SeekTo $startMs")
                }
                start()
            }
            assetFileDescriptor.close()

            _isPlaying.value = true
            Log.d(TAG, "Player: START SUCCESS. isPlaying=true")

            if (endMs > startMs) {
                val durationMs = endMs - startMs
                val timeToWait = (durationMs / speed).toLong()
                Log.d(TAG, "Player: Timer set for $timeToWait ms")

                playbackJob = scope.launch {
                    delay(timeToWait)
                    if (_isPlaying.value) {
                        Log.d(TAG, "Player: Timer FINISHED. Force pausing.")
                        try { mediaPlayer?.pause() } catch (e: Exception) { }
                        stop()
                    }
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Player: EXCEPTION: $e")
            e.printStackTrace()
            stop()
        }
    }

    fun stop() {
        Log.d(TAG, "Player: stop() called")

        mainHandler.removeCallbacksAndMessages(null)
        playbackJob?.cancel()
        playbackJob = null

        releaseMediaPlayer()

        if (_isPlaying.value) {
            _isPlaying.value = false
            Log.d(TAG, "Player: isPlaying forcibly set to false")
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