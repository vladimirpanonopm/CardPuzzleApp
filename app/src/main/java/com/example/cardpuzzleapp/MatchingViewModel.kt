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

@HiltViewModel
class MatchingViewModel @Inject constructor(
    private val levelRepository: LevelRepository,
    private val progressManager: GameProgressManager
) : ViewModel() {

    // --- Игровое поле ---
    val hebrewCards = mutableStateListOf<MatchItem>()
    val translationCards = mutableStateListOf<MatchItem>()

    // --- Состояние UI ---
    var currentTaskTitleResId by mutableStateOf(R.string.game_task_matching)
        private set
    var isGameWon by mutableStateOf(false)
        private set
    var errorCount by mutableStateOf(0)
        private set
    var resultSnapshot by mutableStateOf<RoundResultSnapshot?>(null)
        private set

    // --- ИЗМЕНЕНИЕ 1: Добавляем состояния для BottomBar и BottomSheet ---
    var showResultSheet by mutableStateOf(false)
        private set
    var isLastRoundAvailable by mutableStateOf(false)
        private set
    // -----------------------------------------------------------

    var selectedItem by mutableStateOf<MatchItem?>(null)
        private set

    // --- Каналы событий ---
    private val _hapticEventChannel = Channel<HapticEvent>()
    val hapticEvents = _hapticEventChannel.receiveAsFlow()

    private val _completionEventChannel = Channel<String>()
    val completionEvents = _completionEventChannel.receiveAsFlow()

    // --- Состояние текущего раунда ---
    var currentLevelId: Int by mutableStateOf(1)
        private set
    // --- ИЗМЕНЕНИЕ 2: Делаем currentRoundIndex публичным для навигации Журнала ---
    var currentRoundIndex: Int by mutableStateOf(0)
        private set
    // ----------------------------------------------------------------------
    private var allLevelSentences = listOf<SentenceData>() // Храним все раунды уровня


    fun loadLevelAndRound(levelId: Int, roundIndex: Int) {
        this.currentLevelId = levelId
        this.currentRoundIndex = roundIndex
        loadRound()
    }

    fun loadRound() {
        viewModelScope.launch {
            Log.d(AppDebug.TAG, "MatchingViewModel: loadRound() (level $currentLevelId, round $currentRoundIndex)")

            // --- ИЗМЕНЕНИЕ 3: Загружаем ВСЕ данные уровня, а не только текущий раунд ---
            val allData = levelRepository.getLevelData(currentLevelId)
            if (allData == null) {
                Log.e(AppDebug.TAG, "MatchingViewModel: Ошибка! Не удалось загрузить LevelData.")
                currentTaskTitleResId = R.string.game_task_unknown
                return@launch
            }
            allLevelSentences = allData

            val levelData = allData.getOrNull(currentRoundIndex)
            // -----------------------------------------------------------------

            if (levelData == null || levelData.taskType != TaskType.MATCHING_PAIRS) {
                currentTaskTitleResId = R.string.game_task_unknown
                Log.e(AppDebug.TAG, "MatchingViewModel: Ошибка! 'MATCHING_PAIRS' не найдено.")
                return@launch
            }

            currentTaskTitleResId = R.string.game_task_matching

            // Генерируем карточки
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

            // Очищаем поле
            hebrewCards.clear()
            hebrewCards.addAll(newHebrewList.shuffled())
            translationCards.clear()
            translationCards.addAll(newTranslationList.shuffled())

            isGameWon = false
            resultSnapshot = null
            showResultSheet = false
            selectedItem = null
            errorCount = 0

            // --- ИЗМЕНЕНИЕ 4: Рассчитываем, последний ли это раунд ---
            updateLastRoundAvailability()
            // -----------------------------------------------

            Log.d(AppDebug.TAG, "MatchingViewModel: Поле сгенерировано. ${hebrewCards.size} пар.")
        }
    }

    // --- ИЗМЕНЕНИЕ 5: Новая функция для проверки последнего раунда ---
    private fun updateLastRoundAvailability() {
        val allCompleted = progressManager.getCompletedRounds(currentLevelId)
        val allArchived = progressManager.getArchivedRounds(currentLevelId)
        val uncompletedRounds = allLevelSentences.indices.filter {
            !allCompleted.contains(it) && !allArchived.contains(it)
        }
        isLastRoundAvailable = uncompletedRounds.size <= 1
    }
    // ---------------------------------------------------------

    fun onMatchItemClicked(item: MatchItem) {
        if (item.isMatched || isGameWon) return // Блокируем клики после победы

        val currentSelection = selectedItem

        if (currentSelection == null) {
            // --- 1. ПЕРВЫЙ ВЫБОР ---
            setSelection(item, true)
            selectedItem = item

        } else if (currentSelection.isHebrew == item.isHebrew) {
            // --- 2. ПОВТОРНЫЙ ВЫБОР В ТОМ ЖЕ СТОЛБЦЕ ---
            if (currentSelection.id == item.id) {
                setSelection(item, false)
                selectedItem = null
            } else {
                setSelection(currentSelection, false)
                setSelection(item, true)
                selectedItem = item
            }
        } else {
            // --- 3. ВТОРОЙ ВЫБОР В ДРУГОМ СТОЛБЦЕ (ПРОВЕРКА) ---
            if (currentSelection.pairId == item.pairId) {
                // --- 3a. УСПЕХ: Пара совпала ---
                setCardsAsMatched(currentSelection.pairId)
                selectedItem = null
                viewModelScope.launch { _hapticEventChannel.send(HapticEvent.Success) }
                Log.d(AppDebug.TAG, "MatchViewModel: УСПЕХ! Пара найдена: ${currentSelection.text} / ${item.text}")

                if (hebrewCards.all { it.isMatched }) {
                    handleWin()
                }

            } else {
                // --- 3b. ПРОВАЛ: Пара не совпала ---
                errorCount++
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
        val list = if (item.isHebrew) hebrewCards else translationCards
        val index = list.indexOfFirst { it.id == item.id }

        if (index != -1) {
            list[index] = list[index].copy(isSelected = isSelected)
        }
    }

    private fun setCardsAsMatched(pairId: String) {
        hebrewCards.indices.filter { hebrewCards[it].pairId == pairId }.forEach { i ->
            hebrewCards[i] = hebrewCards[i].copy(isMatched = true, isSelected = false)
        }
        translationCards.indices.filter { translationCards[it].pairId == pairId }.forEach { i ->
            translationCards[i] = translationCards[i].copy(isMatched = true, isSelected = false)
        }
    }

    private fun handleWin() {
        isGameWon = true
        showResultSheet = false // --- ИЗМЕНЕНИЕ 6: Не показываем шторку автоматически
        progressManager.saveProgress(currentLevelId, currentRoundIndex)

        resultSnapshot = RoundResultSnapshot(
            gameResult = GameResult.WIN,
            completedCards = emptyList(),
            errorCount = errorCount,
            timeSpent = 0, // (Таймер не реализован в этом режиме)
            levelId = currentLevelId,
            hasMoreRounds = !isLastRoundAvailable, // (Используем кешированный флаг)
            audioFilename = null
        )
    }

    fun proceedToNextRound() {
        isGameWon = false
        resultSnapshot = null
        showResultSheet = false
        viewModelScope.launch {
            _completionEventChannel.send("WIN")
        }
    }

    fun restartCurrentRound() {
        isGameWon = false
        resultSnapshot = null
        showResultSheet = false
        loadRound()
    }

    fun onTrackClick() {
        viewModelScope.launch {
            _completionEventChannel.send("TRACK")
        }
    }

    // --- ИЗМЕНЕНИЕ 7: Новые функции для управления шторкой и пропуска ---
    fun showResultSheet() {
        showResultSheet = true
    }

    fun hideResultSheet() {
        showResultSheet = false
    }

    fun skipToNextAvailableRound() {
        viewModelScope.launch {
            _completionEventChannel.send("SKIP")
        }
    }
    // --------------------------------------------------------------
}