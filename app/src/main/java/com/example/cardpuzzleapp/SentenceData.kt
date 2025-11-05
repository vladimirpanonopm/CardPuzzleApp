package com.example.cardpuzzleapp

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable
import kotlin.OptIn

/**
 * Этот класс представляет структуру одной записи (одного предложения)
 * в JSON-файле уровня. Аннотация @Serializable говорит библиотеке,
 * как преобразовывать JSON в этот объект.
 */
@OptIn(InternalSerializationApi::class) // <-- Добавьте эту аннотацию
@Serializable
data class SentenceData(
    val hebrew: String,
    val russian_translation: String? = null,
    val english_translation: String? = null,
    val spanish_translation: String? = null,
    val french_translation: String? = null,
    val audioFilename: String,
    val imageName: String? = null // <--- ДОБАВЬТЕ ЭТУ СТРОКУ
)
