package com.example.cardpuzzleapp

import kotlinx.serialization.Serializable

@Serializable
enum class TaskType {
    ASSEMBLE_TRANSLATION,
    AUDITION,
    FILL_IN_BLANK,
    QUIZ,
    CONJUGATION,
    MATCHING_PAIRS,
    MAKE_QUESTION,
    MAKE_ANSWER, // --- НОВОЕ ---
    UNKNOWN
}