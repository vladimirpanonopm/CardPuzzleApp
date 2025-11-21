package com.example.cardpuzzleapp

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

private const val TAG = "JOURNAL_DEBUG"

@HiltViewModel
class JournalViewModel @Inject constructor(
    private val progressManager: GameProgressManager,
    private val audioPlayer: AudioPlayer,
    private val levelRepository: LevelRepository,
    private val ttsPlayer: TtsPlayer
) : ViewModel() {

    var journalItems by mutableStateOf<List<JournalItem>>(emptyList())
        private set

    var isLoading by mutableStateOf(true)
        private set

    private var audioPlaybackJob: Job? = null

    val initialFontSize: Float = progressManager.getJournalFontSize()
    val initialFontStyle: FontStyle = progressManager.getJournalFontStyle()

    fun loadGlobalJournal() {
        Log.d(TAG, "VM: loadGlobalJournal() called")

        viewModelScope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) { isLoading = true }

                val allItems = mutableListOf<JournalItem>()

                // 1. Получаем список всех уровней
                Log.d(TAG, "VM: Fetching levels metadata...")
                val levelsMeta = levelRepository.getLevelsMetadata()
                Log.d(TAG, "VM: Found ${levelsMeta.size} levels")

                // 2. Пробегаем по каждому уровню
                for (meta in levelsMeta) {
                    val levelId = meta.levelId
                    // Log.d(TAG, "VM: Processing Level $levelId...") // (Раскомментируй, если нужно детальнее)

                    // Грузим данные уровня
                    levelRepository.loadLevelDataIfNeeded(levelId)
                    val sentences = levelRepository.getSentencesForLevel(levelId)

                    if (sentences.isEmpty()) {
                        Log.w(TAG, "VM: Warning - Level $levelId has NO sentences loaded")
                        continue
                    }

                    // 3. Получаем прогресс
                    val completed = progressManager.getCompletedRounds(levelId)
                    val archived = progressManager.getArchivedRounds(levelId)

                    // Фильтр
                    val activeIndices = completed - archived
                    // Log.d(TAG, "VM: Level $levelId -> Active indices: ${activeIndices.size}")

                    activeIndices.sorted().forEach { index ->
                        val sentence = sentences.getOrNull(index)
                        if (sentence != null) {
                            // Фильтруем AUDITION
                            if (sentence.taskType != TaskType.AUDITION) {
                                allItems.add(JournalItem(
                                    sentence = sentence,
                                    levelId = levelId,
                                    roundIndex = index
                                ))
                            }
                        } else {
                            Log.e(TAG, "VM: ERROR! Index $index in Level $levelId is out of bounds/null")
                        }
                    }
                }

                Log.d(TAG, "VM: Global Journal constructed. Total items: ${allItems.size}")

                withContext(Dispatchers.Main) {
                    journalItems = allItems
                    isLoading = false
                    Log.d(TAG, "VM: UI updated. isLoading = false")
                }
            } catch (e: Exception) {
                Log.e(TAG, "VM: CRASH inside loadGlobalJournal!", e)
                e.printStackTrace()
                // Не даем приложению упасть молча, сбрасываем загрузку
                withContext(Dispatchers.Main) { isLoading = false }
            }
        }
    }

    // --- Методы управления ---

    fun resetProgress(item: JournalItem) {
        Log.d(TAG, "VM: resetProgress for L${item.levelId}-R${item.roundIndex}")
        progressManager.removeSingleRoundProgress(item.levelId, item.roundIndex)
        loadGlobalJournal()
    }

    fun archiveCard(item: JournalItem) {
        Log.d(TAG, "VM: archiveCard for L${item.levelId}-R${item.roundIndex}")
        progressManager.archiveRound(item.levelId, item.roundIndex)
        loadGlobalJournal()
    }

    fun saveJournalFontSize(size: Float) {
        progressManager.saveJournalFontSize(size)
    }

    fun saveJournalFontStyle(style: FontStyle) {
        progressManager.saveJournalFontStyle(style)
    }

    // --- Аудио ---

    fun playSoundForPage(pageIndex: Int) {
        Log.d(TAG, "VM: playSoundForPage($pageIndex)")
        stopAudio()
        audioPlaybackJob?.cancel()
        audioPlaybackJob = viewModelScope.launch {
            val item = journalItems.getOrNull(pageIndex) ?: return@launch
            val sentence = item.sentence

            if (sentence.taskType == TaskType.MATCHING_PAIRS) {
                val words = sentence.task_pairs?.mapNotNull { it.getOrNull(0) } ?: emptyList()
                for (word in words) {
                    ttsPlayer.speakAndAwait(word)
                    delay(500)
                }
            } else if (sentence.audioFilename != null) {
                Log.d(TAG, "VM: Playing file ${sentence.audioFilename}")
                audioPlayer.play(sentence.audioFilename)
            }
        }
    }

    suspend fun playAndAwait(pageIndex: Int, speed: Float) {
        val item = journalItems.getOrNull(pageIndex) ?: return
        val sentence = item.sentence

        if (sentence.taskType == TaskType.MATCHING_PAIRS) {
            val words = sentence.task_pairs?.mapNotNull { it.getOrNull(0) } ?: emptyList()
            for (word in words) {
                ttsPlayer.speakAndAwait(word)
                delay((1000 / speed).toLong())
            }
        } else if (sentence.audioFilename != null) {
            audioPlayer.playAndAwaitCompletion(sentence.audioFilename, speed)
        }
    }

    fun stopAudio() {
        audioPlaybackJob?.cancel()
        audioPlayer.stop()
        ttsPlayer.stop()
    }

    fun releaseAudio() {
        stopAudio()
    }

    override fun onCleared() {
        super.onCleared()
        releaseAudio()
        ttsPlayer.stop()
    }
}