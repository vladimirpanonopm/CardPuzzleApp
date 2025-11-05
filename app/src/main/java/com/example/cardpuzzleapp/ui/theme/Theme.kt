package com.example.cardpuzzleapp.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorPalette = lightColorScheme(
    primary = DarkBlueText,
    onPrimary = AppWhite,
    background = StickyNoteYellow,
    onBackground = DarkBlueText,
    surface = AppWhite,
    onSurface = DarkBlueText,
    surfaceVariant = LinenBg,
    onSurfaceVariant = DarkBlueText,
    secondaryContainer = AppWhite,
    onSecondaryContainer = DarkBlueText,
    outline = LightGrayBorder,
    error = Color(0xFFB00020),
    errorContainer = Color(0xFFFCDADC),
    onErrorContainer = Color(0xFF410002)
    // surfaceContainer будет взят из стандартной палитры Material3 - это и есть наш "серый"
)

@Composable
fun CardPuzzleAppTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = LightColorPalette
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val insetsController = WindowCompat.getInsetsController(window, view)

            // --- ИЗМЕНЕНИЯ ЗДЕСЬ ---
            // 1. Устанавливаем для статус-бара серый цвет меню
            window.statusBarColor = colorScheme.surfaceContainer.toArgb()
            // 2. Устанавливаем для панели навигации тот же серый цвет
            window.navigationBarColor = colorScheme.surfaceContainer.toArgb()

            // 3. Делаем иконки в обоих барах темными для лучшей читаемости
            insetsController.isAppearanceLightStatusBars = true
            insetsController.isAppearanceLightNavigationBars = true
        }
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}