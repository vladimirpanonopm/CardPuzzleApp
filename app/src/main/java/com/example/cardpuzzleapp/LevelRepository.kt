package com.example.cardpuzzleapp

import android.content.Context
import android.util.Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import java.io.IOException
// Мы импортируем data class из вашего файла Models.kt
import com.example.cardpuzzleapp.LevelEntry
import com.example.cardpuzzleapp.SentenceData
// --- ИСПРАВЛЕНИЕ ДЛЯ 'InternalSerializationApi' ---
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.ExperimentalSerializationApi
@OptIn(InternalSerializationApi::class, ExperimentalSerializationApi::class)
// -----------------------------


object LevelRepository {

    // Кэш для готовых SentenceData (как и был)
    private val levelCache = mutableMapOf<Int, List<SentenceData>>()

    // НОВЫЙ КЭШ: Map<LevelID, List<HebrewString>>
    // Хранит списки иврита для каждого уровня
    private val hebrewListCache = mutableMapOf<Int, List<String>>()

    private val jsonParser = Json { ignoreUnknownKeys = true }
    private const val DEBUG_TAG = AppDebug.TAG // Используем наш общий тег

    // Функция теперь загружает список иврита ДЛЯ КОНКРЕТНОГО УРОВНЯ
    private fun loadHebrewListForLevel(context: Context, levelId: Int): List<String> {
        // Загружаем из кэша, если уже загружали
        hebrewListCache[levelId]?.let {
            Log.d(DEBUG_TAG, "LevelRepository: loadHebrewListForLevel($levelId) from CACHE.")
            return it
        }

        val fileName = "hebrew_level_$levelId.json"
        Log.d(DEBUG_TAG, "LevelRepository: loadHebrewListForLevel($levelId) from ASSETS ($fileName).")
        return try {
            val jsonString = context.assets.open(fileName).bufferedReader().use { it.readText() }
            val stringList = jsonParser.decodeFromString<List<String>>(jsonString)
            hebrewListCache[levelId] = stringList // Сохраняем в кэш
            stringList
        } catch (e: Exception) {
            Log.e(DEBUG_TAG, "LevelRepository: ERROR in loadHebrewListForLevel($levelId)", e)
            emptyList()
        }
    }

    fun clearCache() {
        Log.d(DEBUG_TAG, "LevelRepository: Cache cleared.")
        levelCache.clear()
        hebrewListCache.clear() // <-- Очищаем оба кэша
    }

    // Главная функция getLevelData переписана
    fun getLevelData(context: Context, levelId: Int): List<SentenceData>? {
        Log.d(DEBUG_TAG, "LevelRepository: getLevelData($levelId) called.")
        if (levelCache.containsKey(levelId)) {
            Log.d(DEBUG_TAG, "LevelRepository: getLevelData($levelId) from CACHE.")
            return levelCache[levelId]
        }

        // 1. Загружаем список иврита (RTL) для этого уровня
        val hebrewStrings = loadHebrewListForLevel(context, levelId)
        if (hebrewStrings.isEmpty()) {
            Log.e(DEBUG_TAG, "LevelRepository: getLevelData($levelId) FAILED. Hebrew list is empty.")
            return null
        }

        val fileName = "level_$levelId.json"
        Log.d(DEBUG_TAG, "LevelRepository: getLevelData($levelId) loading LTR file: $fileName")
        return try {
            // 2. Загружаем LTR-файл (переводы, аудио, индексы)
            val jsonString = context.assets.open(fileName).bufferedReader().use { it.readText() }

            // 3. Парсим LTR-файл (используя LevelEntry)
            val levelEntries = jsonParser.decodeFromString<List<LevelEntry>>(jsonString)

            // 4. "Собираем" (мерджим) два файла в финальный список
            val sentences = levelEntries.mapNotNull { entry ->
                // Находим текст на иврите по индексу
                val hebrewText = hebrewStrings.getOrNull(entry.hebrew_index)
                if (hebrewText == null) {
                    Log.e(DEBUG_TAG, "LevelRepository: getLevelData($levelId) - Index '${entry.hebrew_index}' not found in hebrew_level_$levelId.json!")
                    return@mapNotNull null // Пропускаем эту карточку
                }

                // Создаем финальный объект SentenceData, который ожидает ViewModel
                SentenceData(
                    hebrew = hebrewText,
                    russian_translation = entry.russian_translation,
                    english_translation = entry.english_translation,
                    french_translation = entry.french_translation,
                    spanish_translation = entry.spanish_translation,
                    audioFilename = entry.audioFilename,
                    taskType = entry.taskType, // <-- ДОБАВЛЕНО
                    voice = entry.voice
                )
            }

            levelCache[levelId] = sentences
            Log.d(DEBUG_TAG, "LevelRepository: getLevelData($levelId) SUCCESS. ${sentences.size} sentences loaded.")
            sentences
        } catch (e: Exception) {
            Log.e(DEBUG_TAG, "LevelRepository: ERROR loading or parsing $fileName", e)
            null
        }
    }

    fun getLevelCount(context: Context): Int {
        Log.d(DEBUG_TAG, "LevelRepository: getLevelCount() called.")
        // Эта функция теперь должна считать ТОЛЬКО LTR-файлы
        return try {
            Log.d(DEBUG_TAG, "LevelRepository: Accessing context.assets.list()...")
            val files = context.assets.list("")
            val count = files?.count { it.startsWith("level_") && it.endsWith(".json") } ?: 0
            Log.d(DEBUG_TAG, "LevelRepository: getLevelCount() SUCCESS. Count: $count")
            count
        } catch (e: IOException) {
            Log.e(DEBUG_TAG, "LevelRepository: ERROR in getLevelCount()", e)
            e.printStackTrace()
            return 0
        }
    }

    fun findLongestSentence(context: Context): SentenceData? {
        clearCache()

        val levelCount = getLevelCount(context)
        if (levelCount == 0) return null

        var longestSentence: SentenceData? = null
        Log.d(DEBUG_TAG, "LevelRepository: --- Finding longest sentence ---")

        for (levelId in 1..levelCount) {
            val levelData = getLevelData(context, levelId)
            levelData?.forEach { sentence ->
                if (sentence.hebrew.length > (longestSentence?.hebrew?.length ?: -1)) {
                    longestSentence = sentence
                    Log.d(DEBUG_TAG, "--> New longest sentence found in level $levelId (length ${longestSentence?.hebrew?.length})")
                }
            }
        }
        Log.d(DEBUG_TAG, "--- Search complete. Result: ${longestSentence?.hebrew}")
        return longestSentence
    }
}