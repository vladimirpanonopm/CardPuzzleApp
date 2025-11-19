package com.example.cardpuzzleapp

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

/**
 * Класс, описывающий один временной отрезок аудио (одна строка).
 * Генерируется Python-скриптом.
 */
@Serializable
data class AudioSegment(
    val text: String,      // Текст фразы (иврит)
    @SerialName("start_ms")
    val startMs: Long,     // Время начала (мс)
    @SerialName("end_ms")
    val endMs: Long        // Время конца (мс)
)

/**
 * Основная модель данных для одной карточки/экрана.
 */
@OptIn(InternalSerializationApi::class)
@Serializable
data class SentenceData(
    @SerialName("uiDisplayTitle")
    val hebrew: String,

    @SerialName("translationPrompt")
    val translation: String? = null,

    val audioFilename: String?,

    // --- НОВОЕ ПОЛЕ: Список тайм-кодов ---
    // Если null или пустой список — значит играем файл целиком (старая логика)
    val segments: List<AudioSegment>? = null,
    // -------------------------------------

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