package com.example.cardpuzzleapp

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import kotlin.math.min

@HiltViewModel
class AlefbetViewModel @Inject constructor(
    private val progressManager: GameProgressManager,
    private val audioPlayer: AudioPlayer
) : ViewModel() {

    val selectedLetters = androidx.compose.runtime.mutableStateListOf<HebrewLetter>()

    var availableLetters by mutableStateOf<List<HebrewLetter>>(emptyList())
        private set
    var currentFontStyle by mutableStateOf(FontStyle.CURSIVE)
        private set
    var isGameWon by mutableStateOf(false)
        private set
    var errorCardId by mutableStateOf<UUID?>(null)
        private set
    var errorCount by mutableStateOf(0)
        private set
    private val _hapticEventChannel = Channel<HapticEvent>()
    val hapticEvents = _hapticEventChannel.receiveAsFlow()
    var completionCount by mutableStateOf(0)
        private set

    // --- ДОБАВЛЕНО: Язык пользователя ---
    val userLanguage: String
        get() = progressManager.getUserLanguage() ?: "ru"
    // ------------------------------------

    val line1 by derivedStateOf {
        val letters = selectedLetters.map { it.letter }
        val breakPoint = if (currentFontStyle == FontStyle.REGULAR) 12 else 11
        letters.subList(0, min(letters.size, breakPoint)).joinToString("")
    }

    val line2 by derivedStateOf {
        val letters = selectedLetters.map { it.letter }
        val breakPoint = if (currentFontStyle == FontStyle.REGULAR) 12 else 11
        if (letters.size > breakPoint) {
            letters.subList(breakPoint, letters.size).joinToString("")
        } else {
            ""
        }
    }

    private val hebrewAlphabet = listOf(
        HebrewLetter(letter = "א", nameRU = "Алеф", nameEN = "Alef", nameFR = "Aleph", nameES = "Álef", audioFilename = "alef.mp3"),
        HebrewLetter(letter = "ב", nameRU = "Бет", nameEN = "Bet", nameFR = "Beth", nameES = "Bet", audioFilename = "bet.mp3"),
        HebrewLetter(letter = "ג", nameRU = "Гимель", nameEN = "Gimel", nameFR = "Gimel", nameES = "Guímel", audioFilename = "gimel.mp3"),
        HebrewLetter(letter = "ד", nameRU = "Далет", nameEN = "Dalet", nameFR = "Daleth", nameES = "Dálet", audioFilename = "dalet.mp3"),
        HebrewLetter(letter = "ה", nameRU = "Хэй", nameEN = "He", nameFR = "Hé", nameES = "He", audioFilename = "hey.mp3"),
        HebrewLetter(letter = "ו", nameRU = "Вав", nameEN = "Vav", nameFR = "Vav", nameES = "Vav", audioFilename = "vav.mp3"),
        HebrewLetter(letter = "ז", nameRU = "Заин", nameEN = "Zayin", nameFR = "Zayin", nameES = "Zayin", audioFilename = "zayin.mp3"),
        HebrewLetter(letter = "ח", nameRU = "Хет", nameEN = "Het", nameFR = "Heth", nameES = "Jet", audioFilename = "chet.mp3"),
        HebrewLetter(letter = "ט", nameRU = "Тет", nameEN = "Tet", nameFR = "Teth", nameES = "Tet", audioFilename = "tet.mp3"),
        HebrewLetter(letter = "י", nameRU = "Йуд", nameEN = "Yud", nameFR = "Yod", nameES = "Yod", audioFilename = "yud.mp3"),
        HebrewLetter(letter = "כ", nameRU = "Каф", nameEN = "Kaf", nameFR = "Kaph", nameES = "Kaf", audioFilename = "kaf.mp3"),
        HebrewLetter(letter = "ל", nameRU = "Ламед", nameEN = "Lamed", nameFR = "Lamed", nameES = "Lámed", audioFilename = "lamed.mp3"),
        HebrewLetter(letter = "מ", nameRU = "Мем", nameEN = "Mem", nameFR = "Mem", nameES = "Mem", audioFilename = "mem.mp3"),
        HebrewLetter(letter = "נ", nameRU = "Нун", nameEN = "Nun", nameFR = "Nun", nameES = "Nun", audioFilename = "nun.mp3"),
        HebrewLetter(letter = "ס", nameRU = "Самех", nameEN = "Samekh", nameFR = "Samekh", nameES = "Sámej", audioFilename = "samech.mp3"),
        HebrewLetter(letter = "ע", nameRU = "Аин", nameEN = "Ayin", nameFR = "Ayin", nameES = "Ayin", audioFilename = "ayin.mp3"),
        HebrewLetter(letter = "פ", nameRU = "Пей", nameEN = "Pei", nameFR = "Pé", nameES = "Pe", audioFilename = "pey.mp3"),
        HebrewLetter(letter = "צ", nameRU = "Цади", nameEN = "Tzadi", nameFR = "Tsadi", nameES = "Tsadi", audioFilename = "tzadi.mp3"),
        HebrewLetter(letter = "ק", nameRU = "Коф", nameEN = "Kof", nameFR = "Qoph", nameES = "Qof", audioFilename = "kof.mp3"),
        HebrewLetter(letter = "ר", nameRU = "Рейш", nameEN = "Reish", nameFR = "Resh", nameES = "Resh", audioFilename = "resh.mp3"),
        HebrewLetter(letter = "ש", nameRU = "Шин", nameEN = "Shin", nameFR = "Shin", nameES = "Shin", audioFilename = "shin.mp3"),
        HebrewLetter(letter = "ת", nameRU = "Тав", nameEN = "Tav", nameFR = "Tav", nameES = "Tav", audioFilename = "tav.mp3")
    )

    init {
        shuffleAndReset()
        completionCount = progressManager.getAlefbetCompletionCount()
    }

    private fun handleWin() {
        isGameWon = true
        progressManager.incrementAlefbetCompletionCount()
        completionCount = progressManager.getAlefbetCompletionCount()
    }

    fun selectLetter(letter: HebrewLetter) {
        // Логика проигрывания аудио теперь внутри ViewModel
        audioPlayer.play(letter.audioFilename)

        if (isGameWon || selectedLetters.any { it.id == letter.id }) {
            return
        }

        val nextCorrectLetter = hebrewAlphabet.getOrNull(selectedLetters.size)

        if (nextCorrectLetter != null && letter.letter == nextCorrectLetter.letter) {
            selectedLetters.add(letter)
            viewModelScope.launch { _hapticEventChannel.send(HapticEvent.Success) }

            if (selectedLetters.size == hebrewAlphabet.size) {
                handleWin()
            }
        } else {
            errorCount++
            errorCardId = letter.id
            viewModelScope.launch { _hapticEventChannel.send(HapticEvent.Failure) }
        }
    }

    private fun resetGame() {
        isGameWon = false
        selectedLetters.clear()
        errorCount = 0
        errorCardId = null
    }

    fun shuffleAndReset() {
        availableLetters = hebrewAlphabet.shuffled()
        resetGame()
    }



    fun toggleFont() {
        currentFontStyle = if (currentFontStyle == FontStyle.CURSIVE) FontStyle.REGULAR else FontStyle.CURSIVE
    }
}