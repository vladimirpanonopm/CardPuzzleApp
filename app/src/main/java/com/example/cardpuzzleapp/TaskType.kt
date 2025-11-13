package com.example.cardpuzzleapp

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Определяет типы заданий в приложении.
 * Использует @SerialName для автоматической конвертации
 * из "STRING_IN_JSON" в TaskType.ENUM_VALUE.
 */
@Serializable
enum class TaskType {
    // "Собери фразу из слов"
    @SerialName("ASSEMBLE_TRANSLATION")
    ASSEMBLE_TRANSLATION,

    // "Заполни пропуск"
    @SerialName("FILL_IN_BLANK")
    FILL_IN_BLANK,

    // --- ДОБАВЛЕНО ---
    @SerialName("AUDITION")
    AUDITION,
    // -----------------

    @SerialName("MATCHING_PAIRS")
    MATCHING_PAIRS,

    // Тип по умолчанию, если парсинг не удался
    @SerialName("UNKNOWN")
    UNKNOWN
}