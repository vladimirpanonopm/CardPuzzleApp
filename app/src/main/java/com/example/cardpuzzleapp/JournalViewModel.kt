package com.example.cardpuzzleapp

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

@HiltViewModel
class JournalViewModel @Inject constructor(
    private val progressManager: GameProgressManager,
    private val audioPlayer: AudioPlayer,
    private val levelRepository: LevelRepository,
    private val ttsPlayer: TtsPlayer
) : ViewModel() {

    var journalSentences by mutableStateOf<List<SentenceData>>(emptyList())
        private set

    private var currentLevelId: Int = -1
    var currentLevelSentences by mutableStateOf<List<SentenceData>>(emptyList())
        private set

    private var audioPlaybackJob: Job? = null

    val initialFontSize: Float = progressManager.getJournalFontSize()
    val initialFontStyle: FontStyle = progressManager.getJournalFontStyle()

    fun loadJournalForLevel(levelId: Int) {
        if (levelId == -1) return
        this.currentLevelId = levelId

        viewModelScope.launch(Dispatchers.IO) {
            levelRepository.loadLevelDataIfNeeded(levelId)
            val levelData = levelRepository.getSentencesForLevel(levelId)

            withContext(Dispatchers.Main) {
                this@JournalViewModel.currentLevelSentences = levelData
                loadJournalSentences()
            }
        }
    }

    private fun loadJournalSentences() {
        if (currentLevelId == -1) return

        val allCompleted = progressManager.getCompletedRounds(currentLevelId)
        val allArchived = progressManager.getArchivedRounds(currentLevelId)

        // Мы показываем только то, что в "Completed" и НЕ в "Archived"
        val activeJournalIndices = allCompleted - allArchived

        journalSentences = currentLevelSentences.filterIndexed { index, sentence ->
            // ФИЛЬТР:
            // 1. Индекс должен быть в списке "активных" (пройденных)
            // 2. Тип задания НЕ должен быть AUDITION (аудирование в журнал не пишем)
            val isAudition = sentence.taskType == TaskType.AUDITION
            (index in activeJournalIndices) && !isAudition
        }
    }

    fun resetSingleRoundProgress(roundIndex: Int) {
        if (currentLevelId == -1) return
        progressManager.removeSingleRoundProgress(currentLevelId, roundIndex)
        loadJournalSentences()
    }

    fun archiveJournalCard(roundIndex: Int) {
        if (currentLevelId == -1) return
        progressManager.archiveRound(currentLevelId, roundIndex)
        loadJournalSentences()
    }

    fun saveJournalFontSize(size: Float) {
        progressManager.saveJournalFontSize(size)
    }

    fun saveJournalFontStyle(style: FontStyle) {
        progressManager.saveJournalFontStyle(style)
    }

    fun playSoundForPage(pageIndex: Int) {
        stopAudio()

        audioPlaybackJob?.cancel()
        audioPlaybackJob = viewModelScope.launch {
            val sentence = journalSentences.getOrNull(pageIndex) ?: return@launch

            if (sentence.taskType == TaskType.MATCHING_PAIRS) {
                val words = sentence.task_pairs?.mapNotNull { it.getOrNull(0) } ?: emptyList()
                for (word in words) {
                    ttsPlayer.speakAndAwait(word)
                    delay(500)
                }
            } else if (sentence.audioFilename != null) {
                audioPlayer.play(sentence.audioFilename)
            }
        }
    }

    suspend fun playAndAwait(pageIndex: Int, speed: Float) {
        val sentence = journalSentences.getOrNull(pageIndex) ?: return

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