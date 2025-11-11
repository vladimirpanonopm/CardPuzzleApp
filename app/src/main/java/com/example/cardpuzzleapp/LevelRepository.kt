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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(InternalSerializationApi::class, ExperimentalSerializationApi::class)
// -----------------------------

@Singleton
class LevelRepository @Inject constructor(
    private val context: Context
) {

    private val levelCache = mutableMapOf<Int, List<SentenceData>>()
    private val hebrewListCache = mutableMapOf<Int, List<String>>()

    // --- ИЗМЕНЕНИЕ: Обучаем Json парсер работать с Enum ---
    private val jsonParser = Json {
        ignoreUnknownKeys = true
        // Если из JSON придет "ASSEMBLE_TRANSLATION",
        // он автоматически превратит это в TaskType.ASSEMBLE_TRANSLATION
        coerceInputValues = true // (Использует TaskType.UNKNOWN если значение не найдено)
    }
    // ----------------------------------------------------

    private suspend fun loadHebrewListForLevel(levelId: Int): List<String> = withContext(Dispatchers.IO) {
        hebrewListCache[levelId]?.let {
            Log.d(DEBUG_TAG, "LevelRepository: loadHebrewListForLevel($levelId) from CACHE.")
            return@withContext it
        }

        val fileName = "hebrew_level_$levelId.json"
        Log.d(DEBUG_TAG, "LevelRepository: loadHebrewListForLevel($levelId) from ASSETS ($fileName).")
        return@withContext try {
            val jsonString = context.assets.open(fileName).bufferedReader().use { it.readText() }
            val stringList = jsonParser.decodeFromString<List<String>>(jsonString)
            hebrewListCache[levelId] = stringList
            stringList
        } catch (e: Exception) {
            Log.e(DEBUG_TAG, "LevelRepository: ERROR in loadHebrewListForLevel($levelId)", e)
            emptyList()
        }
    }

    fun clearCache() {
        Log.d(DEBUG_TAG, "LevelRepository: Cache cleared.")
        levelCache.clear()
        hebrewListCache.clear()
    }

    suspend fun getLevelData(levelId: Int): List<SentenceData>? = withContext(Dispatchers.IO) {
        Log.d(DEBUG_TAG, "LevelRepository: getLevelData($levelId) called.")
        if (levelCache.containsKey(levelId)) {
            Log.d(DEBUG_TAG, "LevelRepository: getLevelData($levelId) from CACHE.")
            return@withContext levelCache[levelId]
        }

        val hebrewStrings = loadHebrewListForLevel(levelId)
        if (hebrewStrings.isEmpty()) {
            Log.e(DEBUG_TAG, "LevelRepository: getLevelData($levelId) FAILED. Hebrew list is empty.")
            return@withContext null
        }

        val fileName = "level_$levelId.json"
        Log.d(DEBUG_TAG, "LevelRepository: getLevelData($levelId) loading LTR file: $fileName")
        return@withContext try {
            val jsonString = context.assets.open(fileName).bufferedReader().use { it.readText() }
            val levelEntries = jsonParser.decodeFromString<List<LevelEntry>>(jsonString)

            val sentences = levelEntries.mapNotNull { entry ->
                val hebrewText = hebrewStrings.getOrNull(entry.hebrew_index)
                if (hebrewText == null) {
                    Log.e(DEBUG_TAG, "LevelRepository: getLevelData($levelId) - Index '${entry.hebrew_index}' not found!")
                    return@mapNotNull null
                }

                // --- ИЗМЕНЕНИЕ: Просто передаем данные. Enum уже готов. ---
                SentenceData(
                    hebrew = hebrewText,
                    russian_translation = entry.russian_translation,
                    english_translation = entry.english_translation,
                    french_translation = entry.french_translation,
                    spanish_translation = entry.spanish_translation,
                    audioFilename = entry.audioFilename,
                    taskType = entry.taskType, // (Теперь это Enum)
                    voice = entry.voice,
                    task_correct_cards = entry.task_correct_cards,
                    task_distractor_cards = entry.task_distractor_cards,
                    // --- ИСПРАВЛЕНИЕ: Передаем недостающее поле ---
                    task_pairs = entry.task_pairs
                    // ------------------------------------------
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

    suspend fun getLevelCount(): Int = withContext(Dispatchers.IO) {
        Log.d(DEBUG_TAG, "LevelRepository: getLevelCount() called.")
        return@withContext try {
            Log.d(DEBUG_TAG, "LevelRepository: Accessing context.assets.list()...")
            val files = context.assets.list("")
            val count = files?.count { it.startsWith("level_") && it.endsWith(".json") } ?: 0
            Log.d(DEBUG_TAG, "LevelRepository: getLevelCount() SUCCESS. Count: $count")
            count
        } catch (e: IOException) {
            Log.e(DEBUG_TAG, "LevelRepository: ERROR in getLevelCount()", e)
            e.printStackTrace()
            0
        }
    }

    suspend fun findLongestSentence(): SentenceData? {
        clearCache()

        val levelCount = getLevelCount()
        if (levelCount == 0) return null

        var longestSentence: SentenceData? = null
        Log.d(DEBUG_TAG, "LevelRepository: --- Finding longest sentence ---")

        for (levelId in 1..levelCount) {
            val levelData = getLevelData(levelId)
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

    companion object {
        private const val DEBUG_TAG = AppDebug.TAG
    }
}