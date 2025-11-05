package com.example.cardpuzzleapp

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import kotlin.math.roundToInt

@Composable
fun PuzzleSandboxScreen() {
    val context = LocalContext.current

    val totalPuzzlePieces = 20
    val totalRoundsInLevel = 80
    val columns = 5

    val roundsPerPiece = remember(totalRoundsInLevel, totalPuzzlePieces) {
        ceil(totalRoundsInLevel.toFloat() / totalPuzzlePieces).toInt().coerceAtLeast(1)
    }

    val sourceBitmap = remember {
        BitmapFactory.decodeResource(context.resources, R.drawable.tesy).asImageBitmap()
    }

    val shuffledIndices = remember {
        (0 until totalPuzzlePieces).toList().shuffled()
    }

    var completedRounds by remember { mutableFloatStateOf(0f) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Песочница для пазла", style = MaterialTheme.typography.headlineSmall)
        Text("1 пазл = $roundsPerPiece раунда", style = MaterialTheme.typography.bodySmall)
        Spacer(modifier = Modifier.height(16.dp))

        // ИЗМЕНЕНИЕ: Используем Layout для построения сетки
        PuzzleGrid(
            sourceBitmap = sourceBitmap,
            totalPieces = totalPuzzlePieces,
            totalRounds = totalRoundsInLevel,
            completedRounds = completedRounds.roundToInt(),
            columns = columns,
            shuffledIndices = shuffledIndices,
            roundsPerPiece = roundsPerPiece,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
        )

        Spacer(modifier = Modifier.height(24.dp))
        Text("Пройдено раундов: ${completedRounds.roundToInt()}", fontWeight = FontWeight.Bold)
        Slider(
            value = completedRounds,
            onValueChange = { completedRounds = it },
            valueRange = 0f..totalRoundsInLevel.toFloat(),
            steps = totalRoundsInLevel - 1
        )
    }
}

// ИЗМЕНЕНИЕ: Новый Composable для сетки, использующий Layout
@Composable
private fun PuzzleGrid(
    sourceBitmap: ImageBitmap,
    totalPieces: Int,
    totalRounds: Int,
    completedRounds: Int,
    columns: Int,
    shuffledIndices: List<Int>,
    roundsPerPiece: Int,
    modifier: Modifier = Modifier
) {
    Layout(
        modifier = modifier,
        content = {
            // Создаем все 20 кусочков пазла
            repeat(totalPieces) { index ->
                val pieceIndex = shuffledIndices[index]
                val roundsCompletedForThisPiece = (completedRounds - (pieceIndex * roundsPerPiece)).coerceIn(0, roundsPerPiece)
                val brightness = roundsCompletedForThisPiece.toFloat() / roundsPerPiece

                PuzzlePiece(
                    sourceBitmap = sourceBitmap,
                    gridX = index % columns,
                    gridY = index / columns,
                    gridWidth = columns,
                    gridHeight = (totalPieces + columns - 1) / columns, // Рассчитываем высоту сетки
                    brightness = brightness
                )
            }
        }
    ) { measurables, constraints ->
        val itemSize = constraints.maxWidth / columns
        val itemConstraints = Constraints.fixed(itemSize, itemSize)

        val placeables = measurables.map { it.measure(itemConstraints) }

        layout(constraints.maxWidth, constraints.maxWidth) {
            placeables.forEachIndexed { index, placeable ->
                val x = (index % columns) * itemSize
                val y = (index / columns) * itemSize
                placeable.placeRelative(x, y)
            }
        }
    }
}

@Composable
private fun PuzzlePiece(
    sourceBitmap: ImageBitmap,
    gridX: Int,
    gridY: Int,
    gridWidth: Int,
    gridHeight: Int,
    brightness: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val pieceWidth = sourceBitmap.width / gridWidth
            val pieceHeight = sourceBitmap.height / gridHeight

            val srcOffset = IntOffset(gridX * pieceWidth, gridY * pieceHeight)
            val srcSize = IntSize(pieceWidth, pieceHeight)

            drawImage(
                image = sourceBitmap,
                srcOffset = srcOffset,
                srcSize = srcSize,
                dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                alpha = brightness
            )
        }
    }
}