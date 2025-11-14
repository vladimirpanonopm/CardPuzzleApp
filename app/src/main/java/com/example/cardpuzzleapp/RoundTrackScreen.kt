package com.example.cardpuzzleapp

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

@Composable
fun RoundTrackScreen(
    levelId: Int,
    viewModel: CardViewModel,
    onHomeClick: () -> Unit,
    onResetClick: () -> Unit,
    onJournalClick: () -> Unit,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current

    val levelData = viewModel.currentLevelSentences

    if (levelData.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Ошибка: не удалось загрузить данные уровня $levelId")
        }
        return
    }

    val progressManager = remember { viewModel.progressManager }

    // --- ИЗМЕНЕНИЕ: Считаем ОБА списка ---
    val journalRounds = progressManager.getCompletedRounds(levelId).size
    val archivedRounds = progressManager.getArchivedRounds(levelId).size
    val completedRounds = journalRounds + archivedRounds
    // --- КОНЕЦ ИЗМЕНЕНИЯ ---

    val sourceBitmap = remember {
        BitmapFactory.decodeResource(context.resources, R.drawable.tesy).asImageBitmap()
    }

    val shuffledIndices = remember(levelId) {
        (0 until 20).toList().shuffled()
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.round_track_title, levelId),
                onBackClick = onBackClick
            )
        },
        bottomBar = {
            AppBottomBar {
                AppBottomBarIcon(
                    imageVector = Icons.Default.Home,
                    contentDescription = stringResource(R.string.button_home),
                    onClick = onHomeClick,
                )
                AppBottomBarIcon(
                    imageVector = Icons.Default.MenuBook,
                    contentDescription = stringResource(R.string.cd_go_to_journal),
                    onClick = onJournalClick
                )
                AppBottomBarIcon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = stringResource(R.string.button_reset_progress),
                    onClick = onResetClick,
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val totalRoundsInLevel = levelData.size

            PuzzleGrid(
                sourceBitmap = sourceBitmap,
                totalPieces = 20,
                totalRounds = totalRoundsInLevel,
                completedRounds = completedRounds,
                columns = 5,
                shuffledIndices = shuffledIndices,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
            )

            Spacer(modifier = Modifier.height(24.dp))
            Text(
                stringResource(R.string.round_track_progress, completedRounds, totalRoundsInLevel),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun PuzzleGrid(
    sourceBitmap: ImageBitmap,
    totalPieces: Int,
    totalRounds: Int,
    completedRounds: Int,
    columns: Int,
    shuffledIndices: List<Int>,
    modifier: Modifier = Modifier
) {
    val roundsPerPiece = remember(totalRounds, totalPieces) {
        kotlin.math.ceil(totalRounds.toFloat() / totalPieces).toInt().coerceAtLeast(1)
    }

    val totalProgress = if (totalRounds > 0) {
        (completedRounds.toFloat() / totalRounds.toFloat()) * totalPieces
    } else {
        0f
    }

    val rows = (totalPieces + columns - 1) / columns

    Layout(
        modifier = modifier,
        content = {
            repeat(totalPieces) { gridIndex ->
                val revealOrder = shuffledIndices.indexOf(gridIndex)
                val brightness = (totalProgress - revealOrder).coerceIn(0.0f, 1.0f)

                PuzzlePiece(
                    sourceBitmap = sourceBitmap,
                    gridX = gridIndex % columns,
                    gridY = gridIndex / columns,
                    gridWidth = columns,
                    gridHeight = rows,
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