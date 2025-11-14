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
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Extension
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cardpuzzleapp.ui.theme.StickyNoteText
import com.example.cardpuzzleapp.ui.theme.StickyNoteYellow
import kotlinx.coroutines.launch
import java.util.UUID

// --- TAG ИЗМЕНЕН ДЛЯ УДОБСТВА ФИЛЬТРАЦИИ ---
private const val TAG = "MATCHING_DEBUG"
// ------------------------------------------

/**
 * Экран для механики "Соедини пары" (Match-to-Line).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchingGameScreen(
    cardViewModel: CardViewModel,
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
    val haptics = LocalHapticFeedback.current

    LaunchedEffect(routeUid) {
        Log.d(TAG, ">>> MatchingGameScreen LaunchedEffect(uid=$routeUid). Вызов loadLevel...")
        cardViewModel.updateCurrentRoundIndex(routeRoundIndex)
        viewModel.loadLevelAndRound(routeLevelId, routeRoundIndex, routeUid)
    }

    LaunchedEffect(Unit) {
        viewModel.hapticEvents.collectLatest { event ->
            Log.d("VIBRATE_DEBUG", "MatchingGameScreen received event: $event")
            when (event) {
                HapticEvent.Success -> haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                HapticEvent.Failure -> haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        }
    }

    val shouldShowLoading = viewModel.isLoading || viewModel.loadedUid != routeUid

    Log.d(TAG, ">>> MatchingGameScreen RECOMPOSING (Route UID: $routeUid):")
    Log.d(TAG, "  > viewModel.isLoading = ${viewModel.isLoading}")
    Log.d(TAG, "  > viewModel.loadedUid = ${viewModel.loadedUid}")
    Log.d(TAG, "  > viewModel.isExamMode = ${viewModel.isExamMode}")
    Log.d(TAG, "  > ==>> shouldShowLoading = $shouldShowLoading (isLoading: ${viewModel.isLoading} || ${viewModel.loadedUid} != $routeUid)")

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
                    imageVector = Icons.AutoMirrored.Filled.MenuBook,
                    contentDescription = stringResource(R.string.journal_title),
                    onClick = onJournalClick
                )
                AppBottomBarIcon(
                    imageVector = Icons.Default.PlaylistAddCheck,
                    contentDescription = stringResource(R.string.round_track_title, viewModel.currentLevelId),
                    onClick = onTrackClick
                )

                // --- ИЗМЕНЕНИЕ: Логика кнопки зависит от isExamMode ---
                if (viewModel.isGameWon) {
                    // 1. Игра выиграна -> Показываем "Глаз"
                    AppBottomBarIcon(
                        imageVector = Icons.Default.Visibility,
                        contentDescription = stringResource(R.string.button_show_result),
                        onClick = { viewModel.showResultSheet() }
                    )
                } else if (!viewModel.isExamMode) {
                    // 2. Режим обучения -> Показываем "Начать экзамен" (Refresh)
                    AppBottomBarIcon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.button_start_exam), // (Нужно добавить R.string.button_start_exam)
                        onClick = { viewModel.startExamMode() }
                    )
                } else {
                    // 3. Режим экзамена (не выигран) -> Показываем "Пропустить"
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
                // --- КОНЕЦ ИЗМЕНЕНИЯ ---
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
                Log.d(TAG, "  > UI: Рисуем КОЛОНКИ (shouldShowLoading=false)")

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
                        roundIndex = viewModel.currentRoundIndex,
                        errorCount = viewModel.errorCount,
                        errorItemId = viewModel.errorItemId,
                        isExamMode = viewModel.isExamMode // <-- Передаем режим
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    MatchColumn(
                        items = viewModel.translationCards,
                        onItemClick = viewModel::onMatchItemClicked,
                        modifier = Modifier.weight(1f),
                        isHebrewColumn = false,
                        roundIndex = viewModel.currentRoundIndex,
                        errorCount = viewModel.errorCount,
                        errorItemId = viewModel.errorItemId,
                        isExamMode = viewModel.isExamMode // <-- Передаем режим
                    )
                }
            } else {
                Log.d(TAG, "  > UI: Рисуем СПИННЕР (shouldShowLoading=true)")
                CircularProgressIndicator(
                    modifier = Modifier.size(64.dp)
                )
            }

            val shouldShowSheet = viewModel.showResultSheet && snapshot != null
            Log.d(TAG, "  > UI: Проверка шторки (shouldShowSheet = $shouldShowSheet)")

            if (shouldShowSheet) {
                Log.d(TAG, "  > UI: Рисуем BottomSheet")
                val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

                ModalBottomSheet(
                    onDismissRequest = {
                        Log.d(TAG, "  > UI: Шторка закрыта (onDismissRequest). Вызов hideResultSheet().")
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
                            Log.d(TAG, "  > UI: Нажата кнопка ПОВТОРИТЬ (onRepeatClick). Вызов restartCurrentRound().")
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
    roundIndex: Int,
    errorCount: Int,
    errorItemId: UUID?,
    isExamMode: Boolean // <-- Принимаем режим
) {
    val columnName = if (isHebrewColumn) "Hebrew" else "Translation"
    Log.d(TAG, "MatchColumn Composing: $columnName. Round=$roundIndex, Items.size=${items.size}")

    LazyColumn(
        modifier = modifier.fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(items, key = { it.id }) { item ->
            // --- ИЗМЕНЕНИЕ: Shakeable только в режиме экзамена ---
            if (isExamMode) {
                Shakeable(
                    trigger = errorCount,
                    errorCardId = errorItemId,
                    currentCardId = item.id
                ) { shakeModifier ->
                    MatchLineItem(
                        item = item,
                        onItemClick = onItemClick,
                        isHebrew = isHebrewColumn,
                        modifier = shakeModifier
                    )
                }
            } else {
                // В режиме обучения "тряска" не нужна
                MatchLineItem(
                    item = item,
                    onItemClick = onItemClick,
                    isHebrew = isHebrewColumn,
                    modifier = Modifier
                )
            }
            // --- КОНЕЦ ИЗМЕНЕНИЯ ---
        }
    }
}

@Composable
private fun MatchLineItem(
    item: MatchItem,
    onItemClick: (MatchItem) -> Unit,
    isHebrew: Boolean,
    modifier: Modifier = Modifier
) {
    val cornerRadius = 8.dp

    val cardColor = when {
        item.isMatched -> StickyNoteYellow
        item.isSelected -> StickyNoteYellow
        else -> MaterialTheme.colorScheme.surface
    }

    val borderColor = when {
        item.isMatched -> StickyNoteText
        item.isSelected -> StickyNoteText
        else -> MaterialTheme.colorScheme.outline
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                // --- ИЗМЕНЕНИЕ: Клик всегда разрешен (VM решает, что делать) ---
                enabled = true,
                // --- КОНЕЦ ---
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