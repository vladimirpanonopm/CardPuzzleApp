package com.example.cardpuzzleapp

import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

// --- МОДЕЛЬ ДЛЯ ЭЛЕМЕНТА ПАРЫ ---
data class MatchItem(
    val id: UUID = UUID.randomUUID(),
    val text: String,
    val pairId: String,
    val isMatched: Boolean = false,
    val isSelected: Boolean = false,
    val isHebrew: Boolean
)
// ---------------------------------

private const val TAG = "MATCHING_DEBUG"

@HiltViewModel
class MatchingViewModel @Inject constructor(
    private val levelRepository: LevelRepository,
    private val progressManager: GameProgressManager,
    private val ttsPlayer: TtsPlayer
) : ViewModel() {

    // --- Игровое поле ---
    var hebrewCards by mutableStateOf<List<MatchItem>>(emptyList())
    var translationCards by mutableStateOf<List<MatchItem>>(emptyList())

    private var originalTranslationCards = listOf<MatchItem>()

    var isLoading by mutableStateOf(true)
        private set
    var loadedUid by mutableStateOf(0L)
        private set

    // --- Состояние UI ---
    var currentTaskTitleResId by mutableStateOf(R.string.game_task_new_words)
        private set
    var isGameWon by mutableStateOf(false)
        private set
    var errorCount by mutableStateOf(0)
        private set
    var errorItemId by mutableStateOf<UUID?>(null)
        private set
    var isExamMode by mutableStateOf(false)
        private set
    var resultSnapshot by mutableStateOf<RoundResultSnapshot?>(null)
        private set
    var showResultSheet by mutableStateOf(false)
        private set
    var isLastRoundAvailable by mutableStateOf(false)
        private set

    var selectedItem by mutableStateOf<MatchItem?>(null)
        private set

    private var currentLoadJob: Job? = null

    // --- Каналы событий ---
    private val _hapticEventChannel = Channel<HapticEvent>()
    val hapticEvents = _hapticEventChannel.receiveAsFlow()

    private val _completionEventChannel = Channel<MatchingCompletionEvent>()
    val completionEvents = _completionEventChannel.receiveAsFlow()

    // --- Состояние текущего раунда ---
    var currentLevelId: Int by mutableStateOf(1)
        private set
    var currentRoundIndex: Int by mutableStateOf(0)
        private set

    fun loadLevelAndRound(levelId: Int, roundIndex: Int, uid: Long) {
        Log.w(TAG, "VM: loadLevelAndRound(uid=$uid) CALLED.")

        if (loadedUid == uid && !isLoading) {
            return
        }

        currentLoadJob?.cancel()
        isLoading = true

        hebrewCards = emptyList()
        translationCards = emptyList()
        isGameWon = false
        resultSnapshot = null
        showResultSheet = false
        selectedItem = null
        errorCount = 0
        errorItemId = null
        isExamMode = false
        currentTaskTitleResId = R.string.game_task_new_words

        this.currentLevelId = levelId
        this.currentRoundIndex = roundIndex

        currentLoadJob = loadRound(uid)
    }

    private fun loadRound(uid: Long): Job {
        return viewModelScope.launch {
            levelRepository.loadLevelDataIfNeeded(currentLevelId)
            ensureActive()
            val levelData = levelRepository.getSingleSentence(currentLevelId, currentRoundIndex)
            val allLevelSentences = levelRepository.getSentencesForLevel(currentLevelId)

            if (allLevelSentences.isEmpty() || levelData == null || levelData.taskType != TaskType.MATCHING_PAIRS) {
                currentTaskTitleResId = R.string.game_task_unknown
                isLoading = false
                loadedUid = uid
                return@launch
            }

            val pairs = levelData.task_pairs ?: emptyList()
            val newHebrewList = mutableListOf<MatchItem>()
            val newTranslationList = mutableListOf<MatchItem>()

            pairs.forEachIndexed { index, pair ->
                val hebrewText = pair.getOrNull(0)
                val translationText = pair.getOrNull(1)
                val pairId = "pair_$index"

                if (hebrewText != null && translationText != null) {
                    newHebrewList.add(MatchItem(text = hebrewText, pairId = pairId, isHebrew = true))
                    newTranslationList.add(MatchItem(text = translationText, pairId = pairId, isHebrew = false))
                }
            }

            ensureActive()

            hebrewCards = newHebrewList
            originalTranslationCards = newTranslationList
            translationCards = newTranslationList

            updateLastRoundAvailability(allLevelSentences)

            isLoading = false
            loadedUid = uid
        }
    }

    private fun updateLastRoundAvailability(allLevelSentences: List<SentenceData>) {
        val allCompleted = progressManager.getCompletedRounds(currentLevelId)
        val allArchived = progressManager.getArchivedRounds(currentLevelId)
        val uncompletedRounds = allLevelSentences.indices.filter {
            !allCompleted.contains(it) && !allArchived.contains(it)
        }
        isLastRoundAvailable = uncompletedRounds.size <= 1
    }

    fun startExamMode() {
        isExamMode = true
        currentTaskTitleResId = R.string.game_task_matching
        selectedItem = null
        errorCount = 0
        errorItemId = null
        translationCards = originalTranslationCards.shuffled()
    }

    fun onMatchItemClicked(item: MatchItem) {
        if (item.isMatched || isGameWon || isLoading) return

        if (!isExamMode) {
            if (item.isHebrew) {
                ttsPlayer.speak(item.text)
            }
            return
        }

        if (item.isHebrew) {
            ttsPlayer.speak(item.text)
        }

        val currentSelection = selectedItem

        if (currentSelection == null) {
            setSelection(item, true)
            selectedItem = item
            errorItemId = null

        } else if (currentSelection.isHebrew == item.isHebrew) {
            if (currentSelection.id == item.id) {
                setSelection(item, false)
                selectedItem = null
            } else {
                setSelection(currentSelection, false)
                setSelection(item, true)
                selectedItem = item
            }
            errorItemId = null

        } else {
            if (currentSelection.pairId == item.pairId) {
                setCardsAsMatched(currentSelection.pairId)
                selectedItem = null
                errorItemId = null
                viewModelScope.launch { _hapticEventChannel.send(HapticEvent.Success) }

                if (hebrewCards.all { it.isMatched }) {
                    handleWin()
                }

            } else {
                errorCount++
                errorItemId = item.id
                viewModelScope.launch {
                    _hapticEventChannel.send(HapticEvent.Failure)
                    delay(500)
                    setSelection(currentSelection, false)
                    setSelection(item, false)
                    selectedItem = null
                }
            }
        }
    }

    private fun setSelection(item: MatchItem, isSelected: Boolean) {
        val list = if (item.isHebrew) hebrewCards.toMutableList() else translationCards.toMutableList()
        val index = list.indexOfFirst { it.id == item.id }

        if (index != -1) {
            list[index] = list[index].copy(isSelected = isSelected)
        }

        if (item.isHebrew) {
            hebrewCards = list
        } else {
            translationCards = list
        }
    }

    private fun setCardsAsMatched(pairId: String) {
        val newHebrewCards = hebrewCards.map {
            if (it.pairId == pairId) it.copy(isMatched = true, isSelected = false) else it
        }
        val newTranslationCards = translationCards.map {
            if (it.pairId == pairId) it.copy(isMatched = true, isSelected = false) else it
        }

        hebrewCards = newHebrewCards
        translationCards = newTranslationCards
    }

    private fun handleWin() {
        isGameWon = true

        // --- ИСПРАВЛЕНИЕ: Теперь сохраняем количество ошибок! ---
        progressManager.saveProgress(currentLevelId, currentRoundIndex, errorCount)
        // -------------------------------------------------------

        resultSnapshot = RoundResultSnapshot(
            gameResult = GameResult.WIN,
            completedCards = emptyList(),
            errorCount = errorCount,
            timeSpent = 0,
            levelId = currentLevelId,
            hasMoreRounds = !isLastRoundAvailable,
            audioFilename = null
        )

        viewModelScope.launch {
            delay(650)
            showResultSheet = true
        }
    }

    fun proceedToNextRound() {
        isGameWon = false
        resultSnapshot = null
        showResultSheet = false
        viewModelScope.launch {
            _completionEventChannel.send(MatchingCompletionEvent.Win)
        }
    }

    fun restartCurrentRound() {
        val currentScreenUid = loadedUid
        loadedUid = 0L
        isLoading = true
        loadLevelAndRound(currentLevelId, currentRoundIndex, currentScreenUid)
    }

    fun onTrackClick() {
        viewModelScope.launch {
            _completionEventChannel.send(MatchingCompletionEvent.Track)
        }
    }

    fun showResultSheet() {
        showResultSheet = true
    }

    fun hideResultSheet() {
        showResultSheet = false
    }

    fun skipToNextAvailableRound() {
        viewModelScope.launch {
            _completionEventChannel.send(MatchingCompletionEvent.Skip)
        }
    }

    override fun onCleared() {
        super.onCleared()
        ttsPlayer.stop()
    }
}