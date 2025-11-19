package com.example.cardpuzzleapp

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@OptIn(InternalSerializationApi::class)
@Serializable
data class SentenceData(
    @SerialName("uiDisplayTitle")
    val hebrew: String,

    // Мапим JSON-поле "translationPrompt" сразу в понятное "translation".
    // Это всегда будет русский текст.
    @SerialName("translationPrompt")
    val translation: String? = null,

    val audioFilename: String?,

    val taskType: TaskType = TaskType.UNKNOWN,

    val voice: String? = null,

    @SerialName("correctOptions")
    val task_correct_cards: List<String>? = null,

    @SerialName("taskTargetCards")
    val task_target_cards: List<String>? = null,

    @SerialName("distractorOptions")
    val task_distractor_cards: List<String>? = null,

    @SerialName("taskPairs")
    val task_pairs: List<List<String>>? = null
)