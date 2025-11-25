package com.example.cardpuzzleapp

import android.annotation.SuppressLint
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class AudioSegment(
    val text: String,
    @SerialName("start_ms")
    val startMs: Long,
    @SerialName("end_ms")
    val endMs: Long
)

@OptIn(InternalSerializationApi::class)
@Serializable
data class SentenceData(
    @SerialName("uiDisplayTitle")
    val hebrew: String,

    @SerialName("gamePrompt")
    val gamePrompt: String? = null,

    @SerialName("translationPrompt")
    val translation: String? = null,

    val audioFilename: String?,

    val segments: List<AudioSegment>? = null,

    val taskType: TaskType = TaskType.UNKNOWN,

    val voice: String? = null,

    // --- Флаг инверсии логики (но не колонок!) ---
    @SerialName("swapColumns")
    val swapColumns: Boolean = false,
    // ---------------------------------------------

    @SerialName("correctOptions")
    val task_correct_cards: List<String>? = null,

    @SerialName("taskTargetCards")
    val task_target_cards: List<String>? = null,

    @SerialName("distractorOptions")
    val task_distractor_cards: List<String>? = null,

    @SerialName("taskPairs")
    val task_pairs: List<List<String>>? = null
)