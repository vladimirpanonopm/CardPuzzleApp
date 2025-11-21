package com.example.cardpuzzleapp

enum class RoundStatus {
    LOCKED,       // Серый (Закрыто)
    ACTIVE,       // Желтый (Текущая цель)
    PERFECT,      // Оранжевый (0 ошибок) - Элита
    GOOD,         // Зеленый (1-3 ошибки) - Хорошо
    PASSED        // Салатовый (>3 ошибок) - "Кое-как"
}

data class RoundNode(
    val levelId: Int,
    val roundIndex: Int,
    val status: RoundStatus,
    val label: String
)

data class LevelMapItem(
    val levelId: Int,
    val name: String,
    val nodes: List<RoundNode>,
    val isLocked: Boolean
)