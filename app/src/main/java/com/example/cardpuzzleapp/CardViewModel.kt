package com.example.cardpuzzleapp

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers

data class AssemblySlot(
    val id: UUID = UUID.randomUUID(),
    val text: String,
    val isBlank: Boolean,
    var filledCard: Card? = null,
    val targetCard: Card? = null
)


@HiltViewModel
class CardViewModel @Inject constructor(
    val progressManager: GameProgressManager,
    private val audioPlayer: AudioPlayer,
    private val levelRepository: LevelRepository
) : ViewModel() {

    var currentTaskPrompt by mutableStateOf<String?>("")
        private set

    var currentHebrewPrompt by mutableStateOf<String?>("")
        private set

    var currentTaskTitleResId by mutableStateOf(R.string.game_task_unknown)
        private set

    var levelCount by mutableStateOf(0)
        private set
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
    var availableCards = mutableStateListOf<AvailableCardSlot>()
        private set
    var assemblyLine = mutableStateListOf<AssemblySlot>()
        private set
    var selectedCards = mutableStateListOf<Card>()
        private set
    var isLastRoundAvailable by mutableStateOf(false)
        private set
    var showResultSheet by mutableStateOf(false)
        private set
    var errorCardId by mutableStateOf<UUID?>(null)
        private set
    var isRoundWon by mutableStateOf(false)
        private set
    var gameFontStyle by mutableStateOf(FontStyle.REGULAR)
        private set
    var currentTaskType by mutableStateOf(TaskType.UNKNOWN)
        private set

    private var wordDictionary = mapOf<String, String>()
    private val _hapticEventChannel = Channel<HapticEvent>()
    val hapticEvents = _hapticEventChannel.receiveAsFlow()
    private var timerJob: Job? = null
    private val _navigationEvent = Channel<String>()
    val navigationEvent = _navigationEvent.receiveAsFlow()

    fun loadLevelCount() {
        viewModelScope.launch(Dispatchers.IO) {
            val count = levelRepository.getLevelCount()
            Log.d(AppDebug.TAG, "CardViewModel: Level count loaded: $count")
            levelCount = count
        }
    }

    fun toggleGameFontStyle() {
        if (currentLevelId == 1) {
            val newStyle = if (gameFontStyle == FontStyle.CURSIVE) FontStyle.REGULAR else FontStyle.CURSIVE
            gameFontStyle = newStyle
            progressManager.saveLevel1FontStyle(newStyle)
        }
    }

    fun resetAllProgress() {
        progressManager.resetAllProgressExceptLanguage()
    }

    fun hideResultSheet() {
        showResultSheet = false
    }

    fun showResultSheet() {
        if (isRoundWon) {
            showResultSheet = true
        }
    }

    fun selectCard(slot: AvailableCardSlot) {
        if (!slot.isVisible) return
        val card = slot.card

        var isCorrect = false
        var targetSlotIndex = -1

        when (currentTaskType) {
            TaskType.ASSEMBLE_TRANSLATION -> {
                val nextExpectedCard = targetCards.getOrNull(selectedCards.size)
                isCorrect = (nextExpectedCard != null && card.text.trim() == nextExpectedCard.text.trim())
            }
            TaskType.FILL_IN_BLANK -> {
                targetSlotIndex = assemblyLine.indexOfFirst { it.isBlank && it.filledCard == null }
                if (targetSlotIndex != -1) {
                    val targetSlot = assemblyLine[targetSlotIndex]
                    isCorrect = (targetSlot.targetCard?.text?.trim() == card.text.trim())
                }
            }
            TaskType.MATCHING_PAIRS, TaskType.UNKNOWN -> {}
        }


        viewModelScope.launch {
            if (isCorrect) {
                _hapticEventChannel.send(HapticEvent.Success)
            } else {
                _hapticEventChannel.send(HapticEvent.Failure)
            }
        }

        if (isCorrect) {
            val indexInAvailable = availableCards.indexOfFirst { it.id == slot.id }
            if (indexInAvailable != -1) {
                availableCards[indexInAvailable] = slot.copy(isVisible = false)
            }

            when (currentTaskType) {
                TaskType.ASSEMBLE_TRANSLATION -> {
                    selectedCards.add(card)
                }
                TaskType.FILL_IN_BLANK -> {
                    if (targetSlotIndex != -1) {
                        assemblyLine[targetSlotIndex] = assemblyLine[targetSlotIndex].copy(filledCard = card)
                    }
                }
                TaskType.MATCHING_PAIRS, TaskType.UNKNOWN -> {}
            }

            checkWinCondition()

        } else {
            errorCount++
            errorCardId = card.id
        }
    }

    fun returnCardFromSlot(slot: AssemblySlot) {
        if (isRoundWon || currentTaskType != TaskType.FILL_IN_BLANK) return

        val card = slot.filledCard ?: return

        when (currentTaskType) {
            TaskType.ASSEMBLE_TRANSLATION -> {
                // (Этот код никогда не вызовется, т.к. мы проверяем FILL_IN_BLANK выше,
                // но оставляем для полноты)
            }
            TaskType.FILL_IN_BLANK -> {
                val index = assemblyLine.indexOfFirst { it.id == slot.id }
                if (index != -1) {
                    assemblyLine[index] = slot.copy(filledCard = null)
                }
            }
            TaskType.MATCHING_PAIRS, TaskType.UNKNOWN -> {}
        }

        val indexInAvailable = availableCards.indexOfFirst { it.card.id == card.id }
        if (indexInAvailable != -1) {
            availableCards[indexInAvailable] = availableCards[indexInAvailable].copy(isVisible = true)
        }
    }

    fun returnLastSelectedCard() {
        if (isRoundWon || currentTaskType != TaskType.ASSEMBLE_TRANSLATION) return

        val cardToReturn = selectedCards.lastOrNull() ?: return

        selectedCards.remove(cardToReturn)

        val indexInAvailable = availableCards.indexOfFirst { it.card.id == cardToReturn.id }
        if (indexInAvailable != -1) {
            availableCards[indexInAvailable] = availableCards[indexInAvailable].copy(isVisible = true)
        }
    }

    private fun checkWinCondition() {
        val didWin = when (currentTaskType) {
            TaskType.ASSEMBLE_TRANSLATION -> {
                selectedCards.size == targetCards.size
            }
            TaskType.FILL_IN_BLANK -> {
                assemblyLine.none { it.isBlank && it.filledCard == null }
            }
            TaskType.MATCHING_PAIRS, TaskType.UNKNOWN -> false
        }

        if (didWin) {
            endRound(GameResult.WIN)
        }
    }

    suspend fun loadLevel(levelId: Int): Boolean {
        val levelData = levelRepository.getLevelData(levelId) ?: return false

        this.currentLevelSentences = levelData
        this.currentLevelId = levelId

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
                } ?: "")
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

        // --- ИСПРАВЛЕНИЕ 1: ОТПРАВЛЯЕМ НАВИГАЦИЮ ДЛЯ ЛЮБОГО ТИПА ---
        val currentRoundData = currentLevelSentences.getOrNull(currentRoundIndex)
        viewModelScope.launch {
            if (currentRoundData?.taskType == TaskType.MATCHING_PAIRS) {
                _navigationEvent.send("matching_game/${currentLevelId}/${currentRoundIndex}")
            } else {
                // (ASSEMBLE_TRANSLATION или FILL_IN_BLANK)
                _navigationEvent.send("game")
            }
        }
        // ----------------------------------------------------

        return false
    }

    fun loadRound(roundIndex: Int) {
        resultSnapshot = null
        isRoundWon = false
        if (roundIndex < currentLevelSentences.size) {
            this.currentRoundIndex = roundIndex
            resetAndStartCounters()

            val roundData = currentLevelSentences[roundIndex]
            val userLanguage = progressManager.getUserLanguage()

            currentTaskType = roundData.taskType
            currentTaskPrompt = when (userLanguage) {
                "en" -> roundData.english_translation
                "fr" -> roundData.french_translation
                "es" -> roundData.spanish_translation
                else -> roundData.russian_translation
            }

            assemblyLine.clear()
            selectedCards.clear()
            availableCards.clear()

            when (roundData.taskType) {

                TaskType.ASSEMBLE_TRANSLATION -> {
                    currentTaskTitleResId = R.string.game_task_assemble
                    currentHebrewPrompt = ""
                    this.targetCards = parseSentenceToCards(roundData.hebrew, wordDictionary)

                    availableCards.addAll(
                        targetCards.shuffled().map {
                            AvailableCardSlot(card = it, isVisible = true)
                        }
                    )
                }

                TaskType.FILL_IN_BLANK -> {
                    currentTaskTitleResId = R.string.game_task_fill_in_blank
                    currentHebrewPrompt = roundData.hebrew

                    val correctCards = roundData.task_correct_cards?.map {
                        Card(text = it.trim(), translation = "")
                    } ?: emptyList()
                    this.targetCards = correctCards

                    val hebrewPrompt = roundData.hebrew
                    val promptParts = hebrewPrompt.split("___")
                    var correctCardIndex = 0

                    promptParts.forEachIndexed { index, part ->
                        if (part.isNotEmpty()) {
                            assemblyLine.add(AssemblySlot(text = part, isBlank = false, filledCard = null, targetCard = null))
                        }

                        if (index < promptParts.size - 1) {
                            if (correctCardIndex < correctCards.size) {
                                assemblyLine.add(AssemblySlot(
                                    text = "___",
                                    isBlank = true,
                                    filledCard = null,
                                    targetCard = correctCards[correctCardIndex]
                                ))
                                correctCardIndex++
                            } else {
                                Log.e("ViewModel", "Mismatch in FILL_IN_BLANK: More '___' than 'task_correct_cards'.")
                            }
                        }
                    }

                    val distractors = roundData.task_distractor_cards?.map {
                        Card(text = it.trim(), translation = "")
                    } ?: emptyList()

                    availableCards.addAll(
                        (correctCards + distractors).shuffled().map {
                            AvailableCardSlot(card = it, isVisible = true)
                        }
                    )
                }

                TaskType.MATCHING_PAIRS -> {
                    currentTaskTitleResId = R.string.game_task_matching
                    currentHebrewPrompt = ""
                    this.targetCards = emptyList()
                    // Фактический запуск MatchingGameScreen происходит через NavigationEvent в loadLevel
                }

                TaskType.UNKNOWN -> {
                    currentTaskTitleResId = R.string.game_task_unknown
                    currentTaskPrompt = "ОШИБКА: Тип задания не распознан. (${roundData.taskType})"
                    currentHebrewPrompt = ""
                    this.targetCards = emptyList()
                }
            }

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

        val completedCardsList = when (currentTaskType) {
            TaskType.ASSEMBLE_TRANSLATION -> selectedCards.toList()
            TaskType.FILL_IN_BLANK -> assemblyLine.mapNotNull { it.filledCard ?: it.targetCard }
            TaskType.MATCHING_PAIRS, TaskType.UNKNOWN -> emptyList()
        }

        resultSnapshot = RoundResultSnapshot(
            gameResult = result,
            completedCards = completedCardsList,
            errorCount = this.errorCount,
            timeSpent = this.timeSpent,
            levelId = this.currentLevelId,
            hasMoreRounds = hasMoreRounds,
            audioFilename = if (result == GameResult.WIN) currentSentence?.audioFilename else null
        )

        viewModelScope.launch {
            delay(650)
            showResultSheet = true
            if (result == GameResult.WIN) {
                resultSnapshot?.audioFilename?.let { audioPlayer.play(it) }
            }
        }
    }

    fun restartCurrentRound() {
        if (isLevelFullyCompleted) isLevelFullyCompleted = false
        loadRound(currentRoundIndex)
    }

    // --- ИСПРАВЛЕНИЕ 2: Сброс уровня должен также отправлять событие навигации ---
    fun resetCurrentLevelProgress() {
        progressManager.resetLevelProgress(currentLevelId)
        isLevelFullyCompleted = false
        // Загружаем Раунд 0
        loadRound(0)

        // Отправляем событие навигации, как в loadLevel
        val currentRoundData = currentLevelSentences.getOrNull(0)
        viewModelScope.launch {
            if (currentRoundData?.taskType == TaskType.MATCHING_PAIRS) {
                _navigationEvent.send("matching_game/${currentLevelId}/0")
            } else {
                _navigationEvent.send("game")
            }
        }
    }
    // -----------------------------------------------------------------

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

        // --- Проверяем тип задания после загрузки нового раунда ---
        val nextRoundData = currentLevelSentences.getOrNull(nextRound)
        viewModelScope.launch {
            if (nextRoundData?.taskType == TaskType.MATCHING_PAIRS) {
                _navigationEvent.send("matching_game/${currentLevelId}/${nextRound}")
            } else {
                _navigationEvent.send("game")
            }
        }
        // -----------------------------------------------------------------------
    }

    fun skipToNextAvailableRound() {
        val completedRounds = progressManager.getCompletedRounds(currentLevelId)
        val archivedRounds = progressManager.getArchivedRounds(currentLevelId)

        val activeRounds = currentLevelSentences.indices.filter {
            !completedRounds.contains(it) && !archivedRounds.contains(it)
        }

        if (activeRounds.isEmpty()) return
        val currentIndexInActiveList = activeRounds.indexOf(currentRoundIndex)
        val nextIndexInActiveList = if(currentIndexInActiveList != -1) (currentIndexInActiveList + 1) % activeRounds.size else 0
        loadRound(activeRounds[nextIndexInActiveList])

        // --- Проверяем тип задания после перехода ---
        val nextRound = activeRounds[nextIndexInActiveList]
        val nextRoundData = currentLevelSentences.getOrNull(nextRound)
        viewModelScope.launch {
            if (nextRoundData?.taskType == TaskType.MATCHING_PAIRS) {
                _navigationEvent.send("matching_game/${currentLevelId}/${nextRound}")
            } else {
                _navigationEvent.send("game")
            }
        }
        // --------------------------------------------------------
    }

    companion object {
        private const val TAG = "VIBRATE_DEBUG"
    }
}