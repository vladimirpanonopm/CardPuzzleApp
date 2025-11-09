package com.example.cardpuzzleapp

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.unit.sp
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * Этот ViewModel отвечает исключительно за логику и данные экрана "Журнал".
 */
@HiltViewModel
class JournalViewModel @Inject constructor(
    private val progressManager: GameProgressManager,
    private val audioPlayer: AudioPlayer,
    private val levelRepository: LevelRepository
) : ViewModel() {

    val journalSentences = mutableStateListOf<SentenceData>()

    private var currentLevelId: Int = -1
    var currentLevelSentences = listOf<SentenceData>()
        private set

    // --- НОВЫЕ STATE ДЛЯ ШРИФТА (1) ---
    var currentFontStyle by mutableStateOf(FontStyle.REGULAR)
        private set
    var currentFontSizeSp by mutableStateOf(32.sp) // Используем TextUnit
        private set
    // ---------------------------------

    // Блок инициализации для загрузки настроек при создании ViewModel
    init {
        loadPreferences()
    }

    // Метод для загрузки шрифта из GameProgressManager
    private fun loadPreferences() {
        currentFontStyle = progressManager.getJournalFontStyle()
        val sizeFloat = progressManager.getJournalFontSize() // Предполагается, что возвращает Float
        currentFontSizeSp = sizeFloat.sp
    }

    // --- Методы для УПРАВЛЕНИЯ ШРИФТОМ (2) ---
    fun toggleFontStyle() {
        currentFontStyle = if (currentFontStyle == FontStyle.CURSIVE) FontStyle.REGULAR else FontStyle.CURSIVE
        progressManager.saveJournalFontStyle(currentFontStyle)
    }

    fun saveNewFontSize(newSize: Float) {
        // Минимальный размер, чтобы избежать проблем
        if (newSize > 0f) {
            currentFontSizeSp = newSize.sp
            progressManager.saveJournalFontSize(newSize)
        }
    }
    // ------------------------------------------

    /**
     * Главный метод инициализации.
     * Загружает данные для конкретного уровня и обновляет список карточек в журнале.
     */
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