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

    /**
     * Главный метод инициализации.
     * Загружает данные для конкретного уровня и обновляет список карточек в журнале.
     */
    fun loadJournalForLevel(levelId: Int) {
        if (levelId == -1) return
        this.currentLevelId = levelId

        // --- ИЗМЕНЕНИЕ ДЛЯ "ПРОБЛЕМЫ 2" ---
        viewModelScope.launch(Dispatchers.IO) {
            // 1. ВЫЗЫВАЕМ НОВЫЙ МЕТОД ЗАГРУЗКИ (асинхронно)
            levelRepository.loadLevelDataIfNeeded(levelId)

            // 2. ВЫЗЫВАЕМ НОВЫЙ СИНХРОННЫЙ GETTER
            val levelData = levelRepository.getSentencesForLevel(levelId)

            withContext(Dispatchers.Main) {
                this@JournalViewModel.currentLevelSentences = levelData ?: emptyList()
                // Обновляем список карточек для отображения.
                loadJournalSentences()
            }
        }
        // --- КОНЕЦ ИЗМЕНЕНИЯ ---
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

    /**
     * Воспроизводит звук для одной страницы (обычное нажатие или пролистывание).
     */
    fun playSoundForPage(pageIndex: Int) {
        audioPlaybackJob?.cancel() // Отменяем предыдущее воспроизведение
        audioPlaybackJob = viewModelScope.launch {
            val sentence = journalSentences.getOrNull(pageIndex) ?: return@launch

            if (sentence.taskType == TaskType.MATCHING_PAIRS) {
                // Для "Соедини пары" - озвучиваем слова по очереди
                val words = sentence.task_pairs?.mapNotNull { it.getOrNull(0) } ?: emptyList()
                for (word in words) {
                    ttsPlayer.speakAndAwait(word) // Ждем завершения слова
                    delay(500) // Небольшая пауза между словами
                }
            } else if (sentence.audioFilename != null) {
                // Для остальных - проигрываем аудиофайл
                audioPlayer.play(sentence.audioFilename)
            }
        }
    }

    /**
     * Воспроизводит звук и ЖДЕТ завершения. Используется циклом A-B.
     */
    suspend fun playAndAwait(pageIndex: Int, speed: Float) {
        val sentence = journalSentences.getOrNull(pageIndex) ?: return

        if (sentence.taskType == TaskType.MATCHING_PAIRS) {
            // Для "Соедини пары" - озвучиваем слова по очереди
            val words = sentence.task_pairs?.mapNotNull { it.getOrNull(0) } ?: emptyList()
            for (word in words) {
                ttsPlayer.speakAndAwait(word) // Ждем завершения слова
                // Пауза 1 сек (с поправкой на скорость)
                delay((1000 / speed).toLong())
            }
        } else if (sentence.audioFilename != null) {
            // Для остальных - проигрываем аудиофайл
            audioPlayer.playAndAwaitCompletion(sentence.audioFilename, speed)
        }
    }

    /**
     * Останавливает любое воспроизведение (и AudioPlayer, и TTS).
     */
    fun stopAudio() {
        audioPlaybackJob?.cancel()
        audioPlayer.stop()
        ttsPlayer.stop()
    }

    /**
     * Вызывается, когда экран Журнала закрывается (DisposableEffect).
     */
    fun releaseAudio() {
        stopAudio()
    }

    /**
     * Вызывается, когда ViewModel уничтожается.
     */
    override fun onCleared() {
        super.onCleared()
        releaseAudio()
        ttsPlayer.stop()
    }
}