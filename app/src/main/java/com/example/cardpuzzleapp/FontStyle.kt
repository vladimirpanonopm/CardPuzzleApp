package com.example.cardpuzzleapp

import androidx.annotation.StringRes
import androidx.compose.ui.text.font.FontFamily
import com.example.cardpuzzleapp.ui.theme.GveretLevinFontFamily
import com.example.cardpuzzleapp.ui.theme.NotoSansHebrewFontFamily

/**
 * Этот enum представляет доступные стили шрифтов в приложении.
 * Он связывает понятное имя стиля с конкретным файлом шрифта.
 */
enum class FontStyle(@StringRes val displayNameRes: Int, val fontFamily: FontFamily) {
    REGULAR(R.string.font_style_regular, NotoSansHebrewFontFamily), // Обычный, печатный шрифт
    CURSIVE(R.string.font_style_cursive, GveretLevinFontFamily)    // Прописной, рукописный шрифт
}