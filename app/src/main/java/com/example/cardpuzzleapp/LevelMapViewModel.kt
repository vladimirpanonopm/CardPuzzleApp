package com.example.cardpuzzleapp

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LevelMapViewModel @Inject constructor(
    private val levelRepository: LevelRepository,
    private val progressManager: GameProgressManager
) : ViewModel() {

    var levels by mutableStateOf<List<LevelMapItem>>(emptyList())
        private set

    var isLoading by mutableStateOf(true)
        private set

    fun loadMap() {
        viewModelScope.launch {
            isLoading = true

            val metadataList = levelRepository.getLevelsMetadata()
            val mapItems = mutableListOf<LevelMapItem>()

            var isPreviousLevelFullyCompleted = true

            for (meta in metadataList) {
                val levelId = meta.levelId
                val totalRounds = meta.totalRounds

                val completedSet = progressManager.getCompletedRounds(levelId)
                val archivedSet = progressManager.getArchivedRounds(levelId)
                val allPassedRounds = completedSet + archivedSet

                // Получаем рекорды
                val scoresMap = progressManager.getRoundBestErrors(levelId)

                val isLevelUnlocked = isPreviousLevelFullyCompleted
                val nodes = mutableListOf<RoundNode>()

                for (i in 0 until totalRounds) {
                    val isPassed = allPassedRounds.contains(i)

                    val status = if (!isLevelUnlocked) {
                        RoundStatus.LOCKED
                    } else if (isPassed) {
                        // --- ИСПРАВЛЕНИЕ: Честная проверка ошибок для ВСЕХ (включая архив) ---
                        // Если записи нет (старый сейв), считаем 0 (Perfect)
                        val errors = scoresMap[i] ?: 0

                        when {
                            errors == 0 -> RoundStatus.PERFECT // Оранжевый
                            errors <= 3 -> RoundStatus.GOOD    // Зеленый
                            else -> RoundStatus.PASSED         // Салатовый
                        }
                        // ---------------------------------------------------------------------
                    } else {
                        val prevRoundsCompleted = (0 until i).all { allPassedRounds.contains(it) }
                        if (prevRoundsCompleted) RoundStatus.ACTIVE else RoundStatus.LOCKED
                    }

                    nodes.add(RoundNode(
                        levelId = levelId,
                        roundIndex = i,
                        status = status,
                        label = "${i + 1}"
                    ))
                }

                mapItems.add(LevelMapItem(
                    levelId = levelId,
                    name = "Уровень $levelId",
                    nodes = nodes,
                    isLocked = !isLevelUnlocked
                ))

                isPreviousLevelFullyCompleted = (allPassedRounds.size >= totalRounds) && totalRounds > 0
            }

            levels = mapItems
            isLoading = false
        }
    }
}