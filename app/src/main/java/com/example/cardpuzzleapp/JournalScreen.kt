package com.example.cardpuzzleapp
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.PlaylistAddCheck
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cardpuzzleapp.ui.theme.StickyNoteText
import com.example.cardpuzzleapp.ui.theme.StickyNoteYellow
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun JournalScreen(
    levelId: Int,
    journalViewModel: JournalViewModel,
    onBackClick: () -> Unit,
    onTrackClick: () -> Unit,
    initialRoundIndex: Int
) {
    val journalSentences = journalViewModel.journalSentences
    val coroutineScope = rememberCoroutineScope()

    var isFlipped by remember { mutableStateOf(false) }

    var journalFontSize by remember { mutableStateOf(journalViewModel.initialFontSize.sp) }
    var journalFontStyle by remember { mutableStateOf(journalViewModel.initialFontStyle) }

    var fontMenuExpanded by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var cardToDelete by remember { mutableStateOf<SentenceData?>(null) }

    var showRepeatPanel by remember { mutableStateOf(false) }
    var pointA by remember { mutableStateOf<Int?>(null) }
    var pointB by remember { mutableStateOf<Int?>(null) }
    var isLooping by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableStateOf(1.0f) }
    var pauseDuration by remember { mutableStateOf(1f) }

    LaunchedEffect(key1 = levelId) {
        journalViewModel.loadJournalForLevel(levelId)
    }

    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { journalSentences.size }
    )

    LaunchedEffect(journalSentences, initialRoundIndex) {
        if (journalSentences.isNotEmpty()) {
            val page = if (initialRoundIndex != -1) {
                val sentenceToShow = journalViewModel.currentLevelSentences.getOrNull(initialRoundIndex)
                journalSentences.indexOf(sentenceToShow).coerceAtLeast(0)
            } else 0
            pagerState.scrollToPage(page)
        }
    }

    LaunchedEffect(pagerState.isScrollInProgress, isFlipped, journalSentences.size) {
        if (pagerState.isScrollInProgress) {
            journalViewModel.stopAudio()
        } else if (!isFlipped && !isLooping && journalSentences.isNotEmpty()) {
            val currentPage = pagerState.currentPage.coerceIn(0, journalSentences.size - 1)
            journalViewModel.playSoundForPage(currentPage)
        }
    }

    LaunchedEffect(isLooping, pointA, pointB) {
        val localPointA = pointA
        val localPointB = pointB

        if (isLooping && localPointA != null && localPointB != null) {
            val start = minOf(localPointA, localPointB)
            val end = maxOf(localPointA, localPointB)

            if(pagerState.currentPage < start || pagerState.currentPage > end) {
                pagerState.animateScrollToPage(start)
            }

            while (isLooping) {
                val currentPage = pagerState.currentPage

                journalViewModel.playAndAwait(currentPage, playbackSpeed)
                if (!isLooping) break

                kotlinx.coroutines.delay((pauseDuration * 1000).toLong())
                if (!isLooping) break

                val nextPage = if (currentPage >= end) start else currentPage + 1
                pagerState.animateScrollToPage(nextPage)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            journalViewModel.releaseAudio()
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(showRepeatPanel) {
        if (showRepeatPanel) {
            sheetState.expand()
        }
    }

    if (showRepeatPanel) {
        ModalBottomSheet(
            onDismissRequest = { showRepeatPanel = false },
            sheetState = sheetState
        ) {
            RepeatControlPanel(
                cardCount = journalSentences.size,
                currentCardIndex = pagerState.currentPage,
                onSliderPositionChange = { newIndex ->
                    if (newIndex != pagerState.currentPage) {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(newIndex)
                        }
                    }
                },
                cardPreviewText = journalSentences.getOrNull(pagerState.currentPage)?.hebrew ?: "",
                pointA = pointA,
                pointB = pointB,
                onSetAClick = {
                    val localB = pointB
                    val newPointA = pagerState.currentPage
                    if (localB != null && newPointA > localB) {
                        pointA = localB
                        pointB = newPointA
                    } else {
                        pointA = newPointA
                    }
                },
                onSetBClick = {
                    val localA = pointA
                    val newPointB = pagerState.currentPage
                    if (localA != null && newPointB < localA) {
                        pointB = localA
                        pointA = newPointB
                    } else {
                        pointB = newPointB
                    }
                },
                onStartLoopClick = { isLooping = !isLooping },
                isLooping = isLooping,
                playbackSpeed = playbackSpeed,
                onSpeedChange = { speed -> playbackSpeed = speed },
                pauseDuration = pauseDuration,
                onPauseChange = { duration -> pauseDuration = duration }
            )
        }
    }


    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.journal_title),
                onBackClick = onBackClick,
                actions = {
                    Box {
                        IconButton(onClick = { fontMenuExpanded = true }) {
                            Icon(
                                imageVector = Icons.Default.TextFormat,
                                contentDescription = stringResource(R.string.cd_font_settings)
                            )
                        }
                        DropdownMenu(
                            expanded = fontMenuExpanded,
                            onDismissRequest = { fontMenuExpanded = false }
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                    FontStyle.values().forEach { style ->
                                        SegmentedButton(
                                            shape = RoundedCornerShape(16.dp),
                                            onClick = {
                                                journalFontStyle = style
                                                journalViewModel.saveJournalFontStyle(style)
                                            },
                                            selected = journalFontStyle == style
                                        ) {
                                            Text(stringResource(style.displayNameRes))
                                        }
                                    }
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text("A", fontSize = 16.sp)
                                    Slider(
                                        modifier = Modifier.weight(1f),
                                        value = journalFontSize.value,
                                        onValueChange = { journalFontSize = it.sp },
                                        valueRange = 28f..56f,
                                        onValueChangeFinished = {
                                            journalViewModel.saveJournalFontSize(journalFontSize.value)
                                        }
                                    )
                                    Text("A", fontSize = 32.sp)
                                }
                            }
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (journalSentences.isNotEmpty()) {
                val currentPage = pagerState.currentPage.coerceIn(0, journalSentences.size - 1)
                val currentSentence = journalSentences[currentPage]
                AppBottomBar {
                    AppBottomBarIcon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = stringResource(R.string.button_listen),
                        onClick = {
                            if (!isLooping) journalViewModel.playSoundForPage(currentPage)
                        }
                    )
                    AppBottomBarIcon(
                        imageVector = Icons.Default.Repeat,
                        contentDescription = stringResource(R.string.button_ab_repeat),
                        onClick = { showRepeatPanel = true }
                    )
                    AppBottomBarIcon(
                        imageVector = Icons.AutoMirrored.Filled.PlaylistAddCheck,
                        contentDescription = stringResource(R.string.round_track_title, levelId),
                        onClick = onTrackClick
                    )
                    AppBottomBarIcon(
                        imageVector = Icons.Default.Restore,
                        contentDescription = stringResource(R.string.button_forget),
                        onClick = {
                            val roundIndex = journalViewModel.currentLevelSentences.indexOf(currentSentence)
                            if (roundIndex != -1) {
                                coroutineScope.launch {
                                    if (pagerState.currentPage > 0 && pagerState.currentPage == journalSentences.size - 1) {
                                        pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                    }
                                    journalViewModel.resetSingleRoundProgress(roundIndex)
                                }
                            }
                        }
                    )
                    AppBottomBarIcon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.button_delete),
                        onClick = {
                            cardToDelete = currentSentence
                            showDeleteDialog = true
                        }
                    )
                }
            }
        }
    ) { paddingValues ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            color = MaterialTheme.colorScheme.surfaceContainer
        ) {
            if (showDeleteDialog && cardToDelete != null) {
                AlertDialog(
                    onDismissRequest = { showDeleteDialog = false },
                    title = { Text(stringResource(R.string.dialog_delete_title)) },
                    text = { Text(stringResource(R.string.dialog_delete_body)) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                val roundIndex = journalViewModel.currentLevelSentences.indexOf(cardToDelete)
                                if (roundIndex != -1) {
                                    coroutineScope.launch {
                                        if (pagerState.currentPage > 0 && pagerState.currentPage == journalSentences.size - 1) {
                                            pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                        }
                                        journalViewModel.archiveJournalCard(roundIndex)
                                    }
                                }
                                showDeleteDialog = false
                            }
                        ) { Text(stringResource(R.string.button_yes)) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.button_no)) }
                    }
                )
            }

            if (journalSentences.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(stringResource(R.string.journal_empty), style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 48.dp),
                        userScrollEnabled = !isLooping
                    ) { pageIndex ->
                        val pageOffset = (pagerState.currentPage - pageIndex) + pagerState.currentPageOffsetFraction
                        FlippableJournalCard(
                            sentence = journalSentences[pageIndex],
                            fontSize = journalFontSize,
                            fontStyle = journalFontStyle,
                            isFlipped = isFlipped,
                            modifier = Modifier
                                .graphicsLayer {
                                    scaleY = 1f - (abs(pageOffset) * 0.15f)
                                    scaleX = 1f - (abs(pageOffset) * 0.15f)
                                    alpha = 1f - (abs(pageOffset) * 0.5f)
                                }
                                .pointerInput(Unit) {
                                    detectTapGestures(onTap = { if (!isLooping) isFlipped = !isFlipped })
                                }
                        )
                    }
                }
            }
        }
    }
}


@Composable
fun FlippableJournalCard(
    sentence: SentenceData,
    fontSize: TextUnit,
    fontStyle: FontStyle,
    isFlipped: Boolean,
    modifier: Modifier = Modifier
) {
    val animatedRotation by animateFloatAsState(
        targetValue = if (isFlipped) 180f else 0f,
        animationSpec = tween(durationMillis = 600),
        label = "CardFlipRotation"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                rotationY = animatedRotation
                cameraDistance = 8 * density
            }
    ) {
        if (animatedRotation <= 90f) {
            JournalPageContent(
                sentence = sentence,
                isHebrewSide = true,
                fontSize = fontSize,
                fontStyle = fontStyle
            )
        } else {
            Box(Modifier.graphicsLayer { rotationY = 180f }) {
                JournalPageContent(
                    sentence = sentence,
                    isHebrewSide = false,
                    fontSize = fontSize,
                    fontStyle = fontStyle
                )
            }
        }
    }
}


@OptIn(ExperimentalTextApi::class)
@Composable
private fun JournalPageContent(
    sentence: SentenceData,
    isHebrewSide: Boolean,
    fontSize: TextUnit,
    fontStyle: FontStyle
) {

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = StickyNoteYellow,
            shape = RoundedCornerShape(16.dp),
            shadowElevation = 8.dp
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.TopStart
            ) {
                val scrollState = rememberScrollState()

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 2. ТЕКСТ (Иврит)
                    if (isHebrewSide) {

                        val (textToShow, alignment) = when (sentence.taskType) {
                            TaskType.MATCHING_PAIRS -> {
                                val pairsText = sentence.task_pairs?.joinToString("\n") { it.getOrNull(0) ?: "" } ?: ""
                                pairsText to TextAlign.Right
                            }
                            TaskType.FILL_IN_BLANK -> {
                                val hebrewString = sentence.hebrew
                                val correctCards = sentence.task_correct_cards ?: emptyList()
                                val parts = hebrewString.split("___")
                                val assembledText = buildString {
                                    parts.forEachIndexed { index, part ->
                                        append(part)
                                        if (index < correctCards.size) {
                                            append(correctCards[index])
                                        }
                                    }
                                }
                                assembledText to TextAlign.Right
                            }
                            else -> {
                                sentence.hebrew to TextAlign.Right
                            }
                        }

                        val styleConfig = CardStyles.getStyle(fontStyle)

                        val hebrewTextStyle = if (fontStyle == FontStyle.REGULAR) {
                            TextStyle(
                                fontFamily = FontFamily(Font(R.font.noto_sans_hebrew_variable, variationSettings = FontVariation.Settings(
                                    FontVariation.weight(styleConfig.fontWeight.roundToInt()),
                                    FontVariation.width(styleConfig.fontWidth)
                                ))),
                                fontSize = fontSize,
                                textAlign = alignment,
                                color = StickyNoteText,
                                textDirection = TextDirection.Rtl
                            )
                        } else { // CURSIVE
                            TextStyle(
                                fontFamily = fontStyle.fontFamily,
                                fontSize = fontSize,
                                textAlign = alignment,
                                fontWeight = FontWeight(styleConfig.fontWeight.roundToInt()),
                                color = StickyNoteText,
                                textDirection = TextDirection.Rtl
                            )
                        }
                        Text(
                            text = textToShow,
                            style = hebrewTextStyle,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // 3. ТЕКСТ (Перевод)
                    } else {
                        val (textToShow, alignment) = if (sentence.taskType == TaskType.MATCHING_PAIRS) {
                            val pairsText = sentence.task_pairs?.joinToString("\n") { it.getOrNull(1) ?: "" } ?: ""
                            pairsText to TextAlign.Left
                        } else {
                            // Используем поле translation напрямую
                            sentence.translation to TextAlign.Left
                        }

                        val translationTextStyle = MaterialTheme.typography.headlineMedium.copy(
                            fontSize = fontSize,
                            textAlign = alignment,
                            color = StickyNoteText
                        )
                        Text(
                            text = textToShow ?: "",
                            style = translationTextStyle,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}