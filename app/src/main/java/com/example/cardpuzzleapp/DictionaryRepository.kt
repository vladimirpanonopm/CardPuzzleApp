package com.example.cardpuzzleapp

import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Этот репозиторий отвечает за создание "Глобального словаря".
 * Он собирает ВСЕ пройденные "Matching Pairs" из ВСЕХ уровней.
 */
@Singleton
class DictionaryRepository @Inject constructor(
    private val levelRepository: LevelRepository,
    private val progressManager: GameProgressManager
) {
    private val DEBUG_TAG = AppDebug.TAG

    // Кэш, чтобы не собирать словарь каждый раз
    private var dictionaryCache: List<List<String>>? = null

    /**
     * Собирает и возвращает отсортированный список всех пройденных пар.
     * @param forceRefresh Заставит репозиторий пересобрать словарь (например, после прохождения нового уровня).
     */
    suspend fun getGlobalDictionary(forceRefresh: Boolean = false): List<List<String>> = withContext(Dispatchers.IO) {
        if (!forceRefresh && dictionaryCache != null) {
            Log.d(DEBUG_TAG, "DictionaryRepository: Возвращаем словарь из кэша (${dictionaryCache?.size} слов)")
            return@withContext dictionaryCache!!
        }

        Log.d(DEBUG_TAG, "DictionaryRepository: Кэш пуст. Собираем глобальный словарь...")
        // --- ИЗМЕНЕНИЕ: Используем LinkedHashSet для сохранения порядка вставки ---
        val globalDictionary = LinkedHashSet<List<String>>()
        // --- КОНЕЦ ИЗМЕНЕНИЯ ---

        try {
            val levelCount = levelRepository.getLevelCount()
            if (levelCount == 0) {
                Log.w(DEBUG_TAG, "DictionaryRepository: Уровни не найдены (levelCount == 0)")
                return@withContext emptyList()
            }

            // 1. Проходим по всем уровням (от 1 до N)
            for (levelId in 1..levelCount) {
                // 2. Убеждаемся, что данные уровня загружены в кэш LevelRepository
                levelRepository.loadLevelDataIfNeeded(levelId)

                // 3. Получаем список ВСЕХ раундов в уровне
                val allRoundsInLevel = levelRepository.getSentencesForLevel(levelId)

                // 4. Получаем ID только ПРОЙДЕННЫХ раундов
                val completedRoundIndices = progressManager.getCompletedRounds(levelId)

                if (completedRoundIndices.isEmpty()) {
                    continue // Пропускаем уровень, если в нем ничего не пройдено
                }

                // 5. Фильтруем
                val completedMatchingPairs = allRoundsInLevel
                    .filterIndexed { index, data ->
                        // A) Это пройденный раунд
                        index in completedRoundIndices &&
                                // Б) Это 'MATCHING_PAIRS'
                                data.taskType == TaskType.MATCHING_PAIRS &&
                                // В) У него есть пары
                                data.task_pairs != null
                    }
                    .flatMap { it.task_pairs!! } // 6. Собираем все пары в один список

                globalDictionary.addAll(completedMatchingPairs) // LinkedHashSet сохранит порядок
            }

            // --- ИЗМЕНЕНИЕ: Убираем сортировку, просто конвертируем в список ---
            val finalList = globalDictionary.toList()
            dictionaryCache = finalList

            Log.d(DEBUG_TAG, "DictionaryRepository: Сборка завершена. Найдено ${finalList.size} уникальных пар.")
            return@withContext finalList
            // --- КОНЕЦ ИЗМЕНЕНИЯ ---

        } catch (e: Exception) {
            Log.e(DEBUG_TAG, "DictionaryRepository: Ошибка при сборке словаря", e)
            return@withContext emptyList()
        }
    }
}