package com.example.cardpuzzleapp
import androidx.compose.ui.text.style.TextDirection
import android.content.Context
import android.util.Log
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.PlaylistAddCheck
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.PlayArrow
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
    val originalHebrewText: String?
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

        if (viewModel.currentTaskType == TaskType.AUDITION) {
            delay(800)
            viewModel.replayAuditionAudio()
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
                            FontToggleIcon(fontStyle = fontStyle)
                        }
                    }
                }
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
                    imageVector = Icons.AutoMirrored.Filled.PlaylistAddCheck,
                    contentDescription = stringResource(R.string.round_track_title, viewModel.currentLevelId),
                    onClick = { onTrackClick(viewModel.currentLevelId) }
                )
                if (isRoundWon) {
                    AppBottomBarIcon(
                        imageVector = Icons.Default.Visibility,
                        contentDescription = stringResource(R.string.button_show_result),
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

            val roundState = remember(viewModel.currentRoundIndex, viewModel.currentTaskPrompt, viewModel.currentTaskType, viewModel.currentHebrewPrompt) {
                GameRoundState(
                    roundIndex = viewModel.currentRoundIndex,
                    taskPrompt = viewModel.currentTaskPrompt,
                    taskType = viewModel.currentTaskType,
                    originalHebrewText = viewModel.currentHebrewPrompt
                )
            }

            val enterTransition = slideInHorizontally(
                animationSpec = tween(550, delayMillis = 100),
                initialOffsetX = { it }
            ) + fadeIn(animationSpec = tween(550, delayMillis = 100))

            val exitTransition = slideOutHorizontally(
                animationSpec = tween(550),
                targetOffsetX = { -it }
            ) + fadeOut(animationSpec = tween(550))


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
    val isInteractionEnabled = !dynamicState.isAudioPlaying

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
                if (staticState.taskType == TaskType.AUDITION) {

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                            .defaultMinSize(minHeight = 50.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.replayAuditionAudio() },
                            enabled = isInteractionEnabled,
                            modifier = Modifier.size(50.dp),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(0.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = StickyNoteText
                            ),
                            border = BorderStroke(1.dp, StickyNoteText.copy(alpha = 0.5f))
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = stringResource(R.string.button_listen),
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                } else if (staticState.taskType == TaskType.QUIZ) {

                    val fullPrompt = staticState.originalHebrewText ?: ""
                    val promptLines = fullPrompt.lines().filter { it.isNotBlank() }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        if (promptLines.size > 1) {
                            val context = promptLines.dropLast(1).joinToString("\n")
                            val question = promptLines.last()

                            // 1. Контекст
                            Text(
                                text = context,
                                style = hebrewTextStyle
                            )
                            // 2. Разделитель
                            HorizontalDivider(
                                modifier = Modifier
                                    .fillMaxWidth(0.6f)
                                    .padding(vertical = 8.dp),
                                color = StickyNoteText.copy(alpha = 0.4f)
                            )
                            // 3. Вопрос
                            Text(
                                text = question,
                                style = hebrewTextStyle
                            )
                        } else {
                            Text(
                                text = fullPrompt,
                                style = hebrewTextStyle
                            )
                        }
                    }

                } else {
                    Text(
                        text = staticState.taskPrompt ?: "",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            color = StickyNoteText.copy(alpha = 0.8f),
                            textAlign = TextAlign.Start,
                            textDirection = TextDirection.Ltr
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    )
                }

                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 100.dp)
                    ) {

                        when (staticState.taskType) {
                            TaskType.ASSEMBLE_TRANSLATION,
                            TaskType.AUDITION,
                            TaskType.FILL_IN_BLANK,
                            TaskType.QUIZ -> {
                                FillInBlankTaskLayout(
                                    assemblyLine = dynamicState.assemblyLine,
                                    textStyle = hebrewTextStyle,
                                    fontStyle = fontStyle,
                                    taskType = staticState.taskType,
                                    isInteractionEnabled = isInteractionEnabled,
                                    isRoundWon = isRoundWon,
                                    // --- ИЗМЕНЕНИЕ: Передаем пустую лямбду ---
                                    onReturnCardFromSlot = { }
                                    // --- КОНЕЦ ИЗМЕНЕНИЯ ---
                                )
                            }
                            TaskType.MATCHING_PAIRS, TaskType.UNKNOWN -> {}
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
                        .padding(top = 8.dp, start = 16.dp, end = 16.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
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
                                    isVisible = slot.isVisible,
                                    isInteractionEnabled = isInteractionEnabled
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Отображает поле для FILL_IN_BLANK / AUDITION / ASSEMBLE_TRANSLATION / QUIZ
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalTextApi::class)
@Composable
private fun FillInBlankTaskLayout(
    assemblyLine: List<AssemblySlot>,
    textStyle: TextStyle,
    fontStyle: FontStyle,
    taskType: TaskType,
    isInteractionEnabled: Boolean,
    isRoundWon: Boolean,
    onReturnCardFromSlot: (AssemblySlot) -> Unit // (Параметр остался, но мы передаем {} )
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        assemblyLine.forEach { slot ->
            key(slot.id) {

                if (isRoundWon) {
                    val textToShow = slot.targetCard?.text ?: slot.text

                    if (textToShow == "\n") {
                        Spacer(modifier = Modifier.fillMaxWidth())
                    } else {
                        Text(
                            text = textToShow,
                            style = textStyle,
                            modifier = Modifier.padding(vertical = CardStyles.getStyle(fontStyle).verticalPadding)
                        )
                    }
                } else {
                    if (slot.text == "\n" && !slot.isBlank) {
                        Spacer(modifier = Modifier.fillMaxWidth())
                    } else {
                        AssemblySlotItem(
                            slot = slot,
                            textStyle = textStyle,
                            fontStyle = fontStyle,
                            taskType = taskType,
                            // --- ИЗМЕНЕНИЕ: Передаем пустую лямбду ---
                            onReturnCard = { }, // <-- Клик по вставленной карточке теперь ничего не делает
                            // --- КОНЕЦ ИЗМЕНЕНИЯ ---
                            isInteractionEnabled = isInteractionEnabled
                        )
                    }
                }
            }
        }
    }
}