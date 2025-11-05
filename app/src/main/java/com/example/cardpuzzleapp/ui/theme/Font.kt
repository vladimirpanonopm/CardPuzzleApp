package com.example.cardpuzzleapp.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.example.cardpuzzleapp.R

// Прописной шрифт остается без изменений (он статичный)
val GveretLevinFontFamily = FontFamily(
    Font(R.font.gveret_levin_regular, FontWeight.Normal)
)

// highlight-start
// Строчный шрифт теперь — это один вариативный файл
val NotoSansHebrewFontFamily = FontFamily(
    Font(
        // 1. Указываем на наш новый вариативный файл
        resId = R.font.noto_sans_hebrew_variable,
        // 2. Указываем вес по умолчанию. Система сама поймет, что шрифт вариативный,
        // и позволит нам управлять толщиной через FontVariation.Settings.
        weight = FontWeight.Normal
    )
)
// highlight-end
