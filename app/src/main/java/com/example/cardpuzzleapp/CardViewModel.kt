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
    private val levelRepository: LevelRepository,
    private val ttsPlayer: TtsPlayer
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

    fun updateCurrentRoundIndex(index: Int) {
        this.currentRoundIndex = index
        this.currentTaskType = currentLevelSentences.getOrNull(index)?.taskType ?: TaskType.UNKNOWN
    }

    fun loadLevelCount() {
        viewModelScope.launch(Dispatchers.IO) {
            levelCount = levelRepository.getLevelCount()
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
                    isCorrect = (targetSlot.targetCard?.text?.trim() == card.text?.trim())
                }
            }
            TaskType.MATCHING_PAIRS, TaskType.UNKNOWN -> {}
        }

        if (isCorrect) {
            ttsPlayer.speak(card.text.trim())

            viewModelScope.launch {
                _hapticEventChannel.send(HapticEvent.Success)
            }

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
            viewModelScope.launch {
                _hapticEventChannel.send(HapticEvent.Failure)
            }
            errorCount++
            errorCardId = card.id

            ttsPlayer.speak(card.text.trim())
        }
    }

    fun returnCardFromSlot(slot: AssemblySlot) {
        if (isRoundWon || currentTaskType != TaskType.FILL_IN_BLANK) return

        val card = slot.filledCard ?: return

        ttsPlayer.speak(card.text.trim())

        when (currentTaskType) {
            TaskType.ASSEMBLE_TRANSLATION -> {}
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

        ttsPlayer.speak(cardToReturn.text.trim())

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
        Log.d(AppDebug.TAG, "loadLevel($levelId): CALLED")

        levelRepository.clearCache()

        val levelData = levelRepository.getLevelData(levelId)

        Log.d(AppDebug.TAG, "loadLevel($levelId): levelData is ${if (levelData == null) "NULL" else "OK (${levelData.size} sentences)"}")

        if (levelData == null) {
            Log.e(AppDebug.TAG, "loadLevel($levelId): FAILED. levelData is null.")
            return false
        }

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

        Log.d(AppDebug.TAG, "loadLevel($levelId): nextRoundToPlay = $nextRoundToPlay. (isLevelFullyCompleted = $isLevelFullyCompleted)")

        if (nextRoundToPlay != null) {
            this.currentRoundIndex = nextRoundToPlay
            this.currentTaskType = currentLevelSentences.getOrNull(nextRoundToPlay)?.taskType ?: TaskType.UNKNOWN
        } else {
            isLevelFullyCompleted = true
            return true
        }

        val currentRoundData = currentLevelSentences.getOrNull(currentRoundIndex)
        viewModelScope.launch {
            if (currentRoundData?.taskType == TaskType.MATCHING_PAIRS) {
                val uniqueId = System.currentTimeMillis()
                Log.d(AppDebug.TAG, "loadLevel($levelId): Sending nav event: 'matching_game/${currentLevelId}/${currentRoundIndex}?uid=$uniqueId'")
                _navigationEvent.send("matching_game/${currentLevelId}/${currentRoundIndex}?uid=$uniqueId")
            } else {
                Log.d(AppDebug.TAG, "loadLevel($levelId): Sending nav event: 'game/${currentLevelId}/${currentRoundIndex}'")
                _navigationEvent.send("game/${currentLevelId}/${currentRoundIndex}")
            }
        }

        return false
    }

    // --- ИЗМЕНЕНИЕ: ЛОГИКА АТОМАРНОГО ОБНОВЛЕНИЯ ---
    fun loadRound(roundIndex: Int) {
        Log.d(AppDebug.TAG, "CardViewModel: loadRound($roundIndex) - ЗАГРУЗКА ДАННЫХ (вызван из GameScreen)")

        if (roundIndex >= currentLevelSentences.size) {
            Log.e(AppDebug.TAG, "CardViewModel: loadRound($roundIndex) - FAILED. Index out of bounds.")
            return
        }

        val roundData = currentLevelSentences[roundIndex]

        // --- ATOMIC UPDATE: Step 1 ---
        // Создаем *новые* списки. Не трогаем старые.
        val newAssemblyLine = mutableListOf<AssemblySlot>()
        val newAvailableCards = mutableListOf<AvailableCardSlot>()

        // --- ИСПРАВЛЕНИЕ: МЕНЯЕМ 'val' НА 'var' ---
        var newTargetCards: List<Card>
        var newTaskTitleResId: Int
        var newCurrentTaskPrompt: String?
        var newCurrentHebrewPrompt: String?
        // --- КОНЕЦ ИСПРАВЛЕНИЯ ---

        val newRoundIndex = roundIndex
        val newTaskType = roundData.taskType


        // Сброс таймеров
        resetAndStartCounters()
        val userLanguage = progressManager.getUserLanguage()

        newCurrentTaskPrompt = when (userLanguage) {
            "en" -> roundData.english_translation
            "fr" -> roundData.french_translation
            "es" -> roundData.spanish_translation
            else -> roundData.russian_translation
        }

        when (roundData.taskType) {
            TaskType.ASSEMBLE_TRANSLATION -> {
                newTaskTitleResId = R.string.game_task_assemble
                newCurrentHebrewPrompt = ""
                newTargetCards = parseSentenceToCards(roundData.hebrew, wordDictionary)

                newAvailableCards.addAll(
                    newTargetCards.shuffled().map {
                        AvailableCardSlot(card = it, isVisible = true)
                    }
                )
            }

            TaskType.FILL_IN_BLANK -> {
                newTaskTitleResId = R.string.game_task_fill_in_blank
                newCurrentHebrewPrompt = roundData.hebrew

                val correctCards = roundData.task_correct_cards?.map {
                    Card(text = it.trim(), translation = "")
                } ?: emptyList()
                newTargetCards = correctCards

                val hebrewPrompt = roundData.hebrew
                val promptParts = hebrewPrompt.split("___")
                var correctCardIndex = 0

                promptParts.forEachIndexed { index, part ->
                    if (part.isNotEmpty()) {
                        newAssemblyLine.add(AssemblySlot(text = part, isBlank = false, filledCard = null, targetCard = null))
                    }
                    if (index < promptParts.size - 1) {
                        if (correctCardIndex < correctCards.size) {
                            newAssemblyLine.add(AssemblySlot(
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

                newAvailableCards.addAll(
                    (correctCards + distractors).shuffled().map {
                        AvailableCardSlot(card = it, isVisible = true)
                    }
                )
            }

            TaskType.MATCHING_PAIRS -> {
                Log.w(AppDebug.TAG, "CardViewModel: loadRound($roundIndex) - ОШИБКА, ЭТО MATCHING_PAIRS.")
                newTaskTitleResId = R.string.game_task_matching
                newCurrentHebrewPrompt = ""
                newTargetCards = emptyList()
            }

            TaskType.UNKNOWN -> {
                newTaskTitleResId = R.string.game_task_unknown
                newCurrentTaskPrompt = "ОШИБКА: Тип задания не распознан. (${roundData.taskType})"
                newCurrentHebrewPrompt = ""
                newTargetCards = emptyList()
            }
        }

        // --- ATOMIC UPDATE: Step 2 ---
        // Применяем ВСЕ изменения ОДНОВРЕМЕННО
        // Это вызовет *только одну* recomposition
        this.resultSnapshot = null
        this.isRoundWon = false
        this.selectedCards.clear() // Этот можно очистить, он используется только для ASSEMBLE

        this.targetCards = newTargetCards
        this.assemblyLine.clear()
        this.assemblyLine.addAll(newAssemblyLine)
        this.availableCards.clear()
        this.availableCards.addAll(newAvailableCards)

        this.currentTaskPrompt = newCurrentTaskPrompt
        this.currentHebrewPrompt = newCurrentHebrewPrompt
        this.currentTaskTitleResId = newTaskTitleResId
        this.currentTaskType = newTaskType
        this.currentRoundIndex = newRoundIndex // Это должно быть в конце

        Log.d(AppDebug.TAG, "CardViewModel: loadRound($roundIndex) - ATOMIC UPDATE COMPLETE.")
        // ------------------------------


        val allCompleted = progressManager.getCompletedRounds(currentLevelId)
        val allArchived = progressManager.getArchivedRounds(currentLevelId)
        val uncompletedRounds = currentLevelSentences.indices.filter {
            !allCompleted.contains(it) && !allArchived.contains(it)
        }
        isLastRoundAvailable = uncompletedRounds.size <= 1
    }
    // --- КОНЕЦ ИЗМЕНЕНИЯ ---

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

    fun resetCurrentLevelProgress() {
        progressManager.resetLevelProgress(currentLevelId)
        isLevelFullyCompleted = false
        viewModelScope.launch {
            loadLevel(currentLevelId)
        }
    }

    // --- ИЗМЕНЕНИЕ: ВОЗВРАЩАЕМ К СТАРОЙ ЛОГИКЕ ---
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

        val nextRoundData = currentLevelSentences.getOrNull(nextRound)

        viewModelScope.launch {
            if (nextRoundData?.taskType == TaskType.MATCHING_PAIRS) {
                val uniqueId = System.currentTimeMillis()
                _navigationEvent.send("matching_game/${currentLevelId}/${nextRound}?uid=$uniqueId")
            } else {
                _navigationEvent.send("game/${currentLevelId}/${nextRound}")
            }
        }
    }
    // --- КОНЕЦ ИЗМЕНЕНИЯ ---

    // --- ИЗМЕНЕНИЕ: ВОЗВРАЩАЕМ К СТАРОЙ ЛОГИКЕ ---
    fun skipToNextAvailableRound() {
        val completedRounds = progressManager.getCompletedRounds(currentLevelId)
        val archivedRounds = progressManager.getArchivedRounds(currentLevelId)

        val activeRounds = currentLevelSentences.indices.filter {
            !completedRounds.contains(it) && !archivedRounds.contains(it)
        }

        if (activeRounds.isEmpty()) return

        val currentIndexInActiveList = activeRounds.indexOf(currentRoundIndex)

        val nextIndexInActiveList = if(currentIndexInActiveList != -1) (currentIndexInActiveList + 1) % activeRounds.size else 0

        val nextRound = activeRounds[nextIndexInActiveList]

        val nextRoundData = currentLevelSentences.getOrNull(nextRound)

        viewModelScope.launch {
            if (nextRoundData?.taskType == TaskType.MATCHING_PAIRS) {
                val uniqueId = System.currentTimeMillis()
                _navigationEvent.send("matching_game/${currentLevelId}/${nextRound}?uid=$uniqueId")
            } else {
                _navigationEvent.send("game/${currentLevelId}/${nextRound}")
            }
        }
    }
    // --- КОНЕЦ ИЗМЕНЕНИЯ ---

    override fun onCleared() {
        super.onCleared()
        ttsPlayer.stop()
    }

    companion object {
        private const val TAG = "VIBRATE_DEBUG"
    }
}