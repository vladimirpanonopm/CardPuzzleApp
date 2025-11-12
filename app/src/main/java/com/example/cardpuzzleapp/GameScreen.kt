package com.example.cardpuzzleapp
import androidx.compose.ui.text.style.TextDirection
import android.content.Context
import android.util.Log
// --- ИЗМЕНЕНИЕ: ИМПОРТЫ ДЛЯ ANIMATEDCONTENT ---
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
// --- НОВЫЕ ИМПОРТЫ ДЛЯ СЛАЙДА ---
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
// ------------------------------------
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
import java.util.UUID

private const val TAG = "UI_ROUND_DEBUG"

// --- ИЗМЕНЕНИЕ: Data class теперь содержит ТОЛЬКО данные, определяющие раунд ---
@Immutable
private data class GameRoundState(
    val roundIndex: Int,
    val isDataReady: Boolean,

    // --- Данные, которые не меняются во время раунда ---
    val taskPrompt: String?,
    val taskType: TaskType,
    val assemblyLine: List<AssemblySlot>,
    val availableCards: List<AvailableCardSlot>,
    val targetCards: List<Card>
)
// --- КОНЕЦ ИЗМЕНЕНИЯ ---

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

    // --- ИЗМЕНЕНИЕ: Эти данные читаются НАПРЯМУЮ из VM ---
    // Они НЕ будут в 'targetState' анимации
    val isRoundWon = viewModel.isRoundWon
    val selectedCards = viewModel.selectedCards.toList()
    val errorCount = viewModel.errorCount
    val errorCardId = viewModel.errorCardId
    val fontStyle = viewModel.gameFontStyle
    // --- КОНЕЦ ИЗМЕНЕНИЯ ---

    val snapshot = viewModel.resultSnapshot
    val showResultSheet = viewModel.showResultSheet
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Эта логика остается
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
                            FontToggleIcon(fontStyle = fontStyle) // (Используем локальную)
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
                if (isRoundWon) { // (Используем локальную)
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
        // 'Scaffold' (TopBar, BottomBar) остается СТАТИЧНЫМ.
        // Анимируется только 'Column' ВНУТРИ.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {

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

            // --- Анимация "КНИГИ" (как вы просили) ---
            val enterTransition = slideInHorizontally(
                animationSpec = tween(500, delayMillis = 100),
                initialOffsetX = { -it }
            ) + fadeIn(animationSpec = tween(500, delayMillis = 100))

            val exitTransition = slideOutHorizontally(
                animationSpec = tween(500),
                targetOffsetX = { it }
            ) + fadeOut(animationSpec = tween(500))

            // --- ИЗМЕНЕНИЕ: Анимация "книги" НЕ срабатывает при победе ---
            val transition = remember(isRoundWon) {
                if (isRoundWon) {
                    // Если мы выиграли, просто "исчезаем" (Fade)
                    fadeIn(animationSpec = tween(600)) togetherWith fadeOut(animationSpec = tween(600))
                } else {
                    // Если переход между раундами, используем "книгу"
                    enterTransition togetherWith exitTransition
                }
            }
            // --- КОНЕЦ ИЗМЕНЕНИЯ ---

            // --- ИЗМЕНЕНИЕ: Создаем "ключ" (targetState) ---
            // Собираем *только* данные, определяющие раунд
            val isDataReady = viewModel.currentRoundIndex == routeRoundIndex && viewModel.currentTaskType != TaskType.MATCHING_PAIRS
            val roundState = GameRoundState(
                roundIndex = viewModel.currentRoundIndex,
                isDataReady = isDataReady,

                // --- Передаем данные, которые не меняются во время раунда ---
                taskPrompt = viewModel.currentTaskPrompt,
                taskType = viewModel.currentTaskType,
                assemblyLine = viewModel.assemblyLine.toList(),
                availableCards = viewModel.availableCards.toList(),
                targetCards = viewModel.targetCards.toList()
            )
            // --- КОНЕЦ ИЗМЕНЕНИЯ ---

            // 5. Оборачиваем ВСЕ в AnimatedContent
            AnimatedContent(
                targetState = roundState, // <-- Ключ теперь 'roundState'
                label = "GameScreenAnimation",
                transitionSpec = {
                    // --- ИЗМЕНЕНИЕ: Проверяем, изменился ли ТОЛЬКО roundIndex ---
                    // Это предотвращает "книжную" анимацию при победе
                    if (targetState.roundIndex != initialState.roundIndex) {
                        enterTransition togetherWith exitTransition
                    } else {
                        // (Анимация победы)
                        fadeIn(animationSpec = tween(600)) togetherWith fadeOut(animationSpec = tween(600))
                    }
                }
            ) { state ->
                // 'state' - это 'roundState' в момент анимации.
                // Он содержит "снимок" данных раунда.

                if (!state.isDataReady) {
                    // --- СПИННЕР ---
                    Box(
                        modifier = Modifier.fillMaxSize(), // (Занимает все место)
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    // --- ЭКРАН ИГРЫ (ДАННЫЕ ГОТОВЫ) ---
                    Column(modifier = Modifier.fillMaxSize()) {

                        // --- Верхняя "Желтая" часть (70%) ---
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(0.7f),
                            color = StickyNoteYellow,
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp)
                                    .verticalScroll(rememberScrollState()),
                            ) {
                                // --- Русский текст (Подсказка) ---
                                Text(
                                    text = state.taskPrompt ?: "", // <-- (из state)
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
                                        // --- ИЗМЕНЕНИЕ: Читаем 'isRoundWon' НАПРЯМУЮ из VM ---
                                        if (isRoundWon) {
                                            // --- 1. ЭКРАН ПОБЕДЫ ---
                                            val assembledText = remember(isRoundWon, state.taskType) {
                                                if (state.taskType == TaskType.ASSEMBLE_TRANSLATION) {
                                                    state.targetCards.joinToString(separator = "") { it.text }
                                                } else {
                                                    state.assemblyLine.joinToString(separator = "") { slot ->
                                                        val filledCard = slot.filledCard
                                                        filledCard?.text ?: slot.targetCard?.text ?: slot.text
                                                    }
                                                }
                                            }
                                            Text(
                                                text = assembledText,
                                                style = hebrewTextStyle, // (Используем локальный стиль)
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        } else {
                                            // --- 2. ЭКРАН ИГРЫ ---
                                            when (state.taskType) { // (из state)
                                                TaskType.ASSEMBLE_TRANSLATION -> {
                                                    Text(
                                                        // --- ИЗМЕНЕНИЕ: Читаем 'selectedCards' НАПРЯМУЮ из VM ---
                                                        text = selectedCards.joinToString(separator = "") { it.text },
                                                        style = hebrewTextStyle, // (Используем локальный стиль)
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
                                                        state.assemblyLine.forEach { slot -> // (из state)
                                                            key(slot.id) {
                                                                AssemblySlotItem(
                                                                    slot = slot,
                                                                    textStyle = hebrewTextStyle, // (Используем локальный стиль)
                                                                    fontStyle = fontStyle, // (Используем локальный)
                                                                    taskType = state.taskType, // (из state)
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
                        } // --- Конец Surface (0.7) ---

                        // --- Нижний "Банк" карт (30%) ---
                        if (!isRoundWon) { // (Используем локальный)
                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                                FlowRow(
                                    modifier = Modifier
                                        .weight(0.3f)
                                        .fillMaxSize()
                                        .background(Color.White)
                                        .verticalScroll(rememberScrollState())
                                        .padding(top = 16.dp, start = 16.dp, end = 16.dp, bottom = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    state.availableCards.forEach { slot -> // (из state)
                                        key(slot.id) {
                                            Shakeable(
                                                trigger = errorCount, // (Используем локальный)
                                                errorCardId = errorCardId, // (Используем локальный)
                                                currentCardId = slot.card.id
                                            ) { shakeModifier ->
                                                SelectableCard(
                                                    modifier = shakeModifier,
                                                    card = slot.card,
                                                    onSelect = {
                                                        viewModel.selectCard(slot)
                                                    },
                                                    fontStyle = fontStyle, // (Используем локальный)
                                                    taskType = state.taskType, // (из state)
                                                    isAssembledCard = false,
                                                    isVisible = slot.isVisible
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        } // --- Конец IF (!isRoundWon) ---
                    } // --- Конец Column (внутри AnimatedContent) ---
                } // --- Конец IF (isDataReady) ---
            } // --- Конец AnimatedContent ---
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