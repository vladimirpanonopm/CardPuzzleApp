package com.example.cardpuzzleapp

import android.content.Context

class GameProgressManager(context: Context) {
    private val prefs = context.getSharedPreferences("game_progress_v3", Context.MODE_PRIVATE)

    private val PREF_KEY_LANGUAGE = "user_language"
    private fun getLevelProgressKey(levelId: Int): String = "progress_level_$levelId"
    private fun getLevelArchiveKey(levelId: Int): String = "archived_level_$levelId"

    // Новое хранилище для рекордов (ошибок)
    private fun getLevelScoresKey(levelId: Int): String = "scores_level_$levelId"

    private val PREF_KEY_JOURNAL_FONT_SIZE = "journal_font_size_v2"
    private val PREF_KEY_JOURNAL_FONT_STYLE = "journal_font_style_v2"
    private val PREF_KEY_ALEFBET_COMPLETION_COUNT = "alefbet_completion_count"
    private val PREF_KEY_LEVEL1_FONT_STYLE = "level1_font_style"

    fun saveLevel1FontStyle(style: FontStyle) {
        prefs.edit().putString(PREF_KEY_LEVEL1_FONT_STYLE, style.name).apply()
    }

    fun getLevel1FontStyle(): FontStyle {
        val styleName = prefs.getString(PREF_KEY_LEVEL1_FONT_STYLE, FontStyle.CURSIVE.name)
        return try {
            FontStyle.valueOf(styleName ?: FontStyle.CURSIVE.name)
        } catch (e: IllegalArgumentException) {
            FontStyle.CURSIVE
        }
    }

    fun getAlefbetCompletionCount(): Int {
        return prefs.getInt(PREF_KEY_ALEFBET_COMPLETION_COUNT, 0)
    }

    fun incrementAlefbetCompletionCount() {
        val currentCount = getAlefbetCompletionCount()
        prefs.edit().putInt(PREF_KEY_ALEFBET_COMPLETION_COUNT, currentCount + 1).apply()
    }

    // --- Основные методы прогресса ---

    fun getCompletedRounds(levelId: Int): Set<Int> {
        val key = getLevelProgressKey(levelId)
        val savedSet = prefs.getStringSet(key, null) ?: return emptySet()
        return savedSet.mapNotNull { it.toIntOrNull() }.toSet()
    }

    fun getArchivedRounds(levelId: Int): Set<Int> {
        val key = getLevelArchiveKey(levelId)
        val savedSet = prefs.getStringSet(key, null) ?: return emptySet()
        return savedSet.mapNotNull { it.toIntOrNull() }.toSet()
    }

    /**
     * Возвращает карту лучших результатов: [Index -> MinErrors]
     */
    fun getRoundBestErrors(levelId: Int): Map<Int, Int> {
        val key = getLevelScoresKey(levelId)
        val savedSet = prefs.getStringSet(key, null) ?: return emptyMap()

        // Формат строки: "index:errors" (например "0:5", "1:0")
        return savedSet.mapNotNull { str ->
            val parts = str.split(":")
            if (parts.size == 2) {
                val index = parts[0].toIntOrNull()
                val errors = parts[1].toIntOrNull()
                if (index != null && errors != null) index to errors else null
            } else null
        }.toMap()
    }

    /**
     * Сохраняет прогресс с учетом количества ошибок.
     * Если результат лучше (меньше ошибок), чем был - обновляем.
     */
    fun saveProgress(levelId: Int, roundIndex: Int, errorCount: Int = 0) {
        // 1. Сохраняем факт прохождения (как раньше)
        val progressKey = getLevelProgressKey(levelId)
        val currentProgress = getCompletedRounds(levelId).toMutableSet()
        currentProgress.add(roundIndex)
        prefs.edit().putStringSet(progressKey, currentProgress.map { it.toString() }.toSet()).apply()

        // 2. Сохраняем рекорд (ошибки)
        val scoresKey = getLevelScoresKey(levelId)
        val currentScores = getRoundBestErrors(levelId).toMutableMap()

        val oldRecord = currentScores[roundIndex]

        // Обновляем, если раньше не проходили ИЛИ если новый результат лучше (меньше ошибок)
        if (oldRecord == null || errorCount < oldRecord) {
            currentScores[roundIndex] = errorCount

            // Сериализуем обратно в Set<String>
            val newSet = currentScores.map { "${it.key}:${it.value}" }.toSet()
            prefs.edit().putStringSet(scoresKey, newSet).apply()
        }
    }

    // ... (остальные методы: шрифты, archiveRound, reset и т.д. без изменений) ...

    fun saveJournalFontSize(size: Float) {
        prefs.edit().putFloat(PREF_KEY_JOURNAL_FONT_SIZE, size).apply()
    }

    fun getJournalFontSize(): Float {
        return prefs.getFloat(PREF_KEY_JOURNAL_FONT_SIZE, 32f)
    }

    fun saveJournalFontStyle(style: FontStyle) {
        prefs.edit().putString(PREF_KEY_JOURNAL_FONT_STYLE, style.name).apply()
    }

    fun getJournalFontStyle(): FontStyle {
        val styleName = prefs.getString(PREF_KEY_JOURNAL_FONT_STYLE, FontStyle.REGULAR.name)
        return FontStyle.valueOf(styleName ?: FontStyle.REGULAR.name)
    }

    fun removeSingleRoundProgress(levelId: Int, roundIndex: Int) {
        val key = getLevelProgressKey(levelId)
        val currentProgress = getCompletedRounds(levelId).toMutableSet()
        if (currentProgress.remove(roundIndex)) {
            val newSet = currentProgress.map { it.toString() }.toSet()
            prefs.edit().putStringSet(key, newSet).apply()
        }

        // Удаляем и рекорд тоже
        val scoresKey = getLevelScoresKey(levelId)
        val currentScores = getRoundBestErrors(levelId).toMutableMap()
        if (currentScores.remove(roundIndex) != null) {
            val newSet = currentScores.map { "${it.key}:${it.value}" }.toSet()
            prefs.edit().putStringSet(scoresKey, newSet).apply()
        }
    }

    fun archiveRound(levelId: Int, roundIndex: Int) {
        val progressKey = getLevelProgressKey(levelId)
        val archiveKey = getLevelArchiveKey(levelId)
        val currentProgress = getCompletedRounds(levelId).toMutableSet()
        val currentArchive = getArchivedRounds(levelId).toMutableSet()

        currentProgress.remove(roundIndex)
        currentArchive.add(roundIndex)

        val newProgressSet = currentProgress.map { it.toString() }.toSet()
        val newArchiveSet = currentArchive.map { it.toString() }.toSet()
        prefs.edit()
            .putStringSet(progressKey, newProgressSet)
            .putStringSet(archiveKey, newArchiveSet)
            .apply()
    }

    fun resetLevelProgress(levelId: Int) {
        val progressKey = getLevelProgressKey(levelId)
        val archiveKey = getLevelArchiveKey(levelId)
        val scoresKey = getLevelScoresKey(levelId)
        prefs.edit()
            .remove(progressKey)
            .remove(archiveKey)
            .remove(scoresKey)
            .apply()
    }

    fun resetAllProgressExceptLanguage() {
        val editor = prefs.edit()
        prefs.all.keys.forEach { key ->
            if (key != PREF_KEY_LANGUAGE) {
                editor.remove(key)
            }
        }
        editor.apply()
    }

    fun saveUserLanguage(language: String) {
        prefs.edit().putString(PREF_KEY_LANGUAGE, language).apply()
    }

    fun getUserLanguage(): String? {
        return prefs.getString(PREF_KEY_LANGUAGE, null)
    }
}