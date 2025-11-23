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
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.PlaylistAddCheck
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import kotlin.math.roundToInt
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cardpuzzleapp.ui.theme.StickyNoteText
import com.example.cardpuzzleapp.ui.theme.StickyNoteYellow
import kotlinx.coroutines.launch
import java.util.UUID

private const val TAG = "MATCHING_DEBUG"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalTextApi::class)
@Composable
fun MatchingGameScreen(
    cardViewModel: CardViewModel,
    viewModel: MatchingViewModel,
    routeLevelId: Int,
    routeRoundIndex: Int,
    routeUid: Long,
    onBackClick: () -> Unit,
    onJournalClick: () -> Unit,
    onTrackClick: () -> Unit,
    onDictionaryClick: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val snapshot = viewModel.resultSnapshot
    val haptics = LocalHapticFeedback.current

    LaunchedEffect(routeUid) {
        // cardViewModel.updateCurrentRoundIndex(routeRoundIndex) // <-- УДАЛЕНО (MatchingViewModel управляет своим состоянием сам)
        viewModel.loadLevelAndRound(routeLevelId, routeRoundIndex, routeUid)
    }

    LaunchedEffect(Unit) {
        viewModel.hapticEvents.collectLatest { event ->
            when (event) {
                HapticEvent.Success -> haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                HapticEvent.Failure -> haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        }
    }

    val shouldShowLoading = viewModel.isLoading || viewModel.loadedUid != routeUid

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
                    imageVector = Icons.Default.Book,
                    contentDescription = stringResource(R.string.dictionary_title),
                    onClick = onDictionaryClick
                )
                AppBottomBarIcon(
                    imageVector = Icons.AutoMirrored.Filled.PlaylistAddCheck,
                    contentDescription = stringResource(R.string.round_track_title, viewModel.currentLevelId),
                    onClick = onTrackClick
                )

                if (viewModel.isGameWon) {
                    AppBottomBarIcon(
                        imageVector = Icons.Default.Visibility,
                        contentDescription = stringResource(R.string.button_show_result),
                        onClick = { viewModel.showResultSheet() }
                    )
                } else if (!viewModel.isExamMode) {
                    AppBottomBarIcon(
                        imageVector = Icons.Default.QuestionMark,
                        contentDescription = stringResource(R.string.button_start_exam),
                        onClick = { viewModel.startExamMode() }
                    )
                } else {
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
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    // --- ЛЕВАЯ КОЛОНКА: РУССКИЙ ---
                    Column(modifier = Modifier.weight(1f)) {
                        HintText(text = "")
                        MatchColumn(
                            items = viewModel.translationCards,
                            onItemClick = viewModel::onMatchItemClicked,
                            modifier = Modifier.fillMaxWidth(),
                            isHebrewColumn = false,
                            errorCount = viewModel.errorCount,
                            errorItemId = viewModel.errorItemId,
                            isExamMode = viewModel.isExamMode
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    // --- ПРАВАЯ КОЛОНКА: ИВРИТ ---
                    Column(modifier = Modifier.weight(1f)) {
                        HintText(
                            text = if (viewModel.isExamMode) "" else stringResource(R.string.hint_tap_to_listen)
                        )
                        MatchColumn(
                            items = viewModel.hebrewCards,
                            onItemClick = viewModel::onMatchItemClicked,
                            modifier = Modifier.fillMaxWidth(),
                            isHebrewColumn = true,
                            errorCount = viewModel.errorCount,
                            errorItemId = viewModel.errorItemId,
                            isExamMode = viewModel.isExamMode
                        )
                    }
                }
            } else {
                CircularProgressIndicator(modifier = Modifier.size(64.dp))
            }

            val shouldShowSheet = viewModel.showResultSheet && snapshot != null

            if (shouldShowSheet) {
                val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

                ModalBottomSheet(
                    onDismissRequest = { viewModel.hideResultSheet() },
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
    // roundIndex удален, он не нужен здесь
    errorCount: Int,
    errorItemId: UUID?,
    isExamMode: Boolean
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(items, key = { it.id }) { item ->
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
                MatchLineItem(
                    item = item,
                    onItemClick = onItemClick,
                    isHebrew = isHebrewColumn,
                    modifier = Modifier
                )
            }
        }
    }
}

@OptIn(ExperimentalTextApi::class)
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

    val styleConfig = CardStyles.getStyle(FontStyle.REGULAR)

    val currentDirection = if (isHebrew) LayoutDirection.Rtl else LayoutDirection.Ltr

    CompositionLocalProvider(LocalLayoutDirection provides currentDirection) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 56.dp)
                .clickable(
                    enabled = true,
                    onClick = { onItemClick(item) },
                    interactionSource = remember { MutableInteractionSource() },
                    indication = rememberRipple()
                ),
            shape = RoundedCornerShape(cornerRadius),
            colors = CardDefaults.cardColors(containerColor = cardColor),
            border = BorderStroke(2.dp, borderColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp, horizontal = 6.dp),
                contentAlignment = if (isHebrew) Alignment.CenterEnd else Alignment.CenterStart
            ) {
                val textStyle = if (isHebrew) {
                    TextStyle(
                        fontFamily = FontFamily(Font(R.font.noto_sans_hebrew_variable, variationSettings = FontVariation.Settings(
                            FontVariation.weight(styleConfig.fontWeight.roundToInt()),
                            FontVariation.width(styleConfig.fontWidth)
                        ))),
                        textAlign = TextAlign.Right,
                        fontSize = 26.sp,
                        color = StickyNoteText
                    )
                } else {
                    TextStyle(
                        fontFamily = FontFamily.Default,
                        textAlign = TextAlign.Left,
                        textDirection = TextDirection.Ltr,
                        fontSize = 20.sp,
                        color = StickyNoteText
                    )
                }

                Text(
                    text = item.text,
                    modifier = Modifier.fillMaxWidth(),
                    style = textStyle
                )
            }
        }
    }
}

@Composable
private fun HintText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .padding(bottom = 8.dp)
            .fillMaxWidth(),
        textAlign = TextAlign.Center
    )
}