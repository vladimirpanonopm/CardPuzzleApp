package com.example.cardpuzzleapp

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.util.UUID

class CardViewModel(application: Application) : AndroidViewModel(application) {

    // --- НАШЕ ИЗМЕНЕНИЕ (1/2) ---
    // Добавляем новый State, чтобы UI знал, какую картинку показывать
    var currentImageName by mutableStateOf<String?>(null)
        private set
    // ---------------------------

    val progressManager = GameProgressManager(application)
    var resultSnapshot by mutableStateOf<RoundResultSnapshot?>(null)
        private set
    var currentLevelSentences = listOf<SentenceData>()
        private set
    var targetCards = listOf<Card>()
        private set
    var currentLevelId by mutableStateOf(1)
        private set
    var currentRoundIndex by mutableStateOf(0)
        private set
    var isLevelFullyCompleted by mutableStateOf(false)
        private set
    var timeSpent by mutableStateOf(0)
        private set
    var errorCount by mutableStateOf(0)
        private set
    var availableCards = mutableStateListOf<Card>()
        private set
    var selectedCards = mutableStateListOf<Card>()
        private set
    var isLastRoundAvailable by mutableStateOf(false)
        private set

    // Состояния для UI
    var showResultSheet by mutableStateOf(false)
        private set
    var errorCardId by mutableStateOf<UUID?>(null)
        private set
    var isRoundWon by mutableStateOf(false)
        private set

    var gameFontStyle by mutableStateOf(FontStyle.REGULAR)
        private set

    private var wordDictionary = mapOf<String, String>()
    private val _hapticEventChannel = Channel<HapticEvent>()
    val hapticEvents = _hapticEventChannel.receiveAsFlow()
    private var timerJob: Job? = null
    private val _navigationEvent = Channel<String>()
    val navigationEvent = _navigationEvent.receiveAsFlow()

    fun toggleGameFontStyle() {
        if (currentLevelId == 1) {
            val newStyle = if (gameFontStyle == FontStyle.CURSIVE) FontStyle.REGULAR else FontStyle.CURSIVE
            gameFontStyle = newStyle
            // Сохраняем выбор пользователя
            progressManager.saveLevel1FontStyle(newStyle)
        }
    }

    fun onVictoryAnimationFinished() {
        showResultSheet = true
    }

    fun resetAllProgress() {
        progressManager.resetAllProgressExceptLanguage()
    }

    fun hideResultSheet() {
        showResultSheet = false
    }

    fun selectCard(card: Card) {
        val isCorrect = selectedCards.size < targetCards.size && card.text == targetCards[selectedCards.size].text

        viewModelScope.launch {
            if (isCorrect) {
                _hapticEventChannel.send(HapticEvent.Success)
            } else {
                _hapticEventChannel.send(HapticEvent.Failure)
            }
        }

        if (isCorrect) {
            selectedCards.add(card)
            availableCards.remove(card)
            if (selectedCards.size == targetCards.size) {
                endRound(GameResult.WIN)
            }
        } else {
            errorCount++
            errorCardId = card.id
        }
    }

    fun loadLevel(context: android.content.Context, levelId: Int): Boolean {
        val levelData = LevelRepository.getLevelData(context, levelId) ?: return false
        this.currentLevelSentences = levelData
        this.currentLevelId = levelId

        // Загружаем сохраненный шрифт или устанавливаем по умолчанию
        gameFontStyle = if (levelId == 1) progressManager.getLevel1FontStyle() else FontStyle.REGULAR

        val userLanguage = progressManager.getUserLanguage()
        wordDictionary = levelData
            .filter { !it.hebrew.contains(" ") }
            .associate {
                it.hebrew to (when (userLanguage) {
                    "en" -> it.english_translation
                    "fr" -> it.french_translation
                    "es" -> it.spanish_translation
                    else -> it.russian_translation
                } ?: "") // <-- ВОТ ИСПРАВЛЕНИЕ
            }

        val completedRounds = progressManager.getCompletedRounds(levelId)
        val archivedRounds = progressManager.getArchivedRounds(levelId)
        val totalRounds = levelData.size

        if ((completedRounds.size + archivedRounds.size) >= totalRounds && totalRounds > 0) {
            isLevelFullyCompleted = true
            return true
        }

        isLevelFullyCompleted = false
        val nextRoundToPlay = (0 until totalRounds).firstOrNull { !completedRounds.contains(it) && !archivedRounds.contains(it) }

        if (nextRoundToPlay != null) {
            loadRound(nextRoundToPlay)
        } else {
            isLevelFullyCompleted = true
            return true
        }
        return false
    }

    fun loadRound(roundIndex: Int) {
        resultSnapshot = null
        isRoundWon = false
        if (roundIndex < currentLevelSentences.size) {
            this.currentRoundIndex = roundIndex
            resetAndStartCounters()
            val roundData = currentLevelSentences[roundIndex]
            this.targetCards = parseSentenceToCards(roundData.hebrew, wordDictionary)

            // --- НАШЕ ИЗМЕНЕНИЕ (2/2) ---
            // Обновляем State с картинкой для текущего раунда
            this.currentImageName = roundData.imageName
            // ---------------------------

            selectedCards.clear()
            availableCards.clear()
            availableCards.addAll(targetCards.shuffled())

            val allCompleted = progressManager.getCompletedRounds(currentLevelId)
            val allArchived = progressManager.getArchivedRounds(currentLevelId)
            val uncompletedRounds = currentLevelSentences.indices.filter {
                !allCompleted.contains(it) && !allArchived.contains(it)
            }
            isLastRoundAvailable = uncompletedRounds.size <= 1
        }
    }

    private fun resetAndStartCounters() {
        errorCount = 0
        val startTime = System.currentTimeMillis()
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                timeSpent = ((System.currentTimeMillis() - startTime) / 1000).toInt()
                delay(1000)
            }
        }
    }

    private fun endRound(result: GameResult) {
        timerJob?.cancel()
        isRoundWon = true
        val userLanguage = progressManager.getUserLanguage()
        val currentSentence = currentLevelSentences.getOrNull(currentRoundIndex)
        val translation = when (userLanguage) {
            "en" -> currentSentence?.english_translation
            "fr" -> currentSentence?.french_translation
            "es" -> currentSentence?.spanish_translation
            else -> currentSentence?.russian_translation
        }

        if (result == GameResult.WIN) {
            progressManager.saveProgress(currentLevelId, currentRoundIndex)
        }

        val allCompleted = progressManager.getCompletedRounds(currentLevelId)
        val allArchived = progressManager.getArchivedRounds(currentLevelId)
        val hasMoreRounds = (allCompleted.size + allArchived.size) < currentLevelSentences.size

        resultSnapshot = RoundResultSnapshot(
            gameResult = result,
            completedCards = selectedCards.toList(),
            translation = translation ?: "Перевод не найден",
            errorCount = this.errorCount,
            timeSpent = this.timeSpent,
            levelId = this.currentLevelId,
            hasMoreRounds = hasMoreRounds,
            audioFilename = if (result == GameResult.WIN) currentLevelSentences.getOrNull(currentRoundIndex)?.audioFilename else null,
            imageName = if (result == GameResult.WIN) currentLevelSentences.getOrNull(currentRoundIndex)?.imageName else null
        )
    }

    fun restartCurrentRound() {
        if (isLevelFullyCompleted) isLevelFullyCompleted = false
        loadRound(currentRoundIndex)
    }

    fun resetCurrentLevelProgress() {
        progressManager.resetLevelProgress(currentLevelId)
        isLevelFullyCompleted = false
        loadRound(0)
    }

    fun proceedToNextRound() {
        val completedRounds = progressManager.getCompletedRounds(currentLevelId)
        val archivedRounds = progressManager.getArchivedRounds(currentLevelId)

        val uncompletedRounds = (0 until currentLevelSentences.size).filter {
            !completedRounds.contains(it) && !archivedRounds.contains(it)
        }

        if (uncompletedRounds.isEmpty()) {
            isLevelFullyCompleted = true
            viewModelScope.launch {
                _navigationEvent.send("round_track/$currentLevelId")
            }
            return
        }

        val nextForwardRound = uncompletedRounds.firstOrNull { it > currentRoundIndex }

        val nextRound = nextForwardRound ?: uncompletedRounds.first()

        loadRound(nextRound)
    }

    fun skipToNextAvailableRound() {
        val completedRounds = progressManager.getCompletedRounds(currentLevelId)
        val archivedRounds = progressManager.getArchivedRounds(currentLevelId)
        val activeRounds = currentLevelSentences.indices.filter { !completedRounds.contains(it) && !archivedRounds.contains(it) }
        if (activeRounds.isEmpty()) return
        val currentIndexInActiveList = activeRounds.indexOf(currentRoundIndex)
        val nextIndexInActiveList = if(currentIndexInActiveList != -1) (currentIndexInActiveList + 1) % activeRounds.size else 0
        loadRound(activeRounds[nextIndexInActiveList])
    }

    companion object {
        private const val TAG = "VIBRATE_DEBUG"
    }
}