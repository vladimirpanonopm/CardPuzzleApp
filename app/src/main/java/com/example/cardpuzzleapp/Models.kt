package com.example.cardpuzzleapp

import kotlinx.serialization.Serializable
import java.util.UUID
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.ExperimentalSerializationApi

@OptIn(InternalSerializationApi::class, ExperimentalSerializationApi::class)
sealed class HapticEvent {
    object Success : HapticEvent()
    object Failure : HapticEvent()
}

enum class GameResult {
    NONE, WIN, LOSS_TIME, LOSS_ERRORS
}

data class RoundResultSnapshot(
    val gameResult: GameResult,
    val completedCards: List<Card>,
    val translationText: String? = null,
    val errorCount: Int,
    val timeSpent: Int,
    val levelId: Int,
    val hasMoreRounds: Boolean,
    val audioFilename: String?
)

data class Card(
    val id: UUID = UUID.randomUUID(),
    val text: String,
    val translation: String
)

// Оставляем только Иврит и Русский
data class HebrewLetter(
    val id: UUID = UUID.randomUUID(),
    val letter: String,
    val nameRU: String,
    val audioFilename: String
)

data class AvailableCardSlot(
    val id: UUID = UUID.randomUUID(),
    val card: Card,
    val isVisible: Boolean = true
)