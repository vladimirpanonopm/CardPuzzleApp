package com.example.cardpuzzleapp

import android.content.Context

class GameProgressManager(context: Context) {
    private val prefs = context.getSharedPreferences("game_progress_v3", Context.MODE_PRIVATE)

    private fun getLevelProgressKey(levelId: Int): String = "progress_level_$levelId"
    private fun getLevelArchiveKey(levelId: Int): String = "archived_level_$levelId"

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

    fun saveProgress(levelId: Int, roundIndex: Int) {
        val key = getLevelProgressKey(levelId)
        val currentProgress = getCompletedRounds(levelId).toMutableSet()
        currentProgress.add(roundIndex)
        val newSet = currentProgress.map { it.toString() }.toSet()
        prefs.edit().putStringSet(key, newSet).apply()
    }

    fun removeSingleRoundProgress(levelId: Int, roundIndex: Int) {
        val key = getLevelProgressKey(levelId)
        val currentProgress = getCompletedRounds(levelId).toMutableSet()
        if (currentProgress.remove(roundIndex)) {
            val newSet = currentProgress.map { it.toString() }.toSet()
            prefs.edit().putStringSet(key, newSet).apply()
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
        prefs.edit()
            .remove(progressKey)
            .remove(archiveKey)
            .apply()
    }

    fun resetAllProgress() {
        prefs.edit().clear().apply()
    }
}