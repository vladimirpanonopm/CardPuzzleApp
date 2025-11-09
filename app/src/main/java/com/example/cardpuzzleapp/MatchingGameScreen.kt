package com.example.cardpuzzleapp

import android.util.Log
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.cardpuzzleapp.ui.theme.StickyNoteText
import com.example.cardpuzzleapp.ui.theme.StickyNoteYellow
import kotlinx.coroutines.flow.collectLatest

/**
 * Главный экран для игры "Найди Пару" (Matching Pairs).
 * Это наша "Песочница" для отладки.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchingGameScreen(
    viewModel: MatchingViewModel, // Мы получим его из AppNavigation
    onBackClick: () -> Unit      // (В режиме "Песочницы" эта кнопка будет перезапускать игру)
) {
    val haptics = LocalHapticFeedback.current

    LaunchedEffect(Unit) {
        viewModel.hapticEvents.collectLatest { event ->
            Log.d(AppDebug.TAG, "MatchingScreen received event: $event")
            when (event) {
                HapticEvent.Success -> haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                HapticEvent.Failure -> haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = viewModel.currentTaskTitle,
                onBackClick = onBackClick
            )
        },
        bottomBar = {
            AppBottomBar {
                AppBottomBarIcon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = stringResource(R.string.button_reset),
                    onClick = { viewModel.loadRound() } // Кнопка "Обновить" перезапускает раунд
                )
            }
        }
    ) { paddingValues ->

        if (viewModel.isGameWon) {
            // Экран победы
            Box(Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("ПОБЕДА!", style = MaterialTheme.typography.headlineLarge)
                    Button(onClick = { viewModel.loadRound() }) {
                        Text("Играть снова")
                    }
                }
            }
        } else {
            // Игровое поле
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 120.dp), // Авто-подбор кол-ва колонок
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(viewModel.cards, key = { it.id }) { card ->
                    MatchCardItem(
                        card = card,
                        onClick = { viewModel.onCardClicked(card) }
                    )
                }
            }
        }
    }
}

/**
 * Composable-функция для ОДНОЙ карточки на поле.
 * Умеет "переворачиваться".
 */
@Composable
private fun MatchCardItem(
    card: MatchCard,
    onClick: () -> Unit
) {
    val rotation by animateFloatAsState(
        targetValue = if (card.isFlipped) 180f else 0f,
        animationSpec = tween(600), label = "CardFlipAnimation"
    )

    val cardColor = if (card.isMatched) {
        // "Совпавшая" карта - бледная
        StickyNoteYellow.copy(alpha = 0.6f)
    } else {
        // Активная карта - яркая
        StickyNoteYellow
    }

    Card(
        modifier = Modifier
            .aspectRatio(1f) // Делает карту квадратной
            .graphicsLayer {
                rotationY = rotation
                cameraDistance = 8 * density
            }
            .clickable(enabled = !card.isMatched && !card.isFlipped, onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        border = BorderStroke(1.dp, StickyNoteText.copy(alpha = 0.3f))
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (rotation < 90f) {
                // --- Рубашка (Back) ---
                Text("?", fontSize = 48.sp, color = StickyNoteText)
            } else {
                // --- Лицо (Front) ---
                Text(
                    text = card.text,
                    fontSize = 24.sp,
                    textAlign = TextAlign.Center,
                    color = StickyNoteText,
                    modifier = Modifier
                        .padding(8.dp)
                        .graphicsLayer { rotationY = 180f } // (Переворачиваем текст обратно)
                )
            }
        }
    }
}