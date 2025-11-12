package com.example.cardpuzzleapp

import android.content.Context
import android.util.Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import java.io.IOException
// --- УДАЛЕН ИМПОРТ LevelEntry ---
import com.example.cardpuzzleapp.SentenceData
// --- ДОБАВЛЕН ИМПОРТ LevelFile ---
import com.example.cardpuzzleapp.LevelFile
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

    // levelCache остается без изменений
    private val levelCache = mutableMapOf<Int, List<SentenceData>>()
    // hebrewListCache УДАЛЕН

    // jsonParser остается без изменений
    private val jsonParser = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    // --- ФУНКЦИЯ loadHebrewListForLevel УДАЛЕНА ---

    fun clearCache() {
        Log.d(DEBUG_TAG, "LevelRepository: Cache cleared.")
        levelCache.clear()
        // hebrewListCache.clear() УДАЛЕНО
    }

    /**
     * --- ЛОГИКА ПОЛНОСТЬЮ ПЕРЕПИСАНА ---
     * Загружает данные уровня из ЕДИНОГО JSON-файла.
     */
    suspend fun getLevelData(levelId: Int): List<SentenceData>? = withContext(Dispatchers.IO) {
        Log.d(DEBUG_TAG, "LevelRepository: getLevelData($levelId) called.")

        // 1. Проверяем кэш (логика осталась)
        levelCache[levelId]?.let {
            Log.d(DEBUG_TAG, "LevelRepository: getLevelData($levelId) from CACHE.")
            return@withContext it
        }

        // 2. Загружаем ЕДИНСТВЕННЫЙ файл
        val fileName = "level_$levelId.json"
        Log.d(DEBUG_TAG, "LevelRepository: getLevelData($levelId) loading SINGLE file: $fileName")
        return@withContext try {
            val jsonString = context.assets.open(fileName).bufferedReader().use { it.readText() }

            // 3. Парсим в новый корневой объект LevelFile
            val levelFile = jsonParser.decodeFromString<LevelFile>(jsonString)

            // 4. Получаем список карточек (уже в формате List<SentenceData> благодаря @SerialName)
            val sentences = levelFile.cards

            // 5. Кэшируем и возвращаем
            levelCache[levelId] = sentences
            Log.d(DEBUG_TAG, "LevelRepository: getLevelData($levelId) SUCCESS. ${sentences.size} sentences loaded from new format.")
            sentences

        } catch (e: Exception) {
            Log.e(DEBUG_TAG, "LevelRepository: ERROR loading or parsing $fileName", e)
            null
        }
    }
    // --- КОНЕЦ ПЕРЕПИСАННОЙ ЛОГИКИ ---


    // getLevelCount остается без изменений. Он и раньше искал 'level_X.json'.
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

    // findLongestSentence остается без изменений. Он работает с getLevelData.
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