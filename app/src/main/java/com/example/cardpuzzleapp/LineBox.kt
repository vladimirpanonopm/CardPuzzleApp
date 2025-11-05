package com.example.cardpuzzleapp

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Контейнер, который рисует в своем фоне горизонтальные линии,
 * имитируя разлинованную бумагу.
 */
@Composable
fun LinedBox(
    modifier: Modifier = Modifier,
    lineColor: Color,
    lineHeight: Dp,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier.drawBehind {
            val strokeWidth = 1.dp.toPx()
            val lineHeightPx = lineHeight.toPx()

            // Рассчитываем, сколько полных линий поместится в высоте контейнера
            val lineCount = (size.height / lineHeightPx).toInt()

            // Рисуем линии сверху вниз. Каждая линия будет внизу своей "строки".
            for (i in 1..lineCount) {
                // Y-координата для каждой линии
                val y = i * lineHeightPx - (strokeWidth / 2) // Центрируем линию на границе
                if (y <= size.height) {
                    drawLine(
                        color = lineColor,
                        start = Offset(x = 0f, y = y),
                        end = Offset(x = size.width, y = y),
                        strokeWidth = strokeWidth
                    )
                }
            }
        }
    ) {
        content()
    }
}
