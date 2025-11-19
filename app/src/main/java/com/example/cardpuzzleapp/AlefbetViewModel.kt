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
        HebrewLetter(letter = "א", nameRU = "Алеф", audioFilename = "alef.mp3"),
        HebrewLetter(letter = "ב", nameRU = "Бет", audioFilename = "bet.mp3"),
        HebrewLetter(letter = "ג", nameRU = "Гимель", audioFilename = "gimel.mp3"),
        HebrewLetter(letter = "ד", nameRU = "Далет", audioFilename = "dalet.mp3"),
        HebrewLetter(letter = "ה", nameRU = "Хэй", audioFilename = "hey.mp3"),
        HebrewLetter(letter = "ו", nameRU = "Вав", audioFilename = "vav.mp3"),
        HebrewLetter(letter = "ז", nameRU = "Заин", audioFilename = "zayin.mp3"),
        HebrewLetter(letter = "ח", nameRU = "Хет", audioFilename = "chet.mp3"),
        HebrewLetter(letter = "ט", nameRU = "Тет", audioFilename = "tet.mp3"),
        HebrewLetter(letter = "י", nameRU = "Йуд", audioFilename = "yud.mp3"),
        HebrewLetter(letter = "כ", nameRU = "Каф", audioFilename = "kaf.mp3"),
        HebrewLetter(letter = "ל", nameRU = "Ламед", audioFilename = "lamed.mp3"),
        HebrewLetter(letter = "מ", nameRU = "Мем", audioFilename = "mem.mp3"),
        HebrewLetter(letter = "נ", nameRU = "Нун", audioFilename = "nun.mp3"),
        HebrewLetter(letter = "ס", nameRU = "Самех", audioFilename = "samech.mp3"),
        HebrewLetter(letter = "ע", nameRU = "Аин", audioFilename = "ayin.mp3"),
        HebrewLetter(letter = "פ", nameRU = "Пей", audioFilename = "pey.mp3"),
        HebrewLetter(letter = "צ", nameRU = "Цади", audioFilename = "tzadi.mp3"),
        HebrewLetter(letter = "ק", nameRU = "Коф", audioFilename = "kof.mp3"),
        HebrewLetter(letter = "ר", nameRU = "Рейш", audioFilename = "resh.mp3"),
        HebrewLetter(letter = "ש", nameRU = "Шин", audioFilename = "shin.mp3"),
        HebrewLetter(letter = "ת", nameRU = "Тав", audioFilename = "tav.mp3")
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