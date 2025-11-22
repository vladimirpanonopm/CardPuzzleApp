package com.example.cardpuzzleapp

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.min

@Composable
fun ResultSheetContent(
    snapshot: RoundResultSnapshot,
    onContinueClick: () -> Unit,
    onRepeatClick: () -> Unit,
    onTrackClick: () -> Unit
) {
    // Расчет звезд
    val starCount = when {
        snapshot.errorCount == 0 -> 3      // Идеально (Золото)
        snapshot.errorCount <= 3 -> 2      // Хорошо (Зеленый)
        else -> 1                          // Пройдено (Салатовый)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(vertical = 24.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        // --- 1. ЗВЕЗДЫ (ЗАЛИТЫЕ) ---
        MagenDavidRatingBar(
            stars = starCount,
            modifier = Modifier.padding(bottom = 24.dp)
        )
        // ---------------------------

        // --- 2. ТЕКСТ ПЕРЕВОДА ---
        snapshot.translationText?.let { translation ->
            if (translation.isNotBlank()) {
                Text(
                    text = translation,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Start,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp)
                )
            }
        }

        // --- 3. КНОПКИ ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onTrackClick) {
                Icon(
                    imageVector = Icons.Default.Extension,
                    contentDescription = stringResource(R.string.round_track_title, snapshot.levelId),
                    modifier = Modifier.size(32.dp)
                )
            }

            IconButton(onClick = onRepeatClick) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = stringResource(R.string.button_repeat_round),
                    modifier = Modifier.size(32.dp)
                )
            }

            Button(
                onClick = onContinueClick,
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(text = stringResource(R.string.button_continue), fontSize = 20.sp)
            }
        }
    }
}

@Composable
fun MagenDavidRatingBar(
    stars: Int,
    modifier: Modifier = Modifier
) {
    // Цвета чуть подтюнил для солидности
    val goldColor = Color(0xFFFFD700) // Классическое Золото
    val emptyColor = Color(0xFFE0E0E0) // Светло-серый для неактивных

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 1..3) {
            val isActive = i <= stars
            val color = if (isActive) goldColor else emptyColor

            // Используем наш кастомный компонент для рисования звезды
            FilledMagenDavidIcon(
                color = color,
                modifier = Modifier
                    .size(48.dp) // Размер как раньше
                    .offset(y = if(i==2) (-8).dp else 0.dp) // Пьедестал сохраняем
            )
        }
    }
}

/**
 * Рисует залитую шестиконечную звезду (два треугольника).
 */
@Composable
fun FilledMagenDavidIcon(
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.aspectRatio(1f)) {
        val cx = size.width / 2
        val cy = size.height / 2
        val radius = min(size.width, size.height) / 2

        // Геометрия равностороннего треугольника
        // Высота от центра до основания (r * sin(30) = r * 0.5)
        val hBase = radius * 0.5f
        // Половина ширины основания (r * cos(30) = r * 0.866)
        val wHalf = radius * 0.866025f

        // Треугольник вверх ▲
        drawPath(
            path = Path().apply {
                moveTo(cx, cy - radius) // Верхняя вершина
                lineTo(cx + wHalf, cy + hBase) // Правый нижний угол
                lineTo(cx - wHalf, cy + hBase) // Левый нижний угол
                close()
            },
            color = color
        )

        // Треугольник вниз ▼
        drawPath(
            path = Path().apply {
                moveTo(cx, cy + radius) // Нижняя вершина
                lineTo(cx + wHalf, cy - hBase) // Правый верхний угол
                lineTo(cx - wHalf, cy - hBase) // Левый верхний угол
                close()
            },
            color = color
        )
    }
}