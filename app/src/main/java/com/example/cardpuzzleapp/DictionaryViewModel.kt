package com.example.cardpuzzleapp

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * ViewModel для "Глобального Словаря".
 * Отвечает за загрузку, кэширование и фильтрацию
 * ВСЕХ пройденных 'Matching Pairs'.
 */
@HiltViewModel
class DictionaryViewModel @Inject constructor(
    private val dictionaryRepository: DictionaryRepository,
    private val ttsPlayer: TtsPlayer
) : ViewModel() {

    // --- ИЗМЕНЕНИЕ: Тег для логов ---
    private val DEBUG_TAG = "SEARCH_DEBUG"
    // --- КОНЕЦ ИЗМЕНЕНИЯ ---

    // --- Состояния UI ---

    var isLoading by mutableStateOf(true)
        private set

    var searchText by mutableStateOf("")
        private set

    var filteredDictionary by mutableStateOf<List<List<String>>>(emptyList())
        private set

    // --- Приватные состояния ---

    private var _fullDictionary = listOf<List<String>>()
    private var loadJob: Job? = null

    init {
        loadDictionary(forceRefresh = false)
    }

    /**
     * Загружает полный словарь из репозитория.
     */
    fun loadDictionary(forceRefresh: Boolean) {
        if (loadJob?.isActive == true) {
            Log.d(DEBUG_TAG, "DictionaryViewModel: Загрузка уже идет...")
            return
        }

        loadJob = viewModelScope.launch(Dispatchers.Main) {
            isLoading = true
            Log.d(DEBUG_TAG, "DictionaryViewModel: Загрузка словаря...")

            val dictionary = dictionaryRepository.getGlobalDictionary(forceRefresh)

            _fullDictionary = dictionary
            filterDictionary(searchText)

            isLoading = false
            Log.d(DEBUG_TAG, "DictionaryViewModel: Словарь загружен (${dictionary.size} слов)")
        }
    }

    /**
     * Вызывается, когда пользователь вводит текст в поле поиска.
     */
    fun onSearchTextChanged(query: String) {
        searchText = query
        filterDictionary(query)
    }

    /**
     * Вызывается, когда пользователь нажимает на слово на иврите в списке.
     */
    fun onHebrewWordClicked(word: String) {
        ttsPlayer.speak(word)
    }

    /**
     * Фильтрует полный словарь (4000 слов) на основе запроса.
     */
    private fun filterDictionary(query: String) {
        if (query.isBlank()) {
            filteredDictionary = _fullDictionary
            return
        }

        viewModelScope.launch(Dispatchers.Default) {

            val isQueryHebrew = query.isHebrew()
            val queryForTranslation = query.lowercase()
            val queryForHebrew = query.stripNikud()

            // --- ИЗМЕНЕНИЕ: Добавляем логи ---
            Log.d(DEBUG_TAG, "--- Новый поиск ---")
            Log.d(DEBUG_TAG, "Запрос: '$query', Это иврит: $isQueryHebrew")
            Log.d(DEBUG_TAG, "Запрос (очищенный иврит): '$queryForHebrew'")
            Log.d(DEBUG_TAG, "Запрос (очищенный перевод): '$queryForTranslation'")
            // --- КОНЕЦ ИЗМЕНЕНИЯ ---

            val result = _fullDictionary.filter { pair ->
                val hebrewWord = pair.getOrNull(0) ?: ""
                val translationWord = pair.getOrNull(1) ?: ""

                if (isQueryHebrew) {
                    // --- ИЗМЕНЕНИЕ: Логи ---
                    val strippedHebrewWord = hebrewWord.stripNikud()
                    val result = strippedHebrewWord.contains(queryForHebrew)
                    Log.d(DEBUG_TAG, "Иврит: Сравниваем '$strippedHebrewWord' (из словаря) с '$queryForHebrew' (запрос). Результат: $result")
                    result
                    // --- КОНЕЦ ИЗМЕНЕНИЯ ---
                } else {
                    // --- ИЗМЕНЕНИЕ: Логи ---
                    val lowerCaseTranslationWord = translationWord.lowercase()
                    val result = lowerCaseTranslationWord.contains(queryForTranslation)
                    Log.d(DEBUG_TAG, "Перевод: Сравниваем '$lowerCaseTranslationWord' (из словаря) с '$queryForTranslation' (запрос). Результат: $result")
                    result
                    // --- КОНЕЦ ИЗМЕНЕНИЯ ---
                }
            }

            withContext(Dispatchers.Main) {
                filteredDictionary = result
            }
        }
    }
}