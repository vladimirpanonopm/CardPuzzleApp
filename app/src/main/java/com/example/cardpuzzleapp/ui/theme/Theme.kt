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

private val BookColorScheme = lightColorScheme(
    primary = InkBlack,
    onPrimary = CardWhite,

    background = PaperBg,          // Кремовый фон всего экрана
    onBackground = InkBlack,

    surface = CardWhite,           // Белый фон компонентов
    onSurface = InkBlack,

    surfaceVariant = PaperBg,      // Чтобы альтернативные фоны тоже были кремовыми
    onSurfaceVariant = InkBlack,

    // ВАЖНО: Красим контейнеры меню в цвет фона, чтобы не было серых полос
    surfaceContainer = PaperBg,

    outline = BorderGray,
    error = Color(0xFFB3261E),
    onError = Color.White
)

@Composable
fun CardPuzzleAppTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = BookColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val insetsController = WindowCompat.getInsetsController(window, view)

            // Статус бар и навигация — кремовые
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()

            // Иконки темные
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