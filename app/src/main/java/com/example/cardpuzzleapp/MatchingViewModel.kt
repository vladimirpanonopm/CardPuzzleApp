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
// --- ИЗМЕНЕНИЕ 1: Добавить импорты ---
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
// ---------------------------------
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

// --- ЛОГ-ТЕГ ДЛЯ БАГА ---
//private const val TAG = "MATCHING_CARDS_DEBUG"
private const val TAG = AppDebug.TAG
// -------------------------

@HiltViewModel
class MatchingViewModel @Inject constructor(
    private val levelRepository: LevelRepository,
    private val progressManager: GameProgressManager,
    private val ttsPlayer: TtsPlayer
) : ViewModel() {

    // --- Игровое поле ---
    var hebrewCards by mutableStateOf<List<MatchItem>>(emptyList())
    var translationCards by mutableStateOf<List<MatchItem>>(emptyList())

    // --- ФЛАГ ЗАГРУЗКИ ---
    var isLoading by mutableStateOf(true)
        private set

    // <<< ----- НОВЫЙ КЛЮЧЕВОЙ STATE V11 ----- >>>
    /** Хранит UID, который был *успешно* загружен в ViewModel. */
    var loadedUid by mutableStateOf(0L)
        private set
    // <<< --------------------------------- >>>

    // --- Состояние UI ---
    var currentTaskTitleResId by mutableStateOf(R.string.game_task_matching)
        private set
    var isGameWon by mutableStateOf(false)
        private set
    var errorCount by mutableStateOf(0)
        private set
    var resultSnapshot by mutableStateOf<RoundResultSnapshot?>(null)
        private set
    var showResultSheet by mutableStateOf(false)
        private set
    var isLastRoundAvailable by mutableStateOf(false)
        private set

    var selectedItem by mutableStateOf<MatchItem?>(null)
        private set

    // --- ИЗМЕНЕНИЕ 2: Добавить Job для отслеживания загрузки ---
    private var currentLoadJob: Job? = null
    // ----------------------------------------------------

    // --- Каналы событий ---
    private val _hapticEventChannel = Channel<HapticEvent>()
    val hapticEvents = _hapticEventChannel.receiveAsFlow()

    private val _completionEventChannel = Channel<String>()
    val completionEvents = _completionEventChannel.receiveAsFlow()

    // --- Состояние текущего раунда ---
    var currentLevelId: Int by mutableStateOf(1)
        private set
    var currentRoundIndex: Int by mutableStateOf(0)
        private set
    private var allLevelSentences = listOf<SentenceData>() // Храним все раунды уровня


    // --- ИСПРАВЛЕНИЕ V11: Принимаем UID ---
    fun loadLevelAndRound(levelId: Int, roundIndex: Int, uid: Long) {
        Log.d(TAG, "MatchingViewModel: loadLevelAndRound($levelId, $roundIndex, uid=$uid) CALLED.")
        Log.d(TAG, "  > Текущее состояние VM: loadedUid=$loadedUid, isLoading=$isLoading")

        // --- НОВЫЙ ЛОГ: Проверка, актуальны ли данные ---
        if (loadedUid == uid && !isLoading) {
            Log.w(TAG, "  > ОТМЕНА ЗАГРУЗКИ: Этот UID ($uid) уже загружен.")
            return
        }
        // ---------------------------------------------

        // --- ИЗМЕНЕНИЕ 3: Отменить предыдущую активную загрузку ---
        currentLoadJob?.cancel()
        Log.d(TAG, "loadLevelAndRound: Предыдущий Job (если был) отменен.")
        // ------------------------------------------------------

        // 1. Синхронно ставим флаг "Загрузка"
        isLoading = true
        Log.d(TAG, "loadLevelAndRound: СИНХРОННЫЙ СБРОС UI (isLoading = true)")

        // 2. Сбрасываем *все* состояние раунда
        hebrewCards = emptyList()
        translationCards = emptyList()
        isGameWon = false
        resultSnapshot = null
        showResultSheet = false
        selectedItem = null
        errorCount = 0

        this.currentLevelId = levelId
        this.currentRoundIndex = roundIndex

        // --- ИЗМЕНЕНИЕ 4: Запустить и сохранить новый Job ---
        // 3. Запускаем асинхронную загрузку, передавая UID
        currentLoadJob = loadRound(uid)
        // ------------------------------------------------
    }

    // --- ИЗМЕНЕНИЕ 5: Метод теперь возвращает Job ---
    private fun loadRound(uid: Long): Job {
        // -------------------------------------------
        Log.d(TAG, "MatchingViewModel: loadRound(uid=$uid) (АСИНХРОННАЯ ЗАГРУЗКА) CALLED")

        // --- ИЗМЕНЕНИЕ 6: Возвращаем Job из launch ---
        return viewModelScope.launch {
            // ------------------------------------------
            Log.d(TAG, "MatchingViewModel: loadRound(uid=$uid) (coroutine started)")

            levelRepository.clearCache()
            val allData = levelRepository.getLevelData(currentLevelId)

            // --- ИЗМЕНЕНИЕ 7: Проверять, не отменена ли корутина ---
            ensureActive()
            // ---------------------------------------------------

            if (allData == null) {
                Log.e(TAG, "MatchingViewModel: ОШИБКА! allData == null.")
                currentTaskTitleResId = R.string.game_task_unknown
                isLoading = false
                loadedUid = uid // Помечаем, что загрузка (провальная) завершена
                return@launch
            }
            allLevelSentences = allData
            Log.d(TAG, "MatchingViewModel: allData.size = ${allData.size}")

            val levelData = allData.getOrNull(currentRoundIndex)

            // --- ИЗМЕНЕНИЕ 8: Проверять, не отменена ли корутина ---
            ensureActive()
            // ---------------------------------------------------

            if (levelData == null) {
                Log.e(TAG, "MatchingViewModel: ОШИБКА! levelData == null.")
                currentTaskTitleResId = R.string.game_task_unknown
                isLoading = false
                loadedUid = uid // Помечаем, что загрузка (провальная) завершена
                return@launch
            }

            if (levelData.taskType != TaskType.MATCHING_PAIRS) {
                currentTaskTitleResId = R.string.game_task_unknown
                Log.e(TAG, "MatchingViewModel: ОШИБКА! 'MATCHING_PAIRS' не найдено.")
                isLoading = false
                loadedUid = uid // Помечаем, что загрузка (провальная) завершена
                return@launch
            }

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

            // --- ИЗМЕНЕНИЕ 9: ГЛАВНАЯ ПРОВЕРКА. Не обновлять UI, если Job отменен ---
            ensureActive()
            // -------------------------------------------------------------------

            // --- Обновляем State через присваивание ---
            hebrewCards = newHebrewList.shuffled()
            translationCards = newTranslationList.shuffled()
            Log.d(TAG, "MatchingViewModel: (АСИНХРОННО) Карточки загружены. hebrewCards.size = ${hebrewCards.size}")

            updateLastRoundAvailability()

            Log.d(TAG, "MatchingViewModel: Поле сгенерировано. ${hebrewCards.size} пар.")

            // <<< КЛЮЧЕВОЕ ИСПРАВЛЕНИЕ V11 >>>
            // 4. Снимаем флаг "Загрузка" И помечаем UID как загруженный
            isLoading = false
            loadedUid = uid
            Log.d(TAG, "loadRound: (АСИНХРОННО) ЗАВЕРШЕНО. isLoading = false, loadedUid = $uid. UI должен обновиться.")
            // <<< --------------------------- >>>
        }
    }
    // ----------------------------------------------------

    private fun updateLastRoundAvailability() {
        val allCompleted = progressManager.getCompletedRounds(currentLevelId)
        val allArchived = progressManager.getArchivedRounds(currentLevelId)
        val uncompletedRounds = allLevelSentences.indices.filter {
            !allCompleted.contains(it) && !allArchived.contains(it)
        }
        isLastRoundAvailable = uncompletedRounds.size <= 1
    }

    fun onMatchItemClicked(item: MatchItem) {
        if (item.isMatched || isGameWon || isLoading) return

        if (item.isHebrew) {
            ttsPlayer.speak(item.text)
        }

        val currentSelection = selectedItem

        if (currentSelection == null) {
            setSelection(item, true)
            selectedItem = item

        } else if (currentSelection.isHebrew == item.isHebrew) {
            if (currentSelection.id == item.id) {
                setSelection(item, false)
                selectedItem = null
            } else {
                setSelection(currentSelection, false)
                setSelection(item, true)
                selectedItem = item
            }
        } else {
            if (currentSelection.pairId == item.pairId) {
                setCardsAsMatched(currentSelection.pairId)
                selectedItem = null
                viewModelScope.launch { _hapticEventChannel.send(HapticEvent.Success) }
                Log.d(AppDebug.TAG, "MatchViewModel: УСПЕХ! Пара найдена: ${currentSelection.text} / ${item.text}")

                if (hebrewCards.all { it.isMatched }) {
                    handleWin()
                }

            } else {
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
        Log.d(TAG, "handleWin() CALLED")
        isGameWon = true
        Log.d(TAG, "isGameWon = $isGameWon")

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
        Log.d(TAG, "resultSnapshot CREATED: ${resultSnapshot != null}")

        viewModelScope.launch {
            delay(650)
            showResultSheet = true
            Log.d(TAG, "АВТО-ПОДЪЕМ ШТОРКИ: showResultSheet = true")
        }
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
        // --- ИЗМЕНЕНИЕ V11: Мы должны передать UID, чтобы инициировать перезагрузку ---
        // Мы используем 0L, чтобы он гарантированно отличался от loadedUid
        loadLevelAndRound(currentLevelId, currentRoundIndex, 0L)
        // --------------------------------------------------------------------------
    }

    fun onTrackClick() {
        viewModelScope.launch {
            _completionEventChannel.send("TRACK")
        }
    }

    fun showResultSheet() {
        Log.d(TAG, "showResultSheet() CALLED (Кнопка 'Глаз')")
        showResultSheet = true
        Log.d(TAG, "showResultSheet = $showResultSheet")
    }

    fun hideResultSheet() {
        Log.d(TAG, "hideResultSheet() CALLED (Свайп)")
        showResultSheet = false
        Log.d(TAG, "showResultSheet = $showResultSheet")
    }

    fun skipToNextAvailableRound() {
        viewModelScope.launch {
            _completionEventChannel.send("SKIP")
        }
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(AppDebug.TAG, "MatchingViewModel: onCleared(). ОСТАНАВЛИВАЕМ TTS.")
        ttsPlayer.stop()
    }
}