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
    val audioFilename: String,
    // val imageName: String? = null, // <-- УДАЛЕНО
    val taskType: String? = null, // <-- ДОБАВЛЕНО
    val voice: String? = null
)