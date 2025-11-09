package com.example.cardpuzzleapp

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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

@HiltViewModel
class MatchingViewModel @Inject constructor(
    private val levelRepository: LevelRepository,
    private val progressManager: GameProgressManager
    // TODO: Добавить AudioPlayer, если/когда нам понадобится озвучка по клику
) : ViewModel() {

    // --- Игровое поле ---
    val cards = mutableStateListOf<MatchCard>()
    var isInteractionLocked by mutableStateOf(false) // Блокировка UI, пока переворачиваются неверные карты

    // --- Состояние UI ---
    var currentTaskTitle by mutableStateOf("Найди пары")
        private set
    var isGameWon by mutableStateOf(false)
        private set

    // --- Каналы событий ---
    private val _hapticEventChannel = Channel<HapticEvent>()
    val hapticEvents = _hapticEventChannel.receiveAsFlow()

    private var firstFlippedCard: MatchCard? = null
    private var levelData: SentenceData? = null

    // --- ВРЕМЕННО ДЛЯ ОТЛАДКИ ---
    // Мы жестко задаем, что хотим отладить 1-й уровень, 1-ю карточку (которая 0-я в списке)
    private val debugLevelId = 1
    private val debugRoundIndex = 0 // 0-я карточка в level_1.json, которую мы сделали MATCHING_PAIRS

    init {
        loadRound()
    }

    fun loadRound() {
        viewModelScope.launch {
            Log.d(AppDebug.TAG, "MatchingViewModel: loadRound() (level $debugLevelId, round $debugRoundIndex)")
            // 1. Загружаем данные
            val allLevelData = levelRepository.getLevelData(debugLevelId)
            levelData = allLevelData?.getOrNull(debugRoundIndex)

            if (levelData == null || levelData?.taskType != TaskType.MATCHING_PAIRS) {
                currentTaskTitle = "Ошибка: Задание 'Найди Пары' не найдено"
                Log.e(AppDebug.TAG, "MatchingViewModel: Ошибка! 'MATCHING_PAIRS' не найдено в level $debugLevelId / round $debugRoundIndex")
                return@launch
            }

            // 2. Устанавливаем заголовок
            // (В .txt мы клали заголовок в RUSSIAN, а hebrew_list - это просто плейсхолдер)
            currentTaskTitle = levelData?.russian_translation ?: "Найди пары"

            // 3. Генерируем карточки
            val pairs = levelData?.task_pairs ?: emptyList()
            val newCards = mutableListOf<MatchCard>()

            pairs.forEachIndexed { index, pair ->
                val textA = pair.getOrNull(0)
                val textB = pair.getOrNull(1)
                val pairId = "pair_$index" // (напр. "pair_0", "pair_1")

                if (textA != null && textB != null) {
                    newCards.add(MatchCard(text = textA, pairId = pairId))
                    newCards.add(MatchCard(text = textB, pairId = pairId))
                }
            }

            // 4. Очищаем поле, перемешиваем и добавляем
            cards.clear()
            cards.addAll(newCards.shuffled())
            isGameWon = false
            firstFlippedCard = null
            isInteractionLocked = false
            Log.d(AppDebug.TAG, "MatchingViewModel: Поле сгенерировано. ${cards.size} карт.")
        }
    }

    /**
     * Вызывается, когда пользователь нажимает на карточку.
     */
    fun onCardClicked(clickedCard: MatchCard) {
        // Нельзя нажимать на "совпавшие" или во время блокировки UI
        if (clickedCard.isMatched || isInteractionLocked) return

        // Нажатие на уже перевернутую карту
        if (clickedCard.id == firstFlippedCard?.id) return

        viewModelScope.launch {
            // --- Переворачиваем карту в UI ---
            flipCard(clickedCard.id, true)

            val firstCard = firstFlippedCard

            if (firstCard == null) {
                // --- 1. Это ПЕРВАЯ карта ---
                firstFlippedCard = clickedCard.copy(isFlipped = true)
                Log.d(AppDebug.TAG, "MatchingViewModel: Первая карта перевернута (${clickedCard.text})")
            } else {
                // --- 2. Это ВТОРАЯ карта ---
                isInteractionLocked = true // Блокируем UI
                Log.d(AppDebug.TAG, "MatchingViewModel: Вторая карта перевернута (${clickedCard.text})")

                if (firstCard.pairId == clickedCard.pairId) {
                    // --- 2a. УСПЕХ: Пара совпала ---
                    Log.d(AppDebug.TAG, "MatchingViewModel: УСПЕХ! Пара найдена: ${firstCard.text} / ${clickedCard.text}")
                    _hapticEventChannel.send(HapticEvent.Success)
                    // Отмечаем обе как 'isMatched'
                    setCardsAsMatched(firstCard.pairId)
                    firstFlippedCard = null
                    isInteractionLocked = false

                    // Проверяем победу
                    if (cards.all { it.isMatched }) {
                        Log.d(AppDebug.TAG, "MatchingViewModel: ПОБЕДА! Все пары найдены.")
                        isGameWon = true
                    }

                } else {
                    // --- 2b. ПРОВАЛ: Пара не совпала ---
                    Log.d(AppDebug.TAG, "MatchingViewModel: ПРОВАЛ. (${firstCard.text} != ${clickedCard.text})")
                    _hapticEventChannel.send(HapticEvent.Failure)
                    delay(1200) // Даем пользователю посмотреть
                    // Переворачиваем обе обратно
                    flipCard(firstCard.id, false)
                    flipCard(clickedCard.id, false)
                    firstFlippedCard = null
                    isInteractionLocked = false
                }
            }
        }
    }

    /** Обновляет состояние isFlipped для карты в списке */
    private fun flipCard(cardId: UUID, isFlipped: Boolean) {
        val index = cards.indexOfFirst { it.id == cardId }
        if (index != -1) {
            cards[index] = cards[index].copy(isFlipped = isFlipped)
        }
    }

    /** Находит обе карты с pairId и помечает их как 'isMatched' */
    private fun setCardsAsMatched(pairId: String) {
        cards.indices.forEach { i ->
            if (cards[i].pairId == pairId) {
                cards[i] = cards[i].copy(isMatched = true, isFlipped = true)
            }
        }
    }
}