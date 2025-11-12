package com.example.cardpuzzleapp

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable
import kotlin.OptIn
import kotlinx.serialization.SerialName

@OptIn(InternalSerializationApi::class)
@Serializable
data class SentenceData(
    @SerialName("uiDisplayTitle")
    val hebrew: String,

    @SerialName("translationPrompt")
    val russian_translation: String? = null,

    val english_translation: String? = null,
    val spanish_translation: String? = null,
    val french_translation: String? = null,
    val audioFilename: String?,

    val taskType: TaskType = TaskType.UNKNOWN,

    val voice: String? = null,

    @SerialName("correctOptions")
    val task_correct_cards: List<String>? = null,

    @SerialName("distractorOptions")
    val task_distractor_cards: List<String>? = null,

    // --- ВОТ ИСПРАВЛЕНИЕ: Добавляем SerialName ---
    @SerialName("taskPairs")
    val task_pairs: List<List<String>>? = null
)