package com.example.cardpuzzleapp

import android.app.Application
import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel

/**
 * Этот ViewModel отвечает исключительно за логику и данные экрана "Журнал".
 */
class JournalViewModel(application: Application) : AndroidViewModel(application) {

    // Менеджер прогресса для сохранения и загрузки данных.
    private val progressManager = GameProgressManager(application)

    // Список предложений, которые отображаются в журнале.
    // Jetpack Compose будет автоматически следить за его изменениями.
    val journalSentences = mutableStateListOf<SentenceData>()

    // Информация о текущем уровне, для которого открыт журнал.
    private var currentLevelId: Int = -1
    var currentLevelSentences = listOf<SentenceData>()
        private set

    /**
     * Главный метод инициализации.
     * Загружает данные для конкретного уровня и обновляет список карточек в журнале.
     */
    fun loadJournalForLevel(context: Context, levelId: Int) {
        if (levelId == -1) return
        this.currentLevelId = levelId

        // Загружаем все предложения для данного уровня.
        val levelData = LevelRepository.getLevelData(context, levelId)
        this.currentLevelSentences = levelData ?: emptyList()

        // Обновляем список карточек для отображения.
        loadJournalSentences()
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
}