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

// --- НОВЫЙ КЛАСС ДАННЫХ ---
data class LevelMetadata(
    val levelId: Int,
    val totalRounds: Int,
    val filename: String
)
// --------------------------

@OptIn(InternalSerializationApi::class, ExperimentalSerializationApi::class)
@Singleton
class LevelRepository @Inject constructor(
    private val context: Context
) {
    private val levelCache = mutableMapOf<Int, List<SentenceData>>()
    private val mutex = Mutex()

    private val jsonParser = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    // Кэш метаданных (список уровней и кол-во раундов), чтобы не парсить файлы каждый раз
    private var levelsMetadataCache: List<LevelMetadata>? = null

    /**
     * Сканирует папку assets и возвращает список всех доступных уровней с количеством раундов.
     */
    suspend fun getLevelsMetadata(): List<LevelMetadata> = withContext(Dispatchers.IO) {
        if (levelsMetadataCache != null) {
            return@withContext levelsMetadataCache!!
        }

        val metadataList = mutableListOf<LevelMetadata>()
        try {
            val files = context.assets.list("") ?: emptyArray()
            // Фильтруем файлы level_X.json
            val levelFiles = files.filter { it.startsWith("level_") && it.endsWith(".json") }

            // Сортируем их по номеру (level_1, level_2, level_10)
            val sortedFiles = levelFiles.sortedBy {
                it.removePrefix("level_").removeSuffix(".json").toIntOrNull() ?: 999
            }

            for (fileName in sortedFiles) {
                val levelId = fileName.removePrefix("level_").removeSuffix(".json").toIntOrNull() ?: continue

                // Парсим файл, чтобы узнать количество раундов (cards.size)
                // Это быстрая операция, так как файлы локальные
                try {
                    val jsonString = context.assets.open(fileName).bufferedReader().use { it.readText() }
                    val levelFile = jsonParser.decodeFromString<LevelFile>(jsonString)
                    val roundCount = levelFile.cards.size

                    metadataList.add(LevelMetadata(levelId, roundCount, fileName))
                } catch (e: Exception) {
                    Log.e(DEBUG_TAG, "Error parsing metadata for $fileName", e)
                }
            }
        } catch (e: IOException) {
            Log.e(DEBUG_TAG, "Error listing assets", e)
        }

        levelsMetadataCache = metadataList
        return@withContext metadataList
    }

    suspend fun loadLevelDataIfNeeded(levelId: Int) = withContext(Dispatchers.IO) {
        if (levelCache.containsKey(levelId)) return@withContext

        mutex.withLock {
            if (levelCache.containsKey(levelId)) return@withLock

            val fileName = "level_$levelId.json"
            try {
                val jsonString = context.assets.open(fileName).bufferedReader().use { it.readText() }
                val levelFile = jsonParser.decodeFromString<LevelFile>(jsonString)
                val sentences = levelFile.cards
                levelCache[levelId] = sentences
            } catch (e: Exception) {
                Log.e(DEBUG_TAG, "LevelRepository: ERROR loading $fileName", e)
                levelCache[levelId] = emptyList()
            }
        }
    }

    fun getSentencesForLevel(levelId: Int): List<SentenceData> {
        return levelCache[levelId] ?: emptyList()
    }

    fun getSingleSentence(levelId: Int, roundIndex: Int): SentenceData? {
        return levelCache[levelId]?.getOrNull(roundIndex)
    }

    suspend fun getLevelCount(): Int {
        return getLevelsMetadata().size
    }

    // (Утилита поиска длинного предложения удалена за ненадобностью, код стал чище)

    companion object {
        private const val DEBUG_TAG = AppDebug.TAG
    }
}