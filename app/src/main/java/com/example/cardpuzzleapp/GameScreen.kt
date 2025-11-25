package com.example.cardpuzzleapp
import androidx.compose.ui.text.style.TextDirection
import android.util.Log
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.PlaylistAddCheck
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.cardpuzzleapp.ui.theme.StickyNoteText
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.roundToInt

private data class GameRoundState(
    val roundIndex: Int,
    val taskPrompt: String?,
    val taskType: TaskType,
    val originalHebrewText: String?,
    val segments: List<AudioSegment>?,
    val taskPairs: List<List<String>>?,
    val swapColumns: Boolean = false
)

private const val AUDIO_TAG = "AUDIO_DEBUG"
private const val UI_TAG = "UI_DEBUG"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalTextApi::class, ExperimentalLayoutApi::class)
@Composable
fun GameScreen(
    viewModel: CardViewModel,
    routeRoundIndex: Int,
    onHomeClick: () -> Unit,
    onJournalClick: () -> Unit,
    onSkipClick: () -> Unit,
    onTrackClick: (Int) -> Unit,
    onDictionaryClick: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val uiState = viewModel.uiState
    val isRoundWon = uiState.isRoundWon
    val fontStyle = uiState.fontStyle
    val snapshot = uiState.resultSnapshot
    val showResultSheet = uiState.showResultSheet
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(routeRoundIndex) {
        Log.d(UI_TAG, "GameScreen: Loaded round $routeRoundIndex")
        viewModel.loadRound(routeRoundIndex)
        if (viewModel.currentTaskType == TaskType.AUDITION) {
            delay(800)
            viewModel.playFullAudio()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.hapticEvents.collectLatest { event ->
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
                                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
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
                    imageVector = Icons.Default.Book,
                    contentDescription = stringResource(R.string.dictionary_title),
                    onClick = onDictionaryClick
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
            val currentSentence = viewModel.currentLevelSentences.getOrNull(viewModel.currentRoundIndex)

            val roundState = remember(viewModel.currentRoundIndex, viewModel.currentTaskPrompt, viewModel.currentTaskType, viewModel.currentHebrewPrompt, viewModel.currentSegments) {
                GameRoundState(
                    roundIndex = viewModel.currentRoundIndex,
                    taskPrompt = viewModel.currentTaskPrompt,
                    taskType = viewModel.currentTaskType,
                    originalHebrewText = viewModel.currentHebrewPrompt,
                    segments = viewModel.currentSegments,
                    taskPairs = currentSentence?.task_pairs,
                    swapColumns = currentSentence?.swapColumns ?: false
                )
            }

            val enterTransition = slideInHorizontally(animationSpec = tween(550, delayMillis = 100), initialOffsetX = { it }) + fadeIn(animationSpec = tween(550, delayMillis = 100))
            val exitTransition = slideOutHorizontally(animationSpec = tween(550), targetOffsetX = { -it }) + fadeOut(animationSpec = tween(550))

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
    val isSlowMode = dynamicState.isSlowMode
    val styleConfig = CardStyles.getStyle(fontStyle)
    val isPreGameLearning = dynamicState.isPreGameLearning
    val displayPairs = dynamicState.displayPairs
    val activeSlotId = dynamicState.activeSlotId

    val baseFontSize = if (fontStyle == FontStyle.CURSIVE) 32.sp else 28.sp
    val animatedFontSize by animateFloatAsState(
        targetValue = if (isRoundWon) 36f else baseFontSize.value,
        animationSpec = tween(600),
        label = "FontSizeAnimation"
    )

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

    val russianTextStyle = MaterialTheme.typography.headlineSmall.copy(
        color = StickyNoteText.copy(alpha = 0.9f),
        textAlign = TextAlign.Start,
        textDirection = TextDirection.Ltr
    )

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.7f)
                .zIndex(1f),
            color = MaterialTheme.colorScheme.background,
            shadowElevation = 8.dp
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {

                if (staticState.taskType == TaskType.AUDITION && staticState.segments != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.playFullAudio() },
                            enabled = true,
                            modifier = Modifier.size(50.dp),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(0.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = StickyNoteText),
                            border = BorderStroke(1.dp, StickyNoteText.copy(alpha = 0.5f))
                        ) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.VolumeUp, contentDescription = stringResource(R.string.button_listen), modifier = Modifier.size(28.dp))
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        val turtleColor = if (isSlowMode) MaterialTheme.colorScheme.primary else StickyNoteText
                        val turtleAlpha = if (isSlowMode) 1f else 0.5f
                        val turtleBorder = if (isSlowMode) 2.dp else 1.dp
                        OutlinedButton(
                            onClick = { viewModel.toggleSlowMode() },
                            modifier = Modifier.size(50.dp),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(0.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = turtleColor,
                                containerColor = if (isSlowMode) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                            ),
                            border = BorderStroke(turtleBorder, turtleColor.copy(alpha = turtleAlpha))
                        ) {
                            Icon(imageVector = Icons.Default.Speed, contentDescription = "Slow Mode", modifier = Modifier.size(28.dp))
                        }
                    }

                    val linesOfSlots = remember(dynamicState.assemblyLine) {
                        val result = mutableListOf<List<AssemblySlot>>()
                        var currentLine = mutableListOf<AssemblySlot>()
                        dynamicState.assemblyLine.forEach { slot ->
                            if (slot.text == "\n") {
                                if (currentLine.isNotEmpty()) { result.add(currentLine); currentLine = mutableListOf() }
                            } else { currentLine.add(slot) }
                        }
                        if (currentLine.isNotEmpty()) result.add(currentLine)
                        result
                    }

                    LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                        itemsIndexed(linesOfSlots) { index, lineSlots ->
                            val isPlayingThis = dynamicState.playingSegmentIndex == index
                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                                Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(if (isPlayingThis) Color.White else Color.Transparent).padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Start) {
                                    IconButton(onClick = { viewModel.playAudioSegment(index) }, enabled = true, modifier = Modifier.size(36.dp)) {
                                        Icon(imageVector = Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, tint = if (isPlayingThis) Color.Black else StickyNoteText.copy(alpha = 0.6f))
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    FlowRow(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.Start, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        lineSlots.forEach { slot ->
                                            if (isRoundWon) {
                                                val textToShow = slot.targetCard?.text ?: slot.text
                                                Text(text = textToShow, style = hebrewTextStyle, modifier = Modifier.padding(vertical = 2.dp))
                                            } else {
                                                if (!slot.isBlank) {
                                                    Text(text = slot.text, style = hebrewTextStyle, modifier = Modifier.padding(vertical = 2.dp))
                                                } else {
                                                    AssemblySlotItem(slot = slot, textStyle = hebrewTextStyle, fontStyle = fontStyle, taskType = staticState.taskType, onReturnCard = { }, isInteractionEnabled = isInteractionEnabled, isActive = (slot.id == activeSlotId), onClick = { viewModel.onAssemblySlotClicked(slot) })
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            HorizontalDivider(color = StickyNoteText.copy(alpha = 0.1f))
                        }
                    }
                } else {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                        Box(modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 100.dp)) {
                            when (staticState.taskType) {
                                TaskType.ASSEMBLE_TRANSLATION, TaskType.FILL_IN_BLANK, TaskType.QUIZ, TaskType.MAKE_QUESTION, TaskType.MAKE_ANSWER -> {

                                    val topContentInfo = when (staticState.taskType) {
                                        TaskType.MAKE_QUESTION -> null
                                        TaskType.MAKE_ANSWER -> staticState.originalHebrewText to hebrewTextStyle
                                        TaskType.QUIZ -> staticState.originalHebrewText to hebrewTextStyle
                                        TaskType.ASSEMBLE_TRANSLATION -> staticState.taskPrompt to russianTextStyle
                                        TaskType.FILL_IN_BLANK -> null
                                        else -> null
                                    }

                                    val bottomContent = if (staticState.taskType == TaskType.MAKE_QUESTION) staticState.originalHebrewText else null

                                    UniversalAssemblyTaskLayout(
                                        topContent = topContentInfo?.first,
                                        topTextStyle = topContentInfo?.second,
                                        bottomContent = bottomContent,
                                        assemblyLine = dynamicState.assemblyLine,
                                        defaultTextStyle = hebrewTextStyle,
                                        fontStyle = fontStyle,
                                        taskType = staticState.taskType,
                                        isInteractionEnabled = isInteractionEnabled,
                                        isRoundWon = isRoundWon,
                                        activeSlotId = activeSlotId,
                                        onSlotClick = { viewModel.onAssemblySlotClicked(it) }
                                    )
                                }

                                TaskType.CONJUGATION, TaskType.MATCHING_PAIRS -> {
                                    // --- ЛОГИКА ВИЗУАЛЬНОГО ОТОБРАЖЕНИЯ ---
                                    // Иврит (RTL) подразумевает: Первая колонка = Правая, Вторая = Левая.
                                    // MatchingPairs: Слева Русский, Справа Иврит. Это LTR структура визуально.
                                    // Conjugation: Справа Иврит, Слева Иврит. Это RTL структура.

                                    // Мы всегда используем isRtlStructure=true для CONJUGATION (чтобы работала логика Right/Left)
                                    // И isRtlStructure=false для MATCHING (чтобы было LTR).

                                    val isRtlStructure = staticState.taskType == TaskType.CONJUGATION

                                    // Заголовок берем прямо из файла
                                    val headerPrompt = if (staticState.taskType == TaskType.CONJUGATION) staticState.taskPrompt else null

                                    UniversalTableTaskLayout(
                                        assemblyLine = dynamicState.assemblyLine,
                                        taskPairs = displayPairs,
                                        textStyle = hebrewTextStyle,
                                        fontStyle = fontStyle,
                                        taskType = staticState.taskType,
                                        isInteractionEnabled = isInteractionEnabled,
                                        isRoundWon = isRoundWon,
                                        isPreGameLearning = isPreGameLearning,
                                        onCardClickInLearning = { cardText -> viewModel.speakWord(cardText) },
                                        isRtlStructure = isRtlStructure,
                                        headerPrompt = headerPrompt,
                                        activeSlotId = activeSlotId,
                                        onSlotClick = { viewModel.onAssemblySlotClicked(it) }
                                    )
                                }

                                else -> {
                                    Text("UNKNOWN TASK: ${staticState.taskType}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.headlineMedium)
                                }
                            }
                        }
                    }
                }
            }
        }

        HorizontalDivider(thickness = 1.dp, color = Color.LightGray)

        if (!isRoundWon) {
            if (isPreGameLearning) {
                Box(modifier = Modifier.weight(0.3f).fillMaxSize().background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.Center) {
                    Button(onClick = { viewModel.startGame() }, modifier = Modifier.fillMaxWidth(0.8f).height(60.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))) {
                        Text("Я запомнил, начать!", fontSize = 24.sp)
                    }
                }
            } else {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    FlowRow(
                        modifier = Modifier.weight(0.3f).fillMaxSize().background(MaterialTheme.colorScheme.surface).verticalScroll(rememberScrollState()).padding(top = 8.dp, start = 16.dp, end = 16.dp, bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        dynamicState.availableCards.forEach { slot ->
                            key(slot.id) {
                                Shakeable(trigger = dynamicState.errorCount, errorCardId = dynamicState.errorCardId, currentCardId = slot.card.id) { shakeModifier ->
                                    SelectableCard(modifier = shakeModifier, card = slot.card, onSelect = { viewModel.selectCard(slot) }, fontStyle = fontStyle, taskType = staticState.taskType, isAssembledCard = false, isVisible = slot.isVisible, isInteractionEnabled = isInteractionEnabled)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalTextApi::class)
@Composable
private fun UniversalAssemblyTaskLayout(
    topContent: String?,
    topTextStyle: TextStyle?,
    bottomContent: String?,
    assemblyLine: List<AssemblySlot>,
    defaultTextStyle: TextStyle,
    fontStyle: FontStyle,
    taskType: TaskType,
    isInteractionEnabled: Boolean,
    isRoundWon: Boolean,
    activeSlotId: UUID?,
    onSlotClick: (AssemblySlot) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (!topContent.isNullOrBlank()) {
            val styleToUse = topTextStyle ?: defaultTextStyle

            if (taskType == TaskType.ASSEMBLE_TRANSLATION) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Text(
                        text = topContent,
                        style = styleToUse,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                ScrollableTextContent(text = topContent, textStyle = styleToUse)
            }

            if (taskType == TaskType.QUIZ || taskType == TaskType.MAKE_ANSWER) {
                Spacer(modifier = Modifier.height(32.dp))
            } else {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        AssemblyFlow(
            assemblyLine = assemblyLine,
            textStyle = defaultTextStyle,
            fontStyle = fontStyle,
            taskType = taskType,
            isInteractionEnabled = isInteractionEnabled,
            isRoundWon = isRoundWon,
            activeSlotId = activeSlotId,
            onSlotClick = onSlotClick
        )

        if (!bottomContent.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = bottomContent,
                style = defaultTextStyle.copy(
                    textDirection = TextDirection.Rtl,
                    textAlign = TextAlign.Right
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun ScrollableTextContent(
    text: String,
    textStyle: TextStyle
) {
    val lines = text.lines().filter { it.isNotBlank() }

    if (lines.size > 1) {
        Column(modifier = Modifier.fillMaxWidth()) {
            val context = lines.dropLast(1).joinToString("\n")
            val question = lines.last()

            Text(text = context, style = textStyle, modifier = Modifier.fillMaxWidth())
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = StickyNoteText.copy(alpha = 0.5f), thickness = 2.dp)
            Text(text = question, style = textStyle, modifier = Modifier.fillMaxWidth())
        }
    } else {
        Text(
            text = text,
            style = textStyle,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UniversalTableTaskLayout(
    assemblyLine: List<AssemblySlot>,
    taskPairs: List<List<String>>,
    textStyle: TextStyle,
    fontStyle: FontStyle,
    taskType: TaskType,
    isInteractionEnabled: Boolean,
    isRoundWon: Boolean,
    isPreGameLearning: Boolean,
    onCardClickInLearning: (String) -> Unit,
    isRtlStructure: Boolean,
    headerPrompt: String?,
    activeSlotId: UUID?,
    onSlotClick: (AssemblySlot) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {

        if (!headerPrompt.isNullOrBlank()) {
            val parts = headerPrompt.split("/").map { it.trim() }
            // Прямой вывод заголовков: 0 -> Правая, 1 -> Левая
            val rightText = parts.getOrNull(0) ?: ""
            val leftText = parts.getOrNull(1) ?: ""

            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = rightText, style = textStyle, modifier = Modifier.weight(1f).padding(end = 16.dp), textAlign = TextAlign.Start)
                Text(text = leftText, style = textStyle, modifier = Modifier.weight(1f).padding(start = 16.dp), textAlign = TextAlign.Start)
            }
            HorizontalDivider(thickness = 2.dp, color = StickyNoteText)
            Spacer(modifier = Modifier.height(16.dp))
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            itemsIndexed(taskPairs) { index, pair ->
                val rowSlots = assemblyLine.filter { it.rowId == index }
                val staticSlot = rowSlots.find { !it.isBlank }
                val staticText = staticSlot?.text ?: ""
                val answerParts = rowSlots.filter { it.isBlank || it != staticSlot }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start
                ) {
                    if (isRtlStructure) {
                        // RTL: [Правый Элемент] [Левый Элемент]

                        // Элемент 1 (из assemblyLine для правой колонки)
                        // ViewModel уже положил туда либо Статику, либо Слот
                        val rightElement = rowSlots.getOrNull(0)
                        if (rightElement != null) {
                            if (rightElement.isBlank) {
                                FlowRow(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.Start, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    RenderAnswerSlots(isRoundWon, listOf(rightElement), textStyle, fontStyle, taskType, isInteractionEnabled, isPreGameLearning, onCardClickInLearning, activeSlotId, onSlotClick)
                                }
                            } else {
                                Text(text = rightElement.text, style = textStyle, modifier = Modifier.weight(1f).padding(end = 16.dp))
                            }
                        }

                        // Элемент 2 (из assemblyLine для левой колонки)
                        val leftElement = rowSlots.getOrNull(1)
                        if (leftElement != null) {
                            if (leftElement.isBlank) {
                                FlowRow(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.Start, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    RenderAnswerSlots(isRoundWon, listOf(leftElement), textStyle, fontStyle, taskType, isInteractionEnabled, isPreGameLearning, onCardClickInLearning, activeSlotId, onSlotClick)
                                }
                            } else {
                                Text(text = leftElement.text, style = textStyle, modifier = Modifier.weight(1f).padding(start = 16.dp))
                            }
                        }

                    } else {
                        // LTR (Matching): [Левый(Русский)] [Правый(Иврит)]
                        // Здесь уже своя логика, она не менялась
                        FlowRow(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.Start, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            RenderAnswerSlots(isRoundWon, answerParts, textStyle, fontStyle, taskType, isInteractionEnabled, isPreGameLearning, onCardClickInLearning, activeSlotId, onSlotClick)
                        }
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Text(text = staticText, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 20.sp, textAlign = TextAlign.Start), modifier = Modifier.weight(1f).padding(start = 16.dp))
                        }
                    }
                }

                if (index < taskPairs.size - 1) {
                    HorizontalDivider(color = StickyNoteText.copy(alpha = 0.1f), modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalTextApi::class)
@Composable
private fun AssemblyFlow(
    assemblyLine: List<AssemblySlot>,
    textStyle: TextStyle,
    fontStyle: FontStyle,
    taskType: TaskType,
    isInteractionEnabled: Boolean,
    isRoundWon: Boolean,
    activeSlotId: UUID?,
    onSlotClick: (AssemblySlot) -> Unit
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
                            onReturnCard = { },
                            isInteractionEnabled = isInteractionEnabled,
                            isActive = (slot.id == activeSlotId),
                            onClick = { onSlotClick(slot) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTextApi::class, ExperimentalLayoutApi::class)
@Composable
private fun RenderAnswerSlots(
    isRoundWon: Boolean,
    answerSlots: List<AssemblySlot>,
    textStyle: TextStyle,
    fontStyle: FontStyle,
    taskType: TaskType,
    isInteractionEnabled: Boolean,
    isPreGameLearning: Boolean,
    onCardClickInLearning: (String) -> Unit,
    activeSlotId: UUID?,
    onSlotClick: (AssemblySlot) -> Unit
) {
    if (isRoundWon) {
        answerSlots.forEach { slot ->
            val textToShow = slot.targetCard?.text ?: slot.text
            Text(
                text = textToShow,
                style = textStyle,
                color = Color(0xFF4CAF50),
                modifier = Modifier.padding(vertical = 2.dp)
            )
        }
    } else {
        answerSlots.forEach { slot ->
            if (isPreGameLearning && slot.filledCard != null) {
                SelectableCard(
                    card = slot.filledCard!!,
                    onSelect = { onCardClickInLearning(slot.filledCard!!.text) },
                    fontStyle = fontStyle,
                    taskType = taskType,
                    isAssembledCard = false,
                    isVisible = true,
                    isInteractionEnabled = true
                )
            } else {
                AssemblySlotItem(
                    slot = slot,
                    textStyle = textStyle,
                    fontStyle = fontStyle,
                    taskType = taskType,
                    onReturnCard = { },
                    isInteractionEnabled = isInteractionEnabled,
                    isActive = (slot.id == activeSlotId),
                    onClick = { onSlotClick(slot) }
                )
            }
        }
    }
}