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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cardpuzzleapp.ui.theme.StickyNoteText
import com.example.cardpuzzleapp.ui.theme.StickyNoteYellow
import kotlinx.coroutines.launch

// --- TAG ИЗМЕНЕН ДЛЯ УДОБСТВА ФИЛЬТРАЦИИ ---
private const val TAG = "MATCHING_DEBUG"
// ------------------------------------------

/**
 * Экран для механики "Соедини пары" (Match-to-Line).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchingGameScreen(
    // --- ИЗМЕНЕНИЕ V12: Добавлена cardViewModel ---
    cardViewModel: CardViewModel,
    // ------------------------------------------
    viewModel: MatchingViewModel,
    routeLevelId: Int,
    routeRoundIndex: Int,
    routeUid: Long,
    onBackClick: () -> Unit,
    onJournalClick: () -> Unit,
    onTrackClick: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val snapshot = viewModel.resultSnapshot

    LaunchedEffect(routeUid) {
        // --- ЛОГ ---
        Log.d(TAG, ">>> MatchingGameScreen LaunchedEffect(uid=$routeUid). Вызов loadLevel...")
        // ---------

        // --- ИЗМЕНЕНИЕ V12: Сообщаем CardViewModel, что мы - активный раунд ---
        cardViewModel.updateCurrentRoundIndex(routeRoundIndex)
        // ----------------------------------------------------------------

        viewModel.loadLevelAndRound(routeLevelId, routeRoundIndex, routeUid)
    }

    val shouldShowLoading = viewModel.isLoading || viewModel.loadedUid != routeUid

    // --- ЛОГ ---
    Log.d(TAG, ">>> MatchingGameScreen RECOMPOSING (Route UID: $routeUid):")
    Log.d(TAG, "  > viewModel.isLoading = ${viewModel.isLoading}")
    Log.d(TAG, "  > viewModel.loadedUid = ${viewModel.loadedUid}")
    Log.d(TAG, "  > ==>> shouldShowLoading = $shouldShowLoading (isLoading: ${viewModel.isLoading} || ${viewModel.loadedUid} != $routeUid)")
    // ---------

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

                // --- ИЗМЕНЕНИЕ ЗДЕСЬ (БАГФИКС) ---
                if (viewModel.isGameWon) {
                    // Заменяем Spacer на кнопку, которая повторно показывает шторку
                    AppBottomBarIcon(
                        imageVector = Icons.Default.Visibility,
                        contentDescription = stringResource(R.string.button_show_result),
                        onClick = { viewModel.showResultSheet() }
                    )
                } else {
                    // --- КОНЕЦ ИЗМЕНЕНИЯ ---
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

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {

            if (!shouldShowLoading) {
                // --- ЛОГ ---
                Log.d(TAG, "  > UI: Рисуем КОЛОНКИ (shouldShowLoading=false)")
                // ---------

                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    MatchColumn(
                        items = viewModel.hebrewCards,
                        onItemClick = viewModel::onMatchItemClicked,
                        modifier = Modifier.weight(1f),
                        isHebrewColumn = true,
                        roundIndex = viewModel.currentRoundIndex
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    MatchColumn(
                        items = viewModel.translationCards,
                        onItemClick = viewModel::onMatchItemClicked,
                        modifier = Modifier.weight(1f),
                        isHebrewColumn = false,
                        roundIndex = viewModel.currentRoundIndex
                    )
                }
            } else {
                // --- ЛОГ ---
                Log.d(TAG, "  > UI: Рисуем СПИННЕР (shouldShowLoading=true)")
                // ---------
                CircularProgressIndicator(
                    modifier = Modifier.size(64.dp)
                )
            }

            val shouldShowSheet = viewModel.showResultSheet && snapshot != null
            // --- ЛОГ ---
            Log.d(TAG, "  > UI: Проверка шторки (shouldShowSheet = $shouldShowSheet)")
            // ---------

            if (shouldShowSheet) {
                // --- ЛОГ ---
                Log.d(TAG, "  > UI: Рисуем BottomSheet")
                // ---------
                val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

                ModalBottomSheet(
                    onDismissRequest = {
                        // --- ЛОГ ---
                        Log.d(TAG, "  > UI: Шторка закрыта (onDismissRequest). Вызов hideResultSheet().")
                        // ---------
                        viewModel.hideResultSheet()
                    },
                    sheetState = sheetState,
                    dragHandle = null,
                    scrimColor = Color.Transparent
                ) {
                    ResultSheetContent(
                        snapshot = snapshot!!,
                        onContinueClick = {
                            coroutineScope.launch { sheetState.hide() }.invokeOnCompletion {
                                viewModel.hideResultSheet()
                                viewModel.proceedToNextRound()
                            }
                        },
                        onRepeatClick = {
                            // --- ЛОГ ---
                            Log.d(TAG, "  > UI: Нажата кнопка ПОВТОРИТЬ (onRepeatClick). Вызов restartCurrentRound().")
                            // ---------
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
        }
    }
}

@Composable
private fun MatchColumn(
    items: List<MatchItem>,
    onItemClick: (MatchItem) -> Unit,
    modifier: Modifier = Modifier,
    isHebrewColumn: Boolean,
    roundIndex: Int
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