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

private const val TAG = "UI_ROUND_DEBUG"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalTextApi::class, ExperimentalLayoutApi::class)
@Composable
fun GameScreen(
    viewModel: CardViewModel,
    routeRoundIndex: Int,
    onHomeClick: () -> Unit,
    onJournalClick: () -> Unit,
    onSkipClick: () -> Unit,
    onTrackClick: (Int) -> Unit
) {
    Log.i(AppDebug.TAG, ">>> GameScreen RECOMPOSING. routeRoundIndex: $routeRoundIndex, VM.currentRoundIndex: ${viewModel.currentRoundIndex}, Type: ${viewModel.currentTaskType}, Prompt: '${viewModel.currentTaskPrompt}', Cards: ${viewModel.availableCards.size}")

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    val isRoundWon = viewModel.isRoundWon
    val snapshot = viewModel.resultSnapshot
    val showResultSheet = viewModel.showResultSheet
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // GameScreen *всегда* сообщает ViewModel, какой раунд мы хотим.
    // А "атомарный" loadRound() в ViewModel позаботится, чтобы не было мерцания.
    LaunchedEffect(routeRoundIndex) {
        Log.i(AppDebug.TAG, ">>> GameScreen LaunchedEffect(routeRoundIndex=$routeRoundIndex). Вызов viewModel.loadRound().")
        viewModel.loadRound(routeRoundIndex)
    }

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
                    Spacer(modifier = Modifier.size(48.dp))
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
            // Анимация шрифта при победе (ОСТАВЛЯЕМ, т.к. она не конфликтует)
            val animatedFontSize by animateFloatAsState(
                targetValue = if (isRoundWon) 36f else initialFontSize.value,
                animationSpec = tween(600),
                label = "FontSizeAnimation"
            )

            val styleConfig = CardStyles.getStyle(fontStyle)
            // Стиль для иврита (использует анимированный размер)
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

            // Проверяем, готов ли UI к показу
            val isDataReady = viewModel.currentRoundIndex == routeRoundIndex && viewModel.currentTaskType != TaskType.MATCHING_PAIRS

            if (!isDataReady) {
                // --- СПИННЕР (Только при ПЕРВОЙ загрузке) ---
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                // --- ЭКРАН ИГРЫ (ДАННЫЕ ГОТОВЫ) ---

                // --- Верхняя "Желтая" часть ---
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    color = StickyNoteYellow,
                ) {
                    // --- Оборачиваем все в Column ---
                    Column(modifier = Modifier.fillMaxSize()) {

                        // --- Русский текст и Иврит (Верхняя часть) ---
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(0.7f)
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp),
                        ) {
                            // --- Русский текст (Подсказка) ---
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

                            // --- Область для Иврита ---
                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .defaultMinSize(minHeight = 100.dp)
                                ) {
                                    if (isRoundWon) {
                                        // --- 1. ЭКРАН ПОБЕДЫ ---
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
                                        Text(
                                            text = assembledText,
                                            style = hebrewTextStyle, // (Стиль с анимированным шрифтом)
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    } else {
                                        // --- 2. ЭКРАН ИГРЫ ---
                                        when (viewModel.currentTaskType) {
                                            TaskType.ASSEMBLE_TRANSLATION -> {
                                                Text(
                                                    text = viewModel.selectedCards.joinToString(separator = "") { it.text },
                                                    style = hebrewTextStyle, // (Стиль с обычным шрифтом)
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
                                                                textStyle = hebrewTextStyle, // (Стиль с обычным шрифтом)
                                                                fontStyle = fontStyle,
                                                                taskType = viewModel.currentTaskType,
                                                                onReturnCard = { viewModel.returnCardFromSlot(slot) }
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                            TaskType.MATCHING_PAIRS, TaskType.UNKNOWN -> {}
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        } // --- Конец верхней части (Column 0.7) ---

                        // --- Нижний "Банк" карт (30%) ---
                        if (!isRoundWon) {
                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                                FlowRow(
                                    modifier = Modifier
                                        .weight(0.3f)
                                        .fillMaxSize() // (fillMaxSize нужен для weight)
                                        .background(Color.White)
                                        .verticalScroll(rememberScrollState())
                                        .padding(top = 16.dp, start = 16.dp, end = 16.dp, bottom = 16.dp),
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
                                                    modifier = shakeModifier,
                                                    card = slot.card,
                                                    onSelect = {
                                                        // --- ИЗМЕНЕНИЕ: Убираем 'if' ---
                                                        // (Теперь guard в ViewModel)
                                                        viewModel.selectCard(slot)
                                                    },
                                                    fontStyle = fontStyle,
                                                    taskType = viewModel.currentTaskType,
                                                    isAssembledCard = false,
                                                    // --- ИЗМЕНЕНИЕ: Передаем isVisible ---
                                                    isVisible = slot.isVisible
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        } // --- Конец IF (!isRoundWon) ---
                    } // --- Конец Column (внутри Surface) ---
                } // --- Конец Surface ---
            } // --- Конец IF (isDataReady) ---
        } // --- Конец Column (в Scaffold) ---
    } // --- Конец Scaffold ---

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