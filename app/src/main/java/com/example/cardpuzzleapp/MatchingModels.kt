package com.example.cardpuzzleapp

import java.util.UUID

/**
 * Представляет состояние одной карточки на игровом поле "Найди пару".
 * @param id Уникальный ID этой карточки на поле.
 * @param text Текст на карточке (напр. "אני" или "Я").
 * @param pairId Уникальный ID *пары* (напр. "pair_0"). У "אני" и "Я" будет одинаковый pairId.
 * @param isFlipped Перевернута ли карточка (показывает "лицо").
 * @param isMatched Найдена ли уже пара для этой карточки.
 */
data class MatchCard(
    val id: UUID = UUID.randomUUID(),
    val text: String,
    val pairId: String,
    val isFlipped: Boolean = false,
    val isMatched: Boolean = false
)