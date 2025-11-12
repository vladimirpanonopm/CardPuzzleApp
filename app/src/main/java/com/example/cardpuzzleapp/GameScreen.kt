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

@Immutable
private data class GameRoundState(
    val roundIndex: Int,
    val taskPrompt: String?,
    val taskType: TaskType,
    val targetCards: List<Card>
)

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
    Log.i(AppDebug.TAG, ">>> GameScreen RECOMPOSING. routeRoundIndex: $routeRoundIndex, VM.currentRoundIndex: ${viewModel.currentRoundIndex}, Type: ${viewModel.currentTaskType}, Prompt: '${viewModel.currentTaskPrompt}'")

    val coroutineScope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    val uiState = viewModel.uiState

    val isRoundWon = uiState.isRoundWon
    val fontStyle = uiState.fontStyle
    val snapshot = uiState.resultSnapshot
    val showResultSheet = uiState.showResultSheet

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(routeRoundIndex) {
        Log.i(AppDebug.TAG, ">>> GameScreen LaunchedEffect(routeRoundIndex=$routeRoundIndex). Вызов viewModel.loadRound().")
        viewModel.loadRound(routeRoundIndex)
    }

    // --- ИСПРАВЛЕНИЕ: ЭТОТ БЛОК УДАЛЕН ---
    // LaunchedEffect(Unit) {
    //     viewModel.navigationEvent.collectLatest { route ->
    //         if (route.startsWith("round_track/")) { ... }
    //     }
    // }
    // --- КОНЕЦ ИСПРАВЛЕНИЯ ---

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
                            FontToggleIcon(fontStyle = fontStyle)
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
                if (isRoundWon) {
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

            val roundState = remember(viewModel.currentRoundIndex, viewModel.currentTaskPrompt, viewModel.currentTaskType, viewModel.targetCards) {
                GameRoundState(
                    roundIndex = viewModel.currentRoundIndex,
                    taskPrompt = viewModel.currentTaskPrompt,
                    taskType = viewModel.currentTaskType,
                    targetCards = viewModel.targetCards.toList()
                )
            }

            val enterTransition = slideInHorizontally(
                animationSpec = tween(500, delayMillis = 100),
                initialOffsetX = { -it }
            ) + fadeIn(animationSpec = tween(500, delayMillis = 100))

            val exitTransition = slideOutHorizontally(
                animationSpec = tween(500),
                targetOffsetX = { it }
            ) + fadeOut(animationSpec = tween(500))


            AnimatedContent(
                targetState = roundState,
                label = "GameScreenAnimation",
                transitionSpec = {
                    if (targetState.roundIndex != initialState.roundIndex) {
                        enterTransition togetherWith exitTransition
                    } else {
                        fadeIn(animationSpec = tween(600)) togetherWith fadeOut(animationSpec = tween(600))
                    }
                }
            ) { staticState ->

                GameScreenLayout(
                    staticState = staticState,
                    dynamicState = uiState,
                    viewModel = viewModel
                )
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


@OptIn(ExperimentalTextApi::class, ExperimentalLayoutApi::class)
@Composable
private fun GameScreenLayout(
    staticState: GameRoundState,
    dynamicState: CardViewModel.GameUiState,
    viewModel: CardViewModel
) {

    val fontStyle = dynamicState.fontStyle
    val isRoundWon = dynamicState.isRoundWon

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

    Column(modifier = Modifier.fillMaxSize()) {
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
                Text(
                    text = staticState.taskPrompt ?: "",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        color = StickyNoteText.copy(alpha = 0.8f),
                        textAlign = TextAlign.Start
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                )

                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 100.dp)
                    ) {
                        if (isRoundWon) {
                            val assembledText = remember(isRoundWon, staticState.taskType) {
                                if (staticState.taskType == TaskType.ASSEMBLE_TRANSLATION) {
                                    staticState.targetCards.joinToString(separator = "") { it.text }
                                } else {
                                    dynamicState.assemblyLine.joinToString(separator = "") { slot ->
                                        val filledCard = slot.filledCard
                                        filledCard?.text ?: slot.targetCard?.text ?: slot.text
                                    }
                                }
                            }
                            Text(
                                text = assembledText,
                                style = hebrewTextStyle,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            when (staticState.taskType) {
                                TaskType.ASSEMBLE_TRANSLATION -> {
                                    Text(
                                        text = dynamicState.selectedCards.joinToString(separator = "") { it.text },
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
                                        dynamicState.assemblyLine.forEach { slot ->
                                            key(slot.id) {
                                                AssemblySlotItem(
                                                    slot = slot,
                                                    textStyle = hebrewTextStyle,
                                                    fontStyle = fontStyle,
                                                    taskType = staticState.taskType,
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
            }
        }

        if (!isRoundWon) {
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
                    dynamicState.availableCards.forEach { slot ->
                        key(slot.id) {
                            Shakeable(
                                trigger = dynamicState.errorCount,
                                errorCardId = dynamicState.errorCardId,
                                currentCardId = slot.card.id
                            ) { shakeModifier ->
                                SelectableCard(
                                    modifier = shakeModifier,
                                    card = slot.card,
                                    onSelect = {
                                        viewModel.selectCard(slot)
                                    },
                                    fontStyle = fontStyle,
                                    taskType = staticState.taskType,
                                    isAssembledCard = false,
                                    isVisible = slot.isVisible
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}