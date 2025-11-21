package com.example.cardpuzzleapp

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers

// ЕДИНЫЙ ТЕГ ДЛЯ ОТЛАДКИ ЗВУКА
private const val TAG = "AUDIO_DEBUG"

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

    private val partsRegex = Regex("""([\u0590-\u05FF\']+)|\n|([.,:?!\s])""")

    data class GameUiState(
        val isRoundWon: Boolean = false,
        val errorCount: Int = 0,
        val errorCardId: UUID? = null,
        val assemblyLine: List<AssemblySlot> = emptyList(),
        val availableCards: List<AvailableCardSlot> = emptyList(),
        val fontStyle: FontStyle = FontStyle.REGULAR,
        val resultSnapshot: RoundResultSnapshot? = null,
        val showResultSheet: Boolean = false,
        val isAudioPlaying: Boolean = false,
        val playingSegmentIndex: Int = -1,
        val isSlowMode: Boolean = false
    )

    var uiState by mutableStateOf(GameUiState())
        private set

    var currentTaskPrompt by mutableStateOf<String?>("")
        private set
    var currentHebrewPrompt by mutableStateOf<String?>("")
        private set
    var currentTaskTitleResId by mutableStateOf(R.string.game_task_unknown)
        private set

    var currentTaskType by mutableStateOf(TaskType.UNKNOWN)
        private set

    var currentSegments by mutableStateOf<List<AudioSegment>?>(null)
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

    private var wordDictionary = mapOf<String, String>()
    private val _hapticEventChannel = Channel<HapticEvent>()
    val hapticEvents = _hapticEventChannel.receiveAsFlow()
    private var timerJob: Job? = null

    private val _navigationEvent = Channel<NavigationEvent>()
    val navigationEvent = _navigationEvent.receiveAsFlow()

    private var desiredSegmentIndex: Int = -1

    init {
        viewModelScope.launch {
            audioPlayer.isPlaying.collect { isPlaying ->
                val uiIndex = if (isPlaying) desiredSegmentIndex else -1
                uiState = uiState.copy(
                    isAudioPlaying = isPlaying,
                    playingSegmentIndex = uiIndex
                )
            }
        }
    }

    fun toggleSlowMode() {
        uiState = uiState.copy(isSlowMode = !uiState.isSlowMode)
    }

    fun playFullAudio() {
        val currentSentence = levelRepository.getSingleSentence(currentLevelId, currentRoundIndex)
        currentSentence?.audioFilename?.let { filename ->
            desiredSegmentIndex = -1
            val speed = if (uiState.isSlowMode) 0.75f else 1.0f
            audioPlayer.play(filename, speed)
        }
    }

    fun playAudioSegment(index: Int) {
        val currentSentence = levelRepository.getSingleSentence(currentLevelId, currentRoundIndex)
        val segment = currentSentence?.segments?.getOrNull(index)
        val filename = currentSentence?.audioFilename

        if (segment != null && filename != null) {
            desiredSegmentIndex = index
            val speed = if (uiState.isSlowMode) 0.75f else 1.0f
            audioPlayer.playSegment(filename, segment.startMs, segment.endMs, speed)
        }
    }

    fun updateCurrentRoundIndex(index: Int) {
        this.currentRoundIndex = index
        val sentence = currentLevelSentences.getOrNull(index)
        this.currentTaskType = sentence?.taskType ?: TaskType.UNKNOWN
        this.currentSegments = sentence?.segments
    }

    fun getTaskTypeForRound(roundIndex: Int): TaskType {
        return currentLevelSentences.getOrNull(roundIndex)?.taskType ?: TaskType.UNKNOWN
    }

    fun loadLevelCount() {
        viewModelScope.launch(Dispatchers.IO) {
            levelCount = levelRepository.getLevelCount()
        }
    }

    fun toggleGameFontStyle() {
        if (currentLevelId == 1) {
            val newStyle = if (uiState.fontStyle == FontStyle.CURSIVE) FontStyle.REGULAR else FontStyle.CURSIVE
            uiState = uiState.copy(fontStyle = newStyle)
            progressManager.saveLevel1FontStyle(newStyle)
        }
    }

    // --- ИСПРАВЛЕНИЕ: Используем правильный метод менеджера ---
    fun resetAllProgress() {
        progressManager.resetAllProgressExceptLanguage()
    }
    // --- КОНЕЦ ИСПРАВЛЕНИЯ ---

    fun hideResultSheet() {
        uiState = uiState.copy(showResultSheet = false)
    }

    fun showResultSheet() {
        if (uiState.isRoundWon) {
            uiState = uiState.copy(showResultSheet = true)
        }
    }

    fun selectCard(slot: AvailableCardSlot) {
        if (!slot.isVisible) return

        val card = slot.card
        var isCorrect = false
        var targetSlotIndex = -1

        when (currentTaskType) {
            TaskType.ASSEMBLE_TRANSLATION, TaskType.AUDITION, TaskType.FILL_IN_BLANK, TaskType.QUIZ -> {
                targetSlotIndex = uiState.assemblyLine.indexOfFirst { it.isBlank && it.filledCard == null }
                if (targetSlotIndex != -1) {
                    val targetSlot = uiState.assemblyLine[targetSlotIndex]
                    isCorrect = (targetSlot.targetCard?.text?.trim() == card.text.trim())
                }
            }
            TaskType.MATCHING_PAIRS, TaskType.UNKNOWN -> {}
        }

        if (isCorrect) {
            ttsPlayer.speak(card.text.trim())
            viewModelScope.launch { _hapticEventChannel.send(HapticEvent.Success) }

            val newAvailableCards = uiState.availableCards.map {
                if (it.id == slot.id) it.copy(isVisible = false) else it
            }

            when (currentTaskType) {
                TaskType.ASSEMBLE_TRANSLATION, TaskType.AUDITION, TaskType.FILL_IN_BLANK, TaskType.QUIZ -> {
                    val newAssemblyLine = uiState.assemblyLine.toMutableList()
                    if (targetSlotIndex != -1) {
                        newAssemblyLine[targetSlotIndex] = newAssemblyLine[targetSlotIndex].copy(filledCard = card)
                    }
                    uiState = uiState.copy(
                        availableCards = newAvailableCards,
                        assemblyLine = newAssemblyLine
                    )
                }
                else -> uiState = uiState.copy(availableCards = newAvailableCards)
            }
            checkWinCondition()
        } else {
            viewModelScope.launch { _hapticEventChannel.send(HapticEvent.Failure) }
            uiState = uiState.copy(
                errorCount = uiState.errorCount + 1,
                errorCardId = card.id
            )
            ttsPlayer.speak(card.text.trim())
        }
    }

    private fun checkWinCondition() {
        val didWin = when (currentTaskType) {
            TaskType.ASSEMBLE_TRANSLATION, TaskType.AUDITION, TaskType.FILL_IN_BLANK, TaskType.QUIZ -> {
                uiState.assemblyLine.none { it.isBlank && it.filledCard == null }
            }
            else -> false
        }

        if (didWin) {
            endRound(GameResult.WIN)
        }
    }

    suspend fun ensureLevelLoaded(levelId: Int) {
        if (currentLevelId == levelId && currentLevelSentences.isNotEmpty()) return

        levelRepository.loadLevelDataIfNeeded(levelId)
        val levelData = levelRepository.getSentencesForLevel(levelId)

        this.currentLevelSentences = levelData
        this.currentLevelId = levelId

        val newFontStyle = if (levelId == 1) progressManager.getLevel1FontStyle() else FontStyle.REGULAR
        uiState = uiState.copy(fontStyle = newFontStyle)

        wordDictionary = levelData
            .filter { !it.hebrew.contains(" ") }
            .associate { it.hebrew to (it.translation ?: "") }
    }

    suspend fun loadLevel(levelId: Int): Boolean {
        ensureLevelLoaded(levelId)

        if (currentLevelSentences.isEmpty()) return false

        val completedRounds = progressManager.getCompletedRounds(levelId)
        val archivedRounds = progressManager.getArchivedRounds(levelId)
        val totalRounds = currentLevelSentences.size

        if ((completedRounds.size + archivedRounds.size) >= totalRounds && totalRounds > 0) {
            isLevelFullyCompleted = true
            return true
        }

        isLevelFullyCompleted = false
        val nextRoundToPlay = (0 until totalRounds).firstOrNull { !completedRounds.contains(it) && !archivedRounds.contains(it) }

        if (nextRoundToPlay != null) {
            updateCurrentRoundIndex(nextRoundToPlay)
        } else {
            isLevelFullyCompleted = true
            return true
        }

        viewModelScope.launch {
            _navigationEvent.send(NavigationEvent.ShowRound(currentLevelId, currentRoundIndex))
        }

        return false
    }

    fun loadRound(roundIndex: Int) {
        val roundData = levelRepository.getSingleSentence(currentLevelId, roundIndex) ?: return
        val newTaskType = roundData.taskType

        updateCurrentRoundIndex(roundIndex)
        resetAndStartCounters()

        var newAssemblyLine: List<AssemblySlot> = emptyList()
        var newAvailableCards: List<AvailableCardSlot> = emptyList()
        var newTaskTitleResId: Int
        var newCurrentTaskPrompt: String? = ""
        var newCurrentHebrewPrompt: String? = ""

        when (newTaskType) {
            TaskType.ASSEMBLE_TRANSLATION -> {
                newTaskTitleResId = R.string.game_task_assemble
                newCurrentTaskPrompt = roundData.translation
                newCurrentHebrewPrompt = roundData.hebrew
                val (target, available, assembly) = setupAssemblyTask(roundData)
                this.targetCards = target
                newAvailableCards = available
                newAssemblyLine = assembly
            }
            TaskType.AUDITION -> {
                newTaskTitleResId = R.string.game_task_audition
                newCurrentTaskPrompt = ""
                newCurrentHebrewPrompt = roundData.hebrew
                val (target, available, assembly) = setupAssemblyTask(roundData)
                this.targetCards = target
                newAvailableCards = available
                newAssemblyLine = assembly
            }
            TaskType.FILL_IN_BLANK -> {
                newTaskTitleResId = R.string.game_task_fill_in_blank
                newCurrentTaskPrompt = roundData.translation
                newCurrentHebrewPrompt = roundData.hebrew
                val (target, available, assembly) = setupFillInBlankTask(roundData)
                this.targetCards = target
                newAvailableCards = available
                newAssemblyLine = assembly
            }
            TaskType.QUIZ -> {
                newTaskTitleResId = R.string.game_task_quiz
                newCurrentTaskPrompt = roundData.translation
                newCurrentHebrewPrompt = roundData.hebrew
                val (target, available, assembly) = setupQuizTask(roundData)
                this.targetCards = target
                newAvailableCards = available
                newAssemblyLine = assembly
            }
            TaskType.MATCHING_PAIRS -> {
                newTaskTitleResId = R.string.game_task_matching
                this.targetCards = emptyList()
            }
            TaskType.UNKNOWN -> {
                newTaskTitleResId = R.string.game_task_unknown
                this.targetCards = emptyList()
            }
        }

        this.currentTaskPrompt = newCurrentTaskPrompt
        this.currentHebrewPrompt = newCurrentHebrewPrompt
        this.currentTaskTitleResId = newTaskTitleResId

        uiState = uiState.copy(
            isRoundWon = false,
            errorCount = 0,
            errorCardId = null,
            assemblyLine = newAssemblyLine,
            availableCards = newAvailableCards,
            resultSnapshot = null,
            showResultSheet = false,
            playingSegmentIndex = -1,
            isSlowMode = false
        )

        desiredSegmentIndex = -1

        val allCompleted = progressManager.getCompletedRounds(currentLevelId)
        val allArchived = progressManager.getArchivedRounds(currentLevelId)
        val uncompletedRounds = currentLevelSentences.indices.filter {
            !allCompleted.contains(it) && !allArchived.contains(it)
        }
        isLastRoundAvailable = uncompletedRounds.size <= 1
    }

    private fun setupAssemblyTask(roundData: SentenceData): Triple<List<Card>, List<AvailableCardSlot>, List<AssemblySlot>> {
        val newAssemblyLine = mutableListOf<AssemblySlot>()
        val targetWordsList = roundData.task_target_cards ?: emptyList()
        val targetCards = targetWordsList.map { word -> Card(text = word, translation = wordDictionary[word] ?: "") }
        val targetCardsIterator = targetCards.iterator()
        val distractors = roundData.task_distractor_cards?.map { Card(text = it.trim(), translation = "") } ?: emptyList<Card>()
        val newAvailableCards = (targetCards + distractors).shuffled().map { AvailableCardSlot(card = it, isVisible = true) }
        val fullText = roundData.hebrew
        val targetWordsSet = targetWordsList.toSet()

        partsRegex.findAll(fullText).forEach { match ->
            val matchValue = match.value
            if (targetWordsSet.contains(matchValue) && targetCardsIterator.hasNext()) {
                newAssemblyLine.add(AssemblySlot(text = "___", isBlank = true, filledCard = null, targetCard = targetCardsIterator.next()))
            } else {
                newAssemblyLine.add(AssemblySlot(text = matchValue, isBlank = false, filledCard = null, targetCard = null))
            }
        }
        return Triple(targetCards, newAvailableCards, newAssemblyLine)
    }

    private fun setupQuizTask(roundData: SentenceData): Triple<List<Card>, List<AvailableCardSlot>, List<AssemblySlot>> {
        val newAssemblyLine = mutableListOf<AssemblySlot>()
        val fullCorrectSentence = roundData.task_correct_cards?.joinToString(" ") ?: ""
        val targetWordsList = roundData.task_target_cards ?: emptyList()
        val targetCards = targetWordsList.map { word -> Card(text = word, translation = wordDictionary[word] ?: "") }
        val targetCardsIterator = targetCards.iterator()
        val distractors = roundData.task_distractor_cards?.map { Card(text = it.trim(), translation = "") } ?: emptyList<Card>()
        val newAvailableCards = (targetCards + distractors).shuffled().map { AvailableCardSlot(card = it, isVisible = true) }
        val targetWordsSet = targetWordsList.toSet()

        partsRegex.findAll(fullCorrectSentence).forEach { match ->
            val token = match.value
            if (targetWordsSet.contains(token) && targetCardsIterator.hasNext()) {
                newAssemblyLine.add(AssemblySlot(text = "___", isBlank = true, filledCard = null, targetCard = targetCardsIterator.next()))
            } else {
                newAssemblyLine.add(AssemblySlot(text = token, isBlank = false, filledCard = null, targetCard = null))
            }
        }
        return Triple(targetCards, newAvailableCards, newAssemblyLine)
    }

    private fun setupFillInBlankTask(roundData: SentenceData): Triple<List<Card>, List<AvailableCardSlot>, List<AssemblySlot>> {
        val newAssemblyLine = mutableListOf<AssemblySlot>()
        val correctCards = roundData.task_correct_cards?.map { Card(text = it.trim(), translation = "") } ?: emptyList<Card>()
        val correctCardsIterator = correctCards.iterator()
        val distractors = roundData.task_distractor_cards?.map { Card(text = it.trim(), translation = "") } ?: emptyList<Card>()
        val newAvailableCards = (correctCards + distractors).shuffled().map { AvailableCardSlot(card = it, isVisible = true) }
        val hebrewPrompt = roundData.hebrew
        val promptParts = hebrewPrompt.split("___")

        promptParts.forEachIndexed { index, part ->
            partsRegex.findAll(part).forEach { match ->
                newAssemblyLine.add(AssemblySlot(text = match.value, isBlank = false, filledCard = null, targetCard = null))
            }
            if (index < promptParts.size - 1) {
                if (correctCardsIterator.hasNext()) {
                    newAssemblyLine.add(AssemblySlot(text = "___", isBlank = true, filledCard = null, targetCard = correctCardsIterator.next()))
                }
            }
        }
        return Triple(correctCards, newAvailableCards, newAssemblyLine)
    }

    private fun resetAndStartCounters() {
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

// ... (imports)
// ... (class declaration)

    private fun endRound(result: GameResult) {
        timerJob?.cancel()
        val currentSentence = levelRepository.getSingleSentence(currentLevelId, currentRoundIndex)
        val translation = currentSentence?.translation

        if (result == GameResult.WIN) {
            // 1. Сначала ВСЕГДА сохраняем прогресс и количество ошибок (чтобы обновить цвет кружка)
            progressManager.saveProgress(currentLevelId, currentRoundIndex, uiState.errorCount)

            // 2. Если это Audition -> Сразу переносим в Архив (Журнал)
            // (Функция archiveRound уберет его из "активных", но сохраненный выше рекорд ошибок останется в базе)
            if (currentTaskType == TaskType.AUDITION) {
                progressManager.archiveRound(currentLevelId, currentRoundIndex)
            }
        }

        // ... (остальной код создания snapshot без изменений)
        val allCompleted = progressManager.getCompletedRounds(currentLevelId)
        val allArchived = progressManager.getArchivedRounds(currentLevelId)
        val hasMoreRounds = (allCompleted.size + allArchived.size) < currentLevelSentences.size

        val completedCardsList = when (currentTaskType) {
            TaskType.ASSEMBLE_TRANSLATION, TaskType.AUDITION, TaskType.FILL_IN_BLANK, TaskType.QUIZ -> {
                uiState.assemblyLine.mapNotNull { it.filledCard ?: it.targetCard }
            }
            else -> emptyList()
        }

        val newSnapshot = RoundResultSnapshot(
            gameResult = result,
            completedCards = completedCardsList,
            translationText = if (currentTaskType == TaskType.AUDITION) translation else null,
            errorCount = this.uiState.errorCount,
            timeSpent = this.timeSpent,
            levelId = this.currentLevelId,
            hasMoreRounds = hasMoreRounds,
            audioFilename = if (result == GameResult.WIN) currentSentence?.audioFilename else null
        )

        uiState = uiState.copy(isRoundWon = true, resultSnapshot = newSnapshot)

        viewModelScope.launch {
            delay(650)
            uiState = uiState.copy(showResultSheet = true)
            if (result == GameResult.WIN) {
                newSnapshot.audioFilename?.let {
                    val speed = if (uiState.isSlowMode) 0.75f else 1.0f
                    audioPlayer.play(it, speed)
                }
            }
        }
    }

    // ... (остальные методы без изменений)

    fun replayAuditionAudio() {
        playFullAudio()
    }

    fun restartCurrentRound() {
        if (isLevelFullyCompleted) isLevelFullyCompleted = false
        loadRound(currentRoundIndex)
    }

    fun resetCurrentLevelProgress() {
        progressManager.resetLevelProgress(currentLevelId)
        isLevelFullyCompleted = false
        viewModelScope.launch { loadLevel(currentLevelId) }
    }

    fun proceedToNextRound() {
        val completedRounds = progressManager.getCompletedRounds(currentLevelId)
        val archivedRounds = progressManager.getArchivedRounds(currentLevelId)
        val nowCompletedSet = if (currentTaskType == TaskType.AUDITION) completedRounds else completedRounds + currentRoundIndex
        val uncompletedRounds = (0 until currentLevelSentences.size).filter { !nowCompletedSet.contains(it) && !archivedRounds.contains(it) }

        if (uncompletedRounds.isEmpty()) {
            isLevelFullyCompleted = true
            viewModelScope.launch { _navigationEvent.send(NavigationEvent.ShowRoundTrack(currentLevelId)) }
            return
        }
        val nextForwardRound = uncompletedRounds.firstOrNull { it > currentRoundIndex }
        val nextRound = nextForwardRound ?: uncompletedRounds.first()
        viewModelScope.launch { _navigationEvent.send(NavigationEvent.ShowRound(currentLevelId, nextRound)) }
    }

    fun skipToNextAvailableRound() {
        ttsPlayer.stop()
        val completedRounds = progressManager.getCompletedRounds(currentLevelId)
        val archivedRounds = progressManager.getArchivedRounds(currentLevelId)
        val activeRounds = currentLevelSentences.indices.filter { !completedRounds.contains(it) && !archivedRounds.contains(it) }
        if (activeRounds.isEmpty()) return
        val currentIndexInActiveList = activeRounds.indexOf(currentRoundIndex)
        val nextIndexInActiveList = if(currentIndexInActiveList != -1) (currentIndexInActiveList + 1) % activeRounds.size else 0
        val nextRound = activeRounds[nextIndexInActiveList]
        viewModelScope.launch { _navigationEvent.send(NavigationEvent.ShowRound(currentLevelId, nextRound)) }
    }

    override fun onCleared() {
        super.onCleared()
        ttsPlayer.stop()
        audioPlayer.release()
    }

    companion object {
        private const val TAG = "VIBRATE_DEBUG"
    }
}