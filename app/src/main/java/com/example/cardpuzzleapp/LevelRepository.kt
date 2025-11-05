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
    private const val DEBUG_TAG = "DEBUG_REPO"

    // Функция теперь загружает список иврита ДЛЯ КОНКРЕТНОГО УРОВНЯ
    private fun loadHebrewListForLevel(context: Context, levelId: Int): List<String> {
        // Загружаем из кэша, если уже загружали
        hebrewListCache[levelId]?.let {
            Log.d(DEBUG_TAG, "Загрузка hebrew_level_$levelId.json из КЭША.")
            return it
        }

        val fileName = "hebrew_level_$levelId.json"
        Log.d(DEBUG_TAG, "Загрузка $fileName из ASSETS.")
        return try {
            val jsonString = context.assets.open(fileName).bufferedReader().use { it.readText() }
            val stringList = jsonParser.decodeFromString<List<String>>(jsonString)
            hebrewListCache[levelId] = stringList // Сохраняем в кэш
            stringList
        } catch (e: Exception) {
            Log.e(DEBUG_TAG, "ОШИБКА при загрузке $fileName", e)
            emptyList()
        }
    }

    fun clearCache() {
        Log.d(DEBUG_TAG, "Кэш в памяти очищен.")
        levelCache.clear()
        hebrewListCache.clear() // <-- Очищаем оба кэша
    }

    // Главная функция getLevelData переписана
    fun getLevelData(context: Context, levelId: Int): List<SentenceData>? {
        if (levelCache.containsKey(levelId)) {
            Log.d(DEBUG_TAG, "Загрузка уровня $levelId из КЭША.")
            return levelCache[levelId]
        }

        // 1. Загружаем список иврита (RTL) для этого уровня
        val hebrewStrings = loadHebrewListForLevel(context, levelId)
        if (hebrewStrings.isEmpty()) {
            Log.e(DEBUG_TAG, "Список иврита для уровня $levelId пуст! Не могу собрать уровень.")
            return null
        }

        Log.d(DEBUG_TAG, "Загрузка level_$levelId.json (LTR) из ASSETS.")
        return try {
            // 2. Загружаем LTR-файл (переводы, аудио, индексы)
            val fileName = "level_$levelId.json"
            val jsonString = context.assets.open(fileName).bufferedReader().use { it.readText() }

            Log.d(DEBUG_TAG, "--- Содержимое файла $fileName ---")
            Log.d(DEBUG_TAG, jsonString)
            Log.d(DEBUG_TAG, "--- Конец содержимого файла ---")

            // 3. Парсим LTR-файл (используя LevelEntry)
            val levelEntries = jsonParser.decodeFromString<List<LevelEntry>>(jsonString)

            // 4. "Собираем" (мерджим) два файла в финальный список
            val sentences = levelEntries.mapNotNull { entry ->
                // Находим текст на иврите по индексу
                val hebrewText = hebrewStrings.getOrNull(entry.hebrew_index)
                if (hebrewText == null) {
                    Log.e(DEBUG_TAG, "Индекс '${entry.hebrew_index}' не найден в hebrew_level_$levelId.json!")
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
                    imageName = entry.imageName
                )
            }

            levelCache[levelId] = sentences
            sentences
        } catch (e: Exception) {
            Log.e(DEBUG_TAG, "ОШИБКА при загрузке или парсинге $levelId.json", e)
            null
        }
    }

    fun getLevelCount(context: Context): Int {
        // Эта функция теперь должна считать ТОЛЬКО LTR-файлы
        return try {
            context.assets.list("")?.count { it.startsWith("level_") && it.endsWith(".json") } ?: 0
        } catch (e: IOException) {
            e.printStackTrace()
            return 0
        }
    }

    fun findLongestSentence(context: Context): SentenceData? {
        clearCache()

        val levelCount = getLevelCount(context)
        if (levelCount == 0) return null

        var longestSentence: SentenceData? = null
        Log.d(DEBUG_TAG, "--- НАЧИНАЮ ПОИСК САМОГО ДЛИННОГО ПРЕДЛОЖЕНИЯ ---")

        for (levelId in 1..levelCount) {
            val levelData = getLevelData(context, levelId)
            levelData?.forEach { sentence ->
                if (sentence.hebrew.length > (longestSentence?.hebrew?.length ?: -1)) {
                    longestSentence = sentence
                    Log.d(DEBUG_TAG, "--> Найдено новое самое длинное предложение в level $levelId (длина ${longestSentence?.hebrew?.length})")
                }
            }
        }
        Log.d(DEBUG_TAG, "--- ПОИСК ЗАВЕРШЕН ---")
        Log.d(DEBUG_TAG, "Финальный результат: ${longestSentence?.hebrew}")
        return longestSentence
    }
}