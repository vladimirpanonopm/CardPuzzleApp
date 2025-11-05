package com.example.cardpuzzleapp

import android.util.Log

fun parseSentenceToCards(sentence: String, dictionary: Map<String, String>): List<Card> {

    // --- ИЗМЕНЕНИЕ ЗДЕСЬ ---
    val wordFinder = Regex(
        // 1. УБИРАЕМ \n\r из "слова" (Группа 1)
        // 2. ДОБАВЛЯЕМ \n\r в "хвост" (Группа 2)
        """[ \t\f\v]*((?:\p{Nd}+(?:[.:]\p{Nd}+)*)|(?:[\p{L}\p{M}\u05BE\u05F3\u05F4'’-]+))[ \t\f\v]*([.,:?!\n\r]+)?"""
    )
    // --- КОНЕЦ ИЗМЕНЕНИЯ ---

    val cards = wordFinder.findAll(sentence).map {
        val core = it.groupValues[1]         // слово/число (теперь "Вопрос\nОтвет")
        val tail = it.groupValues[2]         // опц. пунктуация
        val text = core + tail
        Card(
            text = text,
            translation = dictionary[text] ?: dictionary[core] ?: ""
        )
    }.toList()

    return cards
}