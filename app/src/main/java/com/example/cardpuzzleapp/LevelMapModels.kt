package com.example.cardpuzzleapp

import java.util.UUID

enum class RoundStatus {
    LOCKED,     // Серый замок (нельзя нажать)
    ACTIVE,     // Желтый пульсирующий (текущая цель)
    COMPLETED   // Зеленый (можно повторить)
}

/**
 * Модель одного "Кружка" на карте.
 */
data class RoundNode(
    val levelId: Int,
    val roundIndex: Int,
    val status: RoundStatus,
    val label: String // "1", "2" или иконка
)

/**
 * Модель целого Уровня (Главы) на карте.
 * Содержит заголовок и список кружков.
 */
data class LevelMapItem(
    val levelId: Int,
    val name: String, // "Уровень 1"
    val nodes: List<RoundNode>,
    val isLocked: Boolean // Если true, весь уровень серый
)