package com.example.cardpuzzleapp

import androidx.compose.ui.unit.dp

/**
 * Объект для хранения констант, связанных с компоновкой игрового поля.
 */
object LayoutConstants {
    /**
     * Настройки для прописного шрифта (Уровень 1)
     */
    val CURSIVE_LINE_HEIGHT = 50.dp
    const val CURSIVE_LINE_COUNT = 5

    /**
     * Настройки для строчного шрифта (Остальные уровни)
     */
    val REGULAR_LINE_HEIGHT = 50.dp
    const val REGULAR_LINE_COUNT = 6
}
