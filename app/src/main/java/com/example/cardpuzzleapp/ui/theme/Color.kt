package com.example.cardpuzzleapp.ui.theme

import androidx.compose.ui.graphics.Color

// === ПАЛИТРА "ДОРОГОЙ УЧЕБНИК" ===

// 1. ФОН: "Слоновая Кость" (Теплый кремовый)
val PaperBg = Color(0xFFF7F5F0)

// 2. ТЕКСТ: "Глубокий Графит"
val InkBlack = Color(0xFF202124)

// 3. КАРТОЧКИ: "Белоснежный"
val CardWhite = Color(0xFFFFFFFF)

// 4. ОБВОДКИ
val BorderGray = Color(0xFFE0E0E0)

// 5. СТАТУСЫ
val StatusGreen = Color(0xFF4CAF50)
val StatusOrange = Color(0xFFFF9800)
val StatusYellow = Color(0xFFFFEB3B)
val StatusGray = Color.LightGray

// --- MAPPING для совместимости ---
val StickyNoteText = InkBlack
val StickyNoteYellow = CardWhite // Теперь это БЕЛЫЙ
val LightGrayBorder = BorderGray
val LinenBg = PaperBg
val DarkBlueText = InkBlack
val AppWhite = CardWhite
val AppBlack = InkBlack
val LineColor = InkBlack
val LightBlueBg = PaperBg
val BlueAccent = StatusOrange