package com.example.cardpuzzleapp

import android.content.Context
import android.util.Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import java.io.IOException
import com.example.cardpuzzleapp.SentenceData
import com.example.cardpuzzleapp.LevelFile
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(InternalSerializationApi::class, ExperimentalSerializationApi::class)
@Singleton
class LevelRepository @Inject constructor(
    private val context: Context
) {

    // levelCache остается, он теперь наш главный источник правды
    private val levelCache = mutableMapOf<Int, List<SentenceData>>()

    // Mutex (мьютекс) гарантирует, что мы не пытаемся загрузить один и тот же файл
    // из двух разных ViewModel одновременно (предотвращает гонку)
    private val mutex = Mutex()

    private val jsonParser = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    // --- НОВЫЙ ГЛАВНЫЙ МЕТОД ---
    /**
     * Асинхронно загружает данные уровня, ЕСЛИ их еще нет в кэше.
     * ViewModel'ы должны вызывать это ПЕРЕД тем, как запрашивать данные.
     */
    suspend fun loadLevelDataIfNeeded(levelId: Int) = withContext(Dispatchers.IO) {
        // Если данные уже в кэше, ничего не делаем. Мгновенный выход.
        if (levelCache.containsKey(levelId)) {
            Log.d(DEBUG_TAG, "LevelRepository: loadLevelDataIfNeeded($levelId) -> CACHE HIT")
            return@withContext
        }

        // Используем Mutex, чтобы только один поток мог выполнять этот код
        mutex.withLock {
            // Двойная проверка (на случай, если другой поток уже загрузил, пока мы ждали lock)
            if (levelCache.containsKey(levelId)) {
                return@withLock
            }

            Log.d(DEBUG_TAG, "LevelRepository: loadLevelDataIfNeeded($levelId) -> LOADING from assets...")
            val fileName = "level_$levelId.json"
            try {
                val jsonString = context.assets.open(fileName).bufferedReader().use { it.readText() }
                val levelFile = jsonParser.decodeFromString<LevelFile>(jsonString)
                val sentences = levelFile.cards

                // Сохраняем в кэш
                levelCache[levelId] = sentences
                Log.d(DEBUG_TAG, "LevelRepository: loadLevelDataIfNeeded($levelId) -> SUCCESS. ${sentences.size} sentences loaded.")
            } catch (e: Exception) {
                Log.e(DEBUG_TAG, "LevelRepository: ERROR loading or parsing $fileName", e)
                // Кэшируем пустой список, чтобы не пытаться снова
                levelCache[levelId] = emptyList()
            }
        }
    }

    // --- НОВЫЙ СИНХРОННЫЙ GETTER (для CardViewModel и JournalViewModel) ---
    /**
     * Синхронно возвращает ВСЕ предложения для уровня из кэша.
     * ВНИМАНИЕ: `loadLevelDataIfNeeded` должен быть вызван первым!
     */
    fun getSentencesForLevel(levelId: Int): List<SentenceData> {
        return levelCache[levelId] ?: emptyList()
    }

    // --- НОВЫЙ СИНХРОННЫЙ GETTER (для MatchingViewModel) ---
    /**
     * Синхронно возвращает ОДНО предложение из кэша.
     * ВНИМАНИЕ: `loadLevelDataIfNeeded` должен быть вызван первым!
     */
    fun getSingleSentence(levelId: Int, roundIndex: Int): SentenceData? {
        return levelCache[levelId]?.getOrNull(roundIndex)
    }

    // --- УДАЛЕНО ---
    // `getLevelData(levelId)` был удален.
    // `clearCache()` был удален.

    // getLevelCount остается без изменений.
    suspend fun getLevelCount(): Int = withContext(Dispatchers.IO) {
        return@withContext try {
            val files = context.assets.list("")
            val count = files?.count { it.startsWith("level_") && it.endsWith(".json") } ?: 0
            count
        } catch (e: IOException) {
            Log.e(DEBUG_TAG, "LevelRepository: ERROR in getLevelCount()", e)
            0
        }
    }

    // findLongestSentence должен быть обновлен, чтобы использовать новую логику
    suspend fun findLongestSentence(): SentenceData? {
        // Мы больше не чистим кэш
        // levelRepository.clearCache() // <-- УДАЛЕНО

        val levelCount = getLevelCount()
        if (levelCount == 0) return null

        var longestSentence: SentenceData? = null
        Log.d(DEBUG_TAG, "LevelRepository: --- Finding longest sentence ---")

        for (levelId in 1..levelCount) {
            // Используем новую логику
            loadLevelDataIfNeeded(levelId)
            val levelData = getSentencesForLevel(levelId)

            levelData.forEach { sentence ->
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