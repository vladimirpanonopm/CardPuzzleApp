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

// --- TAG ИЗМЕНЕН ДЛЯ УДОБСТВА ФИЛЬТРАЦИИ ---
private const val TAG = "MATCHING_DEBUG"
// ------------------------------------------

@HiltViewModel
class MatchingViewModel @Inject constructor(
    // --- ИСПРАВЛЕНИЕ: Возвращаем LevelRepository ---
    private val levelRepository: LevelRepository,
    private val progressManager: GameProgressManager,
    private val ttsPlayer: TtsPlayer
) : ViewModel() {

    // --- Игровое поле ---
    var hebrewCards by mutableStateOf<List<MatchItem>>(emptyList())
    var translationCards by mutableStateOf<List<MatchItem>>(emptyList())

    private var originalTranslationCards = listOf<MatchItem>()

    // --- ИСПРАВЛЕНИЕ: Возвращаем флаги загрузки ---
    var isLoading by mutableStateOf(true)
        private set
    var loadedUid by mutableStateOf(0L)
        private set
    // --- КОНЕЦ ИСПРАВЛЕНИЯ ---

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

    // --- ИСПРАВЛЕНИЕ: Возвращаем Job ---
    private var currentLoadJob: Job? = null
    // --- КОНЕЦ ИСПРАВЛЕНИЯ ---

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

    // --- ИСПРАВЛЕНИЕ: Возвращаем 'loadLevelAndRound' ---
    fun loadLevelAndRound(levelId: Int, roundIndex: Int, uid: Long) {
        Log.w(TAG, "VM: loadLevelAndRound(uid=$uid) CALLED.")
        Log.i(TAG, "  > VM: Текущее состояние: loadedUid=$loadedUid, isLoading=$isLoading")

        if (loadedUid == uid && !isLoading) {
            Log.e(TAG, "  > VM: ОТМЕНА ЗАГРУЗКИ: Этот UID ($uid) уже загружен и не в процессе загрузки.")
            return
        }

        currentLoadJob?.cancel()
        Log.i(TAG, "  > VM: Предыдущий Job (если был) отменен.")

        isLoading = true
        Log.w(TAG, "  > VM: СИНХРОННЫЙ СБРОС UI (isLoading = true)")

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
        Log.w(TAG, "  > VM: loadRound(uid=$uid) (КОРУТИНА) CALLED")

        return viewModelScope.launch {
            Log.i(TAG, "  > VM: loadRound(uid=$uid) (coroutine started)")

            levelRepository.loadLevelDataIfNeeded(currentLevelId)
            ensureActive()
            val levelData = levelRepository.getSingleSentence(currentLevelId, currentRoundIndex)
            val allLevelSentences = levelRepository.getSentencesForLevel(currentLevelId)

            if (allLevelSentences.isEmpty() || levelData == null || levelData.taskType != TaskType.MATCHING_PAIRS) {
                Log.e(TAG, "  > VM: loadRound(uid=$uid) ОШИБКА! Данные уровня некорректны.")
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

            Log.i(TAG, "  > VM: loadRound(uid=$uid) Карточки загружены. hebrewCards.size = ${hebrewCards.size}")
            updateLastRoundAvailability(allLevelSentences)
            Log.i(TAG, "  > VM: loadRound(uid=$uid) Поле сгенерировано. ${hebrewCards.size} пар.")

            isLoading = false
            loadedUid = uid
            Log.w(TAG, "  > VM: loadRound(uid=$uid) (КОРУТИНА) ЗАВЕРШЕНА. isLoading = false, loadedUid = $uid. UI должен обновиться.")
        }
    }
    // --- КОНЕЦ ИСПРАВЛЕНИЯ ---

    private fun updateLastRoundAvailability(allLevelSentences: List<SentenceData>) {
        val allCompleted = progressManager.getCompletedRounds(currentLevelId)
        val allArchived = progressManager.getArchivedRounds(currentLevelId)
        val uncompletedRounds = allLevelSentences.indices.filter {
            !allCompleted.contains(it) && !allArchived.contains(it)
        }
        isLastRoundAvailable = uncompletedRounds.size <= 1
    }

    fun startExamMode() {
        Log.d(TAG, "VM: startExamMode() CALLED.")
        isExamMode = true
        currentTaskTitleResId = R.string.game_task_matching
        selectedItem = null
        errorCount = 0
        errorItemId = null
        translationCards = originalTranslationCards.shuffled()
    }

    fun onMatchItemClicked(item: MatchItem) {
        // --- ИСПРАВЛЕНИЕ: Возвращаем 'isLoading' ---
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
                Log.d(AppDebug.TAG, "MatchViewModel: УСПЕХ! Пара найдена: ${currentSelection.text} / ${item.text}")

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
                Log.d(AppDebug.TAG, "MatchViewModel: ПРОВАЛ. (${currentSelection.text} != ${item.text})")
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
        Log.d(TAG, "VM: handleWin() CALLED")
        isGameWon = true
        Log.d(TAG, "  > VM: isGameWon = $isGameWon")

        progressManager.saveProgress(currentLevelId, currentRoundIndex)

        resultSnapshot = RoundResultSnapshot(
            gameResult = GameResult.WIN,
            completedCards = emptyList(),
            errorCount = errorCount,
            timeSpent = 0,
            levelId = currentLevelId,
            hasMoreRounds = !isLastRoundAvailable,
            audioFilename = null
        )
        Log.d(TAG, "  > VM: resultSnapshot CREATED: ${resultSnapshot != null}")

        viewModelScope.launch {
            delay(650)
            showResultSheet = true
            Log.d(TAG, "  > VM: АВТО-ПОДЪЕМ ШТОРКИ: showResultSheet = true")
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
        // --- ИСПРАВЛЕНИЕ: Возвращаем оригинальную логику ---
        val currentScreenUid = loadedUid
        Log.w(TAG, "VM: restartCurrentRound() CALLED. (target UID: $currentScreenUid)")
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
        Log.d(TAG, "VM: showResultSheet() CALLED (Кнопка 'Глаз')")
        showResultSheet = true
        Log.d(TAG, "  > VM: showResultSheet = $showResultSheet")
    }

    fun hideResultSheet() {
        Log.d(TAG, "VM: hideResultSheet() CALLED (Свайп)")
        showResultSheet = false
        Log.d(TAG, "  > VM: showResultSheet = $showResultSheet")
    }

    fun skipToNextAvailableRound() {
        viewModelScope.launch {
            _completionEventChannel.send(MatchingCompletionEvent.Skip)
        }
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(AppDebug.TAG, "MatchingViewModel: onCleared(). ОСТАНАВЛИВАЕМ TTS.")
        ttsPlayer.stop()
    }
}