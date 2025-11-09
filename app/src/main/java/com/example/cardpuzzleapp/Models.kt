package com.example.cardpuzzleapp

import kotlinx.serialization.Serializable
import java.util.UUID

// --- ВОТ ИСПРАВЛЕНИЕ (НОВЫЙ ИМПОРТ И ANNOTATION) ---
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.JsonElement
@OptIn(InternalSerializationApi::class, ExperimentalSerializationApi::class)
// -----------------------------


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
    // val translation: String, // <-- УДАЛЕНО
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

data class LanguageOption(
    val code: String,
    val displayName: String,
    val flagEmoji: String
)

val supportedLanguages = listOf(
    LanguageOption("ru", "Русский", "🇷🇺"),
    LanguageOption("en", "English", "🇬🇧"),
    LanguageOption("fr", "Français", "🇫🇷"),
    LanguageOption("es", "Español", "🇪🇸")
)

data class HebrewLetter(
    val id: UUID = UUID.randomUUID(),
    val letter: String,
    val nameRU: String,
    val nameEN: String,
    val nameFR: String,
    val nameES: String,
    val audioFilename: String
)


// --- ИЗМЕНЕНИЕ: ОБНОВЛЕННЫЙ LEVELENTRY ДЛЯ ПАРСИНГА JSON ---

@OptIn(InternalSerializationApi::class)
@Serializable
data class LevelEntry(
    val hebrew_index: Int,
    val russian_translation: String,
    val english_translation: String? = null,
    val french_translation: String? = null,
    val spanish_translation: String? = null,
    // --- КЛЮЧЕВОЕ ИСПРАВЛЕНИЕ: String? = null ---
    val audioFilename: String? = null,
    // ---------------------------------------------

    val taskType: TaskType = TaskType.UNKNOWN,

    val voice: String? = null,

    val task_correct_cards: List<String>? = null,
    val task_distractor_cards: List<String>? = null,
    val task_pairs: List<List<String>>? = null
)

// --- Класс-обертка для "Эффекта Duolingo" ---
data class AvailableCardSlot(
    val id: UUID = UUID.randomUUID(),
    val card: Card,
    val isVisible: Boolean = true
)