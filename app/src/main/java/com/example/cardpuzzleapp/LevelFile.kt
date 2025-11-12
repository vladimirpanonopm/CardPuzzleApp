package com.example.cardpuzzleapp

import kotlinx.serialization.Serializable
// --- ДОБАВЛЕНЫ ИМПОРТЫ ---
import kotlinx.serialization.InternalSerializationApi
import kotlin.OptIn

/**
 * Этот класс представляет корневую структуру нового единого JSON-файла.
 * Он используется LevelRepository для десериализации.
 */
// --- ДОБАВЛЕНА АННОТАЦИЯ ---
@OptIn(InternalSerializationApi::class)
@Serializable
data class LevelFile(
    val levelId: String,
    val cards: List<SentenceData> // Он напрямую парсит список в List<SentenceData>
)