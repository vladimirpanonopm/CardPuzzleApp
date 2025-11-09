package com.example.cardpuzzleapp

import android.app.Application
import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
// --- НОВЫЕ ИМПОРТЫ ---
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Этот ViewModel отвечает исключительно за логику и данные экрана "Журнал".
 */
@HiltViewModel
class JournalViewModel @Inject constructor(
    private val progressManager: GameProgressManager,
    private val audioPlayer: AudioPlayer,
    // --- ИЗМЕНЕНИЕ 1: Внедряем Репозиторий ---
    private val levelRepository: LevelRepository
) : ViewModel() {

    val journalSentences = mutableStateListOf<SentenceData>()

    private var currentLevelId: Int = -1
    var currentLevelSentences = listOf<SentenceData>()
        private set

    /**
     * Главный метод инициализации.
     * Загружает данные для конкретного уровня и обновляет список карточек в журнале.
     */
    // --- ИЗМЕНЕНИЕ 2: Убираем Context, добавляем Coroutine ---
    fun loadJournalForLevel(levelId: Int) {
        if (levelId == -1) return
        this.currentLevelId = levelId

        viewModelScope.launch(Dispatchers.IO) {
            // Загружаем все предложения для данного уровня.
            val levelData = levelRepository.getLevelData(levelId)

            withContext(Dispatchers.Main) {
                this@JournalViewModel.currentLevelSentences = levelData ?: emptyList()
                // Обновляем список карточек для отображения.
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

        // В журнале показываем только те, что пройдены, но не в архиве.
        val activeJournalIndices = allCompleted - allArchived

        val completedSentences = currentLevelSentences.filterIndexed { index, _ ->
            index in activeJournalIndices
        }
        journalSentences.clear()
        journalSentences.addAll(completedSentences)
    }
    // -----------------------------------------------------------

    /**
     * "Забывает" карточку, возвращая ее из журнала обратно в игру.
     */
    fun resetSingleRoundProgress(roundIndex: Int) {
        if (currentLevelId == -1) return
        progressManager.removeSingleRoundProgress(currentLevelId, roundIndex)
        // Перезагружаем список, чтобы убранная карточка исчезла из UI.
        loadJournalSentences()
    }

    /**
     * "Удаляет" карточку, перемещая ее в архив. Она больше не будет появляться ни в игре, ни в журнале.
     */
    fun archiveJournalCard(roundIndex: Int) {
        if (currentLevelId == -1) return
        progressManager.archiveRound(currentLevelId, roundIndex)
        // Перезагружаем список, чтобы архивированная карточка исчезла из UI.
        loadJournalSentences()
    }

    // --- Методы для УПРАВЛЕНИЯ AUDIO ---

    fun playSoundForPage(pageIndex: Int) {
        journalSentences.getOrNull(pageIndex)?.audioFilename?.let {
            audioPlayer.play(it)
        }
    }

    suspend fun playAndAwait(pageIndex: Int, speed: Float) {
        journalSentences.getOrNull(pageIndex)?.audioFilename?.let {
            audioPlayer.playAndAwaitCompletion(it, speed)
        }
    }

    fun stopAudio() {
        audioPlayer.stop()
    }

    fun releaseAudio() {
        audioPlayer.release()
    }
}