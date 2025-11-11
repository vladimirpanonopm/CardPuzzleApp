package com.example.cardpuzzleapp

import android.util.Log
import android.app.Activity // <<< НОВЫЙ ИМПОРТ
import android.content.Context // <<< НОВЫЙ ИМПОРТ
import android.content.ContextWrapper // <<< НОВЫЙ ИМПОРТ

fun parseSentenceToCards(sentence: String, dictionary: Map<String, String>): List<Card> {

    // --- ИЗМЕНЕНИЕ ЗДЕСЬ ---
    // Группа 1 (core): Только слово.
    // Группа 2 (tail): Вся пунктуация, пробелы И переносы строк ПОСЛЕ слова.
    val wordFinder = Regex(
        """((?:\p{Nd}+(?:[.:]\p{Nd}+)*)|(?:[\p{L}\p{M}\u05BE\u05F3\u05F4'’-]+))([.,:?!\n\r \t\f\v]*)"""
    )
    // --- КОНЕЦ ИЗМЕНЕНИЯ ---

    val cards = wordFinder.findAll(sentence).map {
        val core = it.groupValues[1]         // Только слово (напр. "אני")
        val tail = it.groupValues[2]         // Все остальное (напр. " \n")

        val text = core + tail

        Card(
            text = text,
            translation = dictionary[text] ?: dictionary[core] ?: ""
        )
    }.toList()

    return cards
}

// --------------------------------------