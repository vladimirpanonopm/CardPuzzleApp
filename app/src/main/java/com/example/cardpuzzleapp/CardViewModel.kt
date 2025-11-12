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

    // --- ШАГ 1: НОВЫЙ ЕДИНЫЙ UI STATE ---
    data class GameUiState(
        val isRoundWon: Boolean = false,
        val errorCount: Int = 0,
        val errorCardId: UUID? = null,
        val selectedCards: List<Card> = emptyList(),
        val assemblyLine: List<AssemblySlot> = emptyList(),
        val availableCards: List<AvailableCardSlot> = emptyList(),
        val fontStyle: FontStyle = FontStyle.REGULAR,
        val resultSnapshot: RoundResultSnapshot? = null,
        val showResultSheet: Boolean = false
    )

    // --- ШАГ 2: ЗАМЕНА СТАРЫХ СОСТОЯНИЙ ---
    var uiState by mutableStateOf(GameUiState())
        private set

    // --- Статичные/Глобальные состояния (остаются без изменений) ---
    var currentTaskPrompt by mutableStateOf<String?>("")
        private set
    var currentHebrewPrompt by mutableStateOf<String?>("")
        private set
    var currentTaskTitleResId by mutableStateOf(R.string.game_task_unknown)
        private set

    var currentTaskType by mutableStateOf(TaskType.UNKNOWN)
        private set

    var levelCount by mutableStateOf(0)
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
    var isLastRoundAvailable by mutableStateOf(false)
        private set
    // --- Конец Шага 2 ---

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

    // --- ШАГ 3: Обновление методов для работы с uiState ---
    fun toggleGameFontStyle() {
        if (currentLevelId == 1) {
            val newStyle = if (uiState.fontStyle == FontStyle.CURSIVE) FontStyle.REGULAR else FontStyle.CURSIVE
            // ИЗМЕНЕНО:
            uiState = uiState.copy(fontStyle = newStyle)
            progressManager.saveLevel1FontStyle(newStyle)
        }
    }

    fun resetAllProgress() {
        progressManager.resetAllProgressExceptLanguage()
    }

    fun hideResultSheet() {
        // ИЗМЕНЕНО:
        uiState = uiState.copy(showResultSheet = false)
    }

    fun showResultSheet() {
        // ИЗМЕНЕНО:
        if (uiState.isRoundWon) {
            uiState = uiState.copy(showResultSheet = true)
        }
    }

    fun selectCard(slot: AvailableCardSlot) {
        if (!slot.isVisible) {
            Log.w(AppDebug.TAG, "selectCard: GHOST TAP DETECTED on invisible card. Ignoring.")
            return
        }

        val card = slot.card
        var isCorrect = false
        var targetSlotIndex = -1

        // ИЗМЕНЕНО: Читаем 'currentTaskType' из ViewModel (он статичен для раунда)
        when (currentTaskType) {
            TaskType.ASSEMBLE_TRANSLATION -> {
                // ИЗМЕНЕНО: Читаем 'selectedCards' из 'uiState'
                val nextExpectedCard = targetCards.getOrNull(uiState.selectedCards.size)
                isCorrect = (nextExpectedCard != null && card.text.trim() == nextExpectedCard.text.trim())
            }
            TaskType.FILL_IN_BLANK -> {
                // ИЗМЕНЕНО: Читаем 'assemblyLine' из 'uiState'
                targetSlotIndex = uiState.assemblyLine.indexOfFirst { it.isBlank && it.filledCard == null }
                if (targetSlotIndex != -1) {
                    val targetSlot = uiState.assemblyLine[targetSlotIndex]
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

            // ИЗМЕНЕНО: Обновляем списки внутри uiState
            val newAvailableCards = uiState.availableCards.map {
                if (it.id == slot.id) it.copy(isVisible = false) else it
            }

            when (currentTaskType) {
                TaskType.ASSEMBLE_TRANSLATION -> {
                    val newSelectedCards = uiState.selectedCards + card
                    uiState = uiState.copy(
                        availableCards = newAvailableCards,
                        selectedCards = newSelectedCards
                    )
                }
                TaskType.FILL_IN_BLANK -> {
                    val newAssemblyLine = uiState.assemblyLine.toMutableList()
                    if (targetSlotIndex != -1) {
                        newAssemblyLine[targetSlotIndex] = newAssemblyLine[targetSlotIndex].copy(filledCard = card)
                    }
                    uiState = uiState.copy(
                        availableCards = newAvailableCards,
                        assemblyLine = newAssemblyLine
                    )
                }
                TaskType.MATCHING_PAIRS, TaskType.UNKNOWN -> {
                    // На случай, если нужно обновить только availableCards
                    uiState = uiState.copy(availableCards = newAvailableCards)
                }
            }

            checkWinCondition()

        } else {
            viewModelScope.launch {
                _hapticEventChannel.send(HapticEvent.Failure)
            }
            // ИЗМЕНЕНО:
            uiState = uiState.copy(
                errorCount = uiState.errorCount + 1,
                errorCardId = card.id
            )

            ttsPlayer.speak(card.text.trim())
        }
    }

    fun returnCardFromSlot(slot: AssemblySlot) {
        // ИЗМЕНЕНО:
        if (uiState.isRoundWon || currentTaskType != TaskType.FILL_IN_BLANK) return

        val card = slot.filledCard ?: return
        ttsPlayer.speak(card.text.trim())

        // ИЗМЕНЕНО:
        val newAssemblyLine = uiState.assemblyLine.map {
            if (it.id == slot.id) it.copy(filledCard = null) else it
        }
        val newAvailableCards = uiState.availableCards.map {
            if (it.card.id == card.id) it.copy(isVisible = true) else it
        }

        uiState = uiState.copy(
            assemblyLine = newAssemblyLine,
            availableCards = newAvailableCards
        )
    }

    fun returnLastSelectedCard() {
        // ИЗМЕНЕНО:
        if (uiState.isRoundWon || currentTaskType != TaskType.ASSEMBLE_TRANSLATION) return

        val cardToReturn = uiState.selectedCards.lastOrNull() ?: return
        ttsPlayer.speak(cardToReturn.text.trim())

        // ИЗМЕНЕНО:
        val newSelectedCards = uiState.selectedCards.dropLast(1)
        val newAvailableCards = uiState.availableCards.map {
            if (it.card.id == cardToReturn.id) it.copy(isVisible = true) else it
        }

        uiState = uiState.copy(
            selectedCards = newSelectedCards,
            availableCards = newAvailableCards
        )
    }

    private fun checkWinCondition() {
        // ИЗМЕНЕНО:
        val didWin = when (currentTaskType) {
            TaskType.ASSEMBLE_TRANSLATION -> {
                uiState.selectedCards.size == targetCards.size
            }
            TaskType.FILL_IN_BLANK -> {
                uiState.assemblyLine.none { it.isBlank && it.filledCard == null }
            }
            TaskType.MATCHING_PAIRS, TaskType.UNKNOWN -> false
        }

        if (didWin) {
            endRound(GameResult.WIN)
        }
    }

    // --- ИЗМЕНЕНИЕ ДЛЯ "ПРОБЛЕМЫ 2" ---
    suspend fun loadLevel(levelId: Int): Boolean {
        Log.d(AppDebug.TAG, "loadLevel($levelId): CALLED")

        // 1. УБИРАЕМ clearCache()
        // levelRepository.clearCache() // <-- УДАЛЕНО

        // 2. ВЫЗЫВАЕМ НОВЫЙ МЕТОД ЗАГРУЗКИ
        levelRepository.loadLevelDataIfNeeded(levelId)

        // 3. ВЫЗЫВАЕМ НОВЫЙ СИНХРОННЫЙ GETTER
        val levelData = levelRepository.getSentencesForLevel(levelId)

        Log.d(AppDebug.TAG, "loadLevel($levelId): levelData is ${if (levelData.isEmpty()) "EMPTY" else "OK (${levelData.size} sentences)"}")

        if (levelData.isEmpty()) {
            Log.e(AppDebug.TAG, "loadLevel($levelId): FAILED. levelData is empty.")
            return false
        }

        this.currentLevelSentences = levelData
        this.currentLevelId = levelId

        val newFontStyle = if (levelId == 1) progressManager.getLevel1FontStyle() else FontStyle.REGULAR
        uiState = uiState.copy(fontStyle = newFontStyle)

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
    // --- КОНЕЦ ИЗМЕНЕНИЯ ---

    fun loadRound(roundIndex: Int) {
        Log.d(AppDebug.TAG, "CardViewModel: loadRound($roundIndex) - ЗАГРУЗКА ДАННЫХ (вызван из GameScreen)")

        // --- ИЗМЕНЕНИЕ ДЛЯ "ПРОБЛЕМЫ 2" ---
        // Данные УЖЕ в кэше, просто берем их
        val roundData = levelRepository.getSingleSentence(currentLevelId, roundIndex)
        // --- КОНЕЦ ИЗМЕНЕНИЯ ---

        if (roundData == null) {
            Log.e(AppDebug.TAG, "CardViewModel: loadRound($roundIndex) - FAILED. roundData is NULL from repository.")
            return
        }

        val newAssemblyLine = mutableListOf<AssemblySlot>()
        val newAvailableCards = mutableListOf<AvailableCardSlot>()
        var newTargetCards: List<Card>
        var newTaskTitleResId: Int
        var newCurrentTaskPrompt: String?
        var newCurrentHebrewPrompt: String?

        val newTaskType = roundData.taskType
        this.currentRoundIndex = roundIndex
        this.currentTaskType = newTaskType // Обновляем статическое поле

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
            // ... (остальные when)
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

        // 1. Обновляем статичные поля
        this.targetCards = newTargetCards
        this.currentTaskPrompt = newCurrentTaskPrompt
        this.currentHebrewPrompt = newCurrentHebrewPrompt
        this.currentTaskTitleResId = newTaskTitleResId

        // 2. Сбрасываем uiState до нового чистого состояния
        uiState = uiState.copy(
            isRoundWon = false,
            errorCount = 0,
            errorCardId = null,
            selectedCards = emptyList(),
            assemblyLine = newAssemblyLine,
            availableCards = newAvailableCards,
            resultSnapshot = null,
            showResultSheet = false
            // fontStyle НЕ сбрасываем, он сохраняется
        )


        Log.d(AppDebug.TAG, "CardViewModel: loadRound($roundIndex) - ATOMIC UPDATE COMPLETE.")
        // ------------------------------


        val allCompleted = progressManager.getCompletedRounds(currentLevelId)
        val allArchived = progressManager.getArchivedRounds(currentLevelId)
        val uncompletedRounds = currentLevelSentences.indices.filter {
            !allCompleted.contains(it) && !allArchived.contains(it)
        }
        isLastRoundAvailable = uncompletedRounds.size <= 1
    }

    private fun resetAndStartCounters() {
        // ИЗМЕНЕНО: 'errorCount' сбрасывается в 'loadRound'
        timeSpent = 0
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

        val userLanguage = progressManager.getUserLanguage()
        // --- ИЗМЕНЕНИЕ ДЛЯ "ПРОБЛЕМЫ 2" ---
        val currentSentence = levelRepository.getSingleSentence(currentLevelId, currentRoundIndex)
        // --- КОНЕЦ ИЗМЕНЕНИЯ ---

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
            TaskType.ASSEMBLE_TRANSLATION -> uiState.selectedCards.toList()
            TaskType.FILL_IN_BLANK -> uiState.assemblyLine.mapNotNull { it.filledCard ?: it.targetCard }
            TaskType.MATCHING_PAIRS, TaskType.UNKNOWN -> emptyList()
        }

        val newSnapshot = RoundResultSnapshot(
            gameResult = result,
            completedCards = completedCardsList,
            errorCount = this.uiState.errorCount, // ИЗМЕНЕНО
            timeSpent = this.timeSpent,
            levelId = this.currentLevelId,
            hasMoreRounds = hasMoreRounds,
            audioFilename = if (result == GameResult.WIN) currentSentence?.audioFilename else null
        )

        uiState = uiState.copy(
            isRoundWon = true,
            resultSnapshot = newSnapshot
        )

        viewModelScope.launch {
            delay(650)
            uiState = uiState.copy(showResultSheet = true)
            if (result == GameResult.WIN) {
                newSnapshot.audioFilename?.let { audioPlayer.play(it) }
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

    fun proceedToNextRound() {
        val completedRounds = progressManager.getCompletedRounds(currentLevelId)
        val archivedRounds = progressManager.getArchivedRounds(currentLevelId)

        val nowCompletedSet = completedRounds + currentRoundIndex

        val uncompletedRounds = (0 until currentLevelSentences.size).filter {
            !nowCompletedSet.contains(it) && !archivedRounds.contains(it)
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

        // --- ИЗМЕНЕНИЕ ДЛЯ "ПРОБЛЕМЫ 2" ---
        val nextRoundData = levelRepository.getSingleSentence(currentLevelId, nextRound)
        // --- КОНЕЦ ИЗМЕНЕНИЯ ---

        viewModelScope.launch {
            if (nextRoundData?.taskType == TaskType.MATCHING_PAIRS) {
                val uniqueId = System.currentTimeMillis()
                _navigationEvent.send("matching_game/${currentLevelId}/${nextRound}?uid=$uniqueId")
            } else {
                _navigationEvent.send("game/${currentLevelId}/${nextRound}")
            }
        }
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
        val nextRound = activeRounds[nextIndexInActiveList]

        // --- ИЗМЕНЕНИЕ ДЛЯ "ПРОБЛЕМЫ 2" ---
        val nextRoundData = levelRepository.getSingleSentence(currentLevelId, nextRound)
        // --- КОНЕЦ ИЗМЕНЕНИЯ ---

        viewModelScope.launch {
            if (nextRoundData?.taskType == TaskType.MATCHING_PAIRS) {
                val uniqueId = System.currentTimeMillis()
                _navigationEvent.send("matching_game/${currentLevelId}/${nextRound}?uid=$uniqueId")
            } else {
                _navigationEvent.send("game/${currentLevelId}/${nextRound}")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        ttsPlayer.stop()
    }

    companion object {
        private const val TAG = "VIBRATE_DEBUG"
    }
}