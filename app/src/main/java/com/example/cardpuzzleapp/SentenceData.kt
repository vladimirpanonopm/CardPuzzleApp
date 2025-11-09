package com.example.cardpuzzleapp

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable
import kotlin.OptIn

@OptIn(InternalSerializationApi::class)
@Serializable
data class SentenceData(
    val hebrew: String,
    val russian_translation: String? = null,
    val english_translation: String? = null,
    val spanish_translation: String? = null,
    val french_translation: String? = null,
    val audioFilename: String?, // (Сделано Nullable)

    val taskType: TaskType = TaskType.UNKNOWN,

    val voice: String? = null,

    // Для FILL_IN_BLANK
    val task_correct_cards: List<String>? = null,
    val task_distractor_cards: List<String>? = null,

    // --- ДОБАВЛЕНО ДЛЯ MATCHING_PAIRS ---
    val task_pairs: List<List<String>>? = null
    // -----------------------------------
)