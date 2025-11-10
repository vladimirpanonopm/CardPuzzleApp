package com.example.cardpuzzleapp
import androidx.compose.ui.text.style.TextDirection
import android.content.Context
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cardpuzzleapp.ui.theme.StickyNoteText
import com.example.cardpuzzleapp.ui.theme.StickyNoteYellow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection


@OptIn(ExperimentalMaterial3Api::class, ExperimentalTextApi::class, ExperimentalLayoutApi::class)
@Composable
fun GameScreen(
    viewModel: CardViewModel,
    onHomeClick: () -> Unit,
    onJournalClick: () -> Unit,
    onSkipClick: () -> Unit,
    onTrackClick: (Int) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    val snapshot = viewModel.resultSnapshot
    val isRoundWon = viewModel.isRoundWon

    val showResultSheet = viewModel.showResultSheet
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(Unit) {
        viewModel.navigationEvent.collectLatest { route ->
            if (route.startsWith("round_track/")) {
                val levelId = route.substringAfter("round_track/").toIntOrNull()
                if (levelId != null) {
                    onTrackClick(levelId)
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.hapticEvents.collectLatest { event ->
            Log.d("VIBRATE_DEBUG", "GameScreen received event: $event")
            when (event) {
                HapticEvent.Success -> haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                HapticEvent.Failure -> haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        }
    }


    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(id = viewModel.currentTaskTitleResId),
                onBackClick = onHomeClick,
                actions = {
                    if (viewModel.currentLevelId == 1) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    color = StickyNoteYellow,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable(
                                    onClick = { viewModel.toggleGameFontStyle() },
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = rememberRipple(color = StickyNoteText)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            FontToggleIcon(fontStyle = viewModel.gameFontStyle)
                        }
                    }
                }
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
                    onClick = { onTrackClick(viewModel.currentLevelId) }
                )
                if (viewModel.isRoundWon) {
                    AppBottomBarIcon(
                        imageVector = Icons.Default.Visibility,
                        contentDescription = stringResource(R.string.button_show_translation),
                        onClick = { viewModel.showResultSheet() }
                    )
                } else {
                    IconButton(onClick = onSkipClick, enabled = !viewModel.isLastRoundAvailable) {
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            val fontStyle = viewModel.gameFontStyle

            val initialFontSize = if (fontStyle == FontStyle.CURSIVE) 32.sp else 28.sp
            val animatedFontSize by animateFloatAsState(
                targetValue = if (isRoundWon) 36f else initialFontSize.value,
                animationSpec = tween(600),
                label = "FontSizeAnimation"
            )

            val styleConfig = CardStyles.getStyle(fontStyle)
            val hebrewTextStyle = if (fontStyle == FontStyle.REGULAR) {
                TextStyle(
                    fontFamily = FontFamily(Font(R.font.noto_sans_hebrew_variable, variationSettings = FontVariation.Settings(
                        FontVariation.weight(styleConfig.fontWeight.roundToInt()),
                        FontVariation.width(styleConfig.fontWidth)
                    ))),
                    fontSize = animatedFontSize.sp,
                    textAlign = TextAlign.Right,
                    lineHeight = (animatedFontSize * 1.4f).sp,
                    color = StickyNoteText,
                    textDirection = TextDirection.Rtl
                )
            } else {
                TextStyle(
                    fontFamily = fontStyle.fontFamily,
                    fontSize = animatedFontSize.sp,
                    textAlign = TextAlign.Right,
                    lineHeight = (animatedFontSize * 1.4f).sp,
                    fontWeight = FontWeight(styleConfig.fontWeight.roundToInt()),
                    color = StickyNoteText,
                    textDirection = TextDirection.Rtl
                )
            }


            if (isRoundWon) {
                // --- ЭКРАН ПОБЕДЫ ---
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1.0f),
                    color = StickyNoteYellow,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            text = viewModel.currentTaskPrompt ?: "",
                            style = MaterialTheme.typography.headlineSmall.copy(
                                color = StickyNoteText.copy(alpha = 0.7f),
                                textAlign = TextAlign.Start
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp)
                        )

                        val assembledText = remember(viewModel.isRoundWon, viewModel.currentTaskType) {
                            if (viewModel.currentTaskType == TaskType.ASSEMBLE_TRANSLATION) {
                                viewModel.targetCards.joinToString(separator = "") { it.text }
                            } else {
                                viewModel.assemblyLine.joinToString(separator = "") { slot ->
                                    val filledCard = slot.filledCard
                                    filledCard?.text ?: slot.targetCard?.text ?: slot.text
                                }
                            }
                        }

                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                            Text(
                                text = assembledText,
                                style = hebrewTextStyle,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(150.dp))
                    }
                }
            }
            // --- ЭКРАН ИГРЫ ---
            else {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.7f), // 70%
                    color = StickyNoteYellow,
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        item {
                            Text(
                                text = viewModel.currentTaskPrompt ?: "",
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    color = StickyNoteText.copy(alpha = 0.8f),
                                    textAlign = TextAlign.Start
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 16.dp)
                            )
                        }

                        item {
                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {

                                Box(modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 100.dp)) {

                                    when (viewModel.currentTaskType) {

                                        TaskType.ASSEMBLE_TRANSLATION -> {
                                            Text(
                                                text = viewModel.selectedCards.joinToString(separator = "") { it.text },
                                                style = hebrewTextStyle,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable(
                                                        interactionSource = remember { MutableInteractionSource() },
                                                        indication = null,
                                                        onClick = { viewModel.returnLastSelectedCard() }
                                                    )
                                            )
                                        }

                                        TaskType.FILL_IN_BLANK -> {
                                            FlowRow(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                viewModel.assemblyLine.forEach { slot ->
                                                    key(slot.id) {
                                                        AssemblySlotItem(
                                                            slot = slot,
                                                            textStyle = hebrewTextStyle,
                                                            fontStyle = fontStyle,
                                                            taskType = viewModel.currentTaskType,
                                                            onReturnCard = { viewModel.returnCardFromSlot(slot) }
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        // --- ИСПРАВЛЕНИЕ: Заменяем TODO() ---
                                        TaskType.MATCHING_PAIRS, TaskType.UNKNOWN -> {
                                            // Эта ветка не должна вызываться,
                                            // но мы оставляем ее пустой, чтобы избежать крэша.
                                        }
                                        // ------------------------------------
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }

                // --- Нижний "Банк" карт ---
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    FlowRow(
                        modifier = Modifier
                            .weight(0.3f) // 30%
                            .fillMaxSize()
                            .background(Color.White)
                            .verticalScroll(rememberScrollState())
                            .padding(top = 16.dp, start = 16.dp, end = 16.dp, bottom = 16.dp)
                            .animateContentSize(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        viewModel.availableCards.forEach { slot ->
                            key(slot.id) {
                                Shakeable(
                                    trigger = viewModel.errorCount,
                                    errorCardId = viewModel.errorCardId,
                                    currentCardId = slot.card.id
                                ) { shakeModifier ->
                                    SelectableCard(
                                        modifier = shakeModifier
                                            .graphicsLayer {
                                                alpha = if (slot.isVisible) 1f else 0f
                                            },
                                        card = slot.card,
                                        onSelect = {
                                            if (slot.isVisible) {
                                                viewModel.selectCard(slot)
                                            }
                                        },
                                        fontStyle = fontStyle,
                                        taskType = viewModel.currentTaskType,
                                        isAssembledCard = false
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showResultSheet && snapshot != null) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.hideResultSheet() },
            sheetState = sheetState,
            scrimColor = Color.Transparent,
            dragHandle = null
        ) {
            ResultSheetContent(
                snapshot = snapshot,
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
                onTrackClick = { onTrackClick(snapshot.levelId) }
            )
        }
    }
}