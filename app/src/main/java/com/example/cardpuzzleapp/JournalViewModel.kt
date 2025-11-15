package com.example.cardpuzzleapp

import android.app.Application
import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay

/**
 * Этот ViewModel отвечает исключительно за логику и данные экрана "Журнал".
 */
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

    val userLanguage: String = progressManager.getUserLanguage() ?: "ru"

    /**
     * Главный метод инициализации.
     * Загружает данные для конкретного уровня и обновляет список карточек в журнале.
     */
    fun loadJournalForLevel(levelId: Int) {
        if (levelId == -1) return
        this.currentLevelId = levelId

        viewModelScope.launch(Dispatchers.IO) {
            levelRepository.loadLevelDataIfNeeded(levelId)
            val levelData = levelRepository.getSentencesForLevel(levelId)

            withContext(Dispatchers.Main) {
                // --- ИЗМЕНЕНИЕ: Убран '?: emptyList()' ---
                this@JournalViewModel.currentLevelSentences = levelData
                // --- КОНЕЦ ---
                loadJournalSentences()
            }
        }
    }

    /**
     * Загружает индексы пройденных раундов и формирует список карточек для журнала.
     */
    private fun loadJournalSentences() {
        if (currentLevelId == -1) return

        val allCompleted = progressManager.getCompletedRounds(currentLevelId)
        val allArchived = progressManager.getArchivedRounds(currentLevelId)

        val activeJournalIndices = allCompleted - allArchived

        journalSentences = currentLevelSentences.filterIndexed { index, _ ->
            index in activeJournalIndices
        }
    }

    /**
     * "Забывает" карточку, возвращая ее из журнала обратно в игру.
     */
    fun resetSingleRoundProgress(roundIndex: Int) {
        if (currentLevelId == -1) return
        progressManager.removeSingleRoundProgress(currentLevelId, roundIndex)
        loadJournalSentences()
    }

    /**
     * "Удаляет" карточку, перемещая ее в архив.
     */
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

    // --- Методы для УПРАВЛЕНИЯ AUDIO ---

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