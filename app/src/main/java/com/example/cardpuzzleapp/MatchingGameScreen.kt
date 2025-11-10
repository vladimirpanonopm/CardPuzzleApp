package com.example.cardpuzzleapp

import android.util.Log
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAddCheck
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cardpuzzleapp.ui.theme.StickyNoteText
import com.example.cardpuzzleapp.ui.theme.StickyNoteYellow
import kotlinx.coroutines.launch

// --- ИЗМЕНЕНИЕ 1: Новый тег для логов ---
private const val TAG = "MATCH_SHEET_DEBUG"
// --------------------------------------

/**
 * Экран для механики "Соедини пары" (Match-to-Line).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchingGameScreen(
    viewModel: MatchingViewModel,
    onBackClick: () -> Unit,
    onJournalClick: () -> Unit,
    onTrackClick: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val snapshot = viewModel.resultSnapshot

    // --- ИЗМЕНЕНИЕ 2: Логируем состояния ---
    Log.d(TAG, "MatchingGameScreen RECOMPOSING:")
    Log.d(TAG, "  > isGameWon = ${viewModel.isGameWon}")
    Log.d(TAG, "  > showResultSheet = ${viewModel.showResultSheet}")
    Log.d(TAG, "  > snapshot is null = ${snapshot == null}")
    // -------------------------------------

    // Слушаем событие победы, чтобы завершить раунд и вернуться на домашний экран
    LaunchedEffect(Unit) {
        viewModel.completionEvents.collect { event ->
            if (event == "WIN") {
                onBackClick()
            }
            // (Событие "TRACK" обрабатывается в MainActivity)
            // (Событие "SKIP" обрабатывается в MainActivity)
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(id = viewModel.currentTaskTitleResId),
                onBackClick = onBackClick
            )
        },
        bottomBar = {
            AppBottomBar {
                AppBottomBarIcon(
                    imageVector = Icons.Default.MenuBook,
                    contentDescription = stringResource(R.string.journal_title),
                    onClick = onJournalClick
                )
                AppBottomBarIcon(
                    imageVector = Icons.Default.PlaylistAddCheck,
                    contentDescription = stringResource(R.string.round_track_title, viewModel.currentLevelId),
                    onClick = onTrackClick
                )

                if (viewModel.isGameWon) {
                    // ПОСЛЕ ПОБЕДЫ: Кнопка "Показать"
                    AppBottomBarIcon(
                        imageVector = Icons.Default.Visibility,
                        contentDescription = stringResource(R.string.button_show_translation),
                        onClick = {
                            // --- ИЗМЕНЕНИЕ 3: Логируем нажатие ---
                            Log.d(TAG, "BottomBar: 'Visibility' icon CLICKED")
                            viewModel.showResultSheet()
                            // --------------------------------
                        }
                    )
                } else {
                    // ВО ВРЕМЯ ИГРЫ: Кнопка "Пропустить"
                    IconButton(
                        onClick = { viewModel.skipToNextAvailableRound() },
                        enabled = !viewModel.isLastRoundAvailable
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = stringResource(R.string.button_skip),
                            modifier = Modifier.size(36.dp),
                            tint = LocalContentColor.current.copy(alpha = if (viewModel.isLastRoundAvailable) 0.38f else 1.0f)
                        )
                    }
                }
            }
        }
    ) { paddingValues ->

        Box(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Левый столбец (Иврит)
                MatchColumn(
                    items = viewModel.hebrewCards,
                    onItemClick = viewModel::onMatchItemClicked,
                    modifier = Modifier.weight(1f),
                    isHebrewColumn = true
                )

                Spacer(modifier = Modifier.width(16.dp))

                // Правый столбец (Перевод)
                MatchColumn(
                    items = viewModel.translationCards,
                    onItemClick = viewModel::onMatchItemClicked,
                    modifier = Modifier.weight(1f),
                    isHebrewColumn = false
                )
            }

            // --- ИЗМЕНЕНИЕ 4: Логируем условие показа шторки ---
            val shouldShowSheet = viewModel.showResultSheet && snapshot != null
            Log.d(TAG, "ModalBottomSheet Check: shouldShowSheet = $shouldShowSheet")

            if (shouldShowSheet) {
                Log.d(TAG, "ModalBottomSheet RECOMPOSING (SHOULD BE VISIBLE)")
                val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

                ModalBottomSheet(
                    onDismissRequest = {
                        Log.d(TAG, "ModalBottomSheet: onDismissRequest CALLED")
                        viewModel.hideResultSheet()
                    },
                    sheetState = sheetState,
                    dragHandle = null,
                    scrimColor = Color.Transparent
                ) {
                    ResultSheetContent(
                        snapshot = snapshot!!, // (Мы уже проверили на null)
                        onContinueClick = {
                            coroutineScope.launch { sheetState.hide() }.invokeOnCompletion {
                                viewModel.hideResultSheet()
                                viewModel.proceedToNextRound()
                            }
                        },
                        onRepeatClick = {
                            coroutineScope.launch { sheetState.hide() }.invokeOnCompletion {
                                viewModel.hideResultSheet()
                                viewModel.restartCurrentRound()
                            }
                        },
                        onTrackClick = {
                            coroutineScope.launch { sheetState.hide() }.invokeOnCompletion {
                                viewModel.hideResultSheet()
                                viewModel.onTrackClick()
                            }
                        }
                    )
                }
            }
            // ----------------------------------------------------
        }
    }
}

@Composable
private fun MatchColumn(
    items: List<MatchItem>,
    onItemClick: (MatchItem) -> Unit,
    modifier: Modifier = Modifier,
    isHebrewColumn: Boolean
) {
    LazyColumn(
        modifier = modifier.fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(items, key = { it.id }) { item ->
            MatchLineItem(
                item = item,
                onItemClick = onItemClick,
                isHebrew = isHebrewColumn
            )
        }
    }
}

@Composable
private fun MatchLineItem(
    item: MatchItem,
    onItemClick: (MatchItem) -> Unit,
    isHebrew: Boolean
) {
    val cornerRadius = 8.dp
    val cardColor = when {
        item.isMatched -> MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.6f)
        item.isSelected -> StickyNoteYellow
        else -> MaterialTheme.colorScheme.surface
    }

    val borderColor = when {
        item.isMatched -> Color.Transparent
        item.isSelected -> StickyNoteText
        else -> MaterialTheme.colorScheme.outline
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                enabled = !item.isMatched,
                onClick = { onItemClick(item) },
                interactionSource = remember { MutableInteractionSource() },
                indication = rememberRipple()
            ),
        shape = RoundedCornerShape(cornerRadius),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        border = BorderStroke(2.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Text(
            text = item.text,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 16.dp),
            textAlign = if (isHebrew) TextAlign.Right else TextAlign.Left,
            fontSize = 18.sp,
            color = StickyNoteText
        )
    }
}