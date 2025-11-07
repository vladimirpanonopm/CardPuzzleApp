package com.example.cardpuzzleapp
import androidx.compose.ui.text.style.TextDirection
import android.content.Context
import android.util.Log
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import java.io.IOException


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
    val view = LocalView.current

    val snapshot = viewModel.resultSnapshot
    val isRoundWon = viewModel.isRoundWon

    val showResultSheet = viewModel.showResultSheet
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val audioPlayer = remember { AudioPlayer(context) }
    DisposableEffect(Unit) {
        onDispose {
            audioPlayer.release()
        }
    }

    // --- ИСПРАВЛЕНИЕ 1: Анимации "по очереди" ---
    // (Ждем, пока шрифт закончит анимацию 600мс, потом показываем шторку)
    LaunchedEffect(isRoundWon) {
        if (isRoundWon) {
            delay(650)
            viewModel.onVictoryAnimationFinished()
        }
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
        val vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
        }

        viewModel.hapticEvents.collectLatest { event ->
            Log.d("VIBRATE_DEBUG", "GameScreen received event: $event")

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                val vibrationEffect = android.os.VibrationEffect.createPredefined(android.os.VibrationEffect.EFFECT_CLICK)
                vibrator.vibrate(vibrationEffect)

            } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val vibrationEffect = android.os.VibrationEffect.createOneShot(20, android.os.VibrationEffect.DEFAULT_AMPLITUDE)
                vibrator.vibrate(vibrationEffect)

            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(20)
            }
        }
    }


    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(id = R.string.game_task_assemble),
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
                        onClick = { viewModel.onVictoryAnimationFinished() }
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

            // --- НАЧАЛО БОЛЬШОГО ИЗМЕНЕНИЯ (ПЛАН "ТАК ТОЧНО!") ---

            // Мы анимируем только шрифт
            val initialFontSize = if (fontStyle == FontStyle.CURSIVE) 32.sp else 28.sp
            val animatedFontSize by animateFloatAsState(
                targetValue = if (isRoundWon) 36f else initialFontSize.value,
                animationSpec = tween(600),
                label = "FontSizeAnimation"
            )

            // ЕСЛИ РАУНД ВЫИГРАН ("Фон Победы")
            if (isRoundWon) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1.0f), // <-- 100% Экрана
                    color = StickyNoteYellow,
                ) {
                    // Используем НОВУЮ разметку (с .verticalScroll наверху)
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState()), // <-- Скролл на всё
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val imageBitmap = remember(viewModel.currentImageName) {
                            viewModel.currentImageName?.let {
                                try {
                                    val inputStream = context.assets.open("images/$it")
                                    val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
                                    inputStream.close()
                                    bitmap?.asImageBitmap()
                                } catch (e: IOException) { null }
                            }
                        }

                        // 1. Картинка (без weight)
                        if (imageBitmap != null) {
                            Image(
                                bitmap = imageBitmap,
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp)
                                    .clip(RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Fit
                            )
                        }

                        // 2. Текст (без weight)
                        val assembledText = viewModel.selectedCards.joinToString(" ") { it.text }
                        val styleConfig = CardStyles.getStyle(fontStyle)
                        val hebrewTextStyle = if (fontStyle == FontStyle.REGULAR) {
                            TextStyle(
                                fontFamily = FontFamily(Font(R.font.noto_sans_hebrew_variable, variationSettings = FontVariation.Settings(
                                    FontVariation.weight(styleConfig.fontWeight.roundToInt()),
                                    FontVariation.width(styleConfig.fontWidth)
                                ))),
                                fontSize = animatedFontSize.sp, // <-- Анимированный
                                textAlign = TextAlign.Right,
                                lineHeight = (animatedFontSize * 1.4f).sp,
                                color = StickyNoteText,
                                textDirection = TextDirection.Rtl
                            )
                        } else {
                            TextStyle(
                                fontFamily = fontStyle.fontFamily,
                                fontSize = animatedFontSize.sp, // <-- Анимированный
                                textAlign = TextAlign.Right,
                                lineHeight = (animatedFontSize * 1.4f).sp,
                                fontWeight = FontWeight(styleConfig.fontWeight.roundToInt()),
                                color = StickyNoteText,
                                textDirection = TextDirection.Rtl
                            )
                        }

                        Text(
                            text = assembledText,
                            style = hebrewTextStyle,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp) // <-- ИСПРАВЛЕНИЕ "ПОДРЕЗКИ"
                        )

                        // Добавляем Spacer в конце скролла, чтобы
                        // шторка не перекрывала текст
                        Spacer(modifier = Modifier.height(150.dp))
                    }
                }
            }
            // ЕСЛИ РАУНД ИДЕТ
            else {
                // Используем СТАРУЮ разметку (70/30)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.7f), // 70%
                    color = StickyNoteYellow,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val imageBitmap = remember(viewModel.currentImageName) {
                            viewModel.currentImageName?.let {
                                try {
                                    val inputStream = context.assets.open("images/$it")
                                    val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
                                    inputStream.close()
                                    bitmap?.asImageBitmap()
                                } catch (e: IOException) { null }
                            }
                        }

                        if (imageBitmap != null) {
                            Image(
                                bitmap = imageBitmap,
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(0.6f)
                                    .padding(bottom = 8.dp)
                                    .clip(RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Fit
                            )
                        }

                        val assembledText = viewModel.selectedCards.joinToString(" ") { it.text }
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

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(0.4f)
                        ) {
                            Text(
                                text = assembledText,
                                style = hebrewTextStyle,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState())
                                    .padding(bottom = 8.dp) // <-- ИСПРАВЛЕНИЕ "ПОДРЕЗКИ"
                            )
                        }
                    }
                }

                FlowRow(
                    modifier = Modifier
                        .weight(0.3f) // 30%
                        .fillMaxSize()
                        .background(Color.White)
                        .verticalScroll(rememberScrollState())
                        .padding(top = 16.dp, start = 16.dp, end = 16.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    viewModel.availableCards.forEach { card ->
                        key(card.id) {
                            Shakeable(
                                trigger = viewModel.errorCount,
                                errorCardId = viewModel.errorCardId,
                                currentCardId = card.id
                            ) { shakeModifier ->
                                SelectableCard(
                                    modifier = shakeModifier,
                                    card = card,
                                    onSelect = { viewModel.selectCard(card) },
                                    fontStyle = fontStyle
                                )
                            }
                        }
                    }
                }
            }
            // --- КОНЕЦ БОЛЬШОГО ИЗМЕНЕНИЯ ---
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
                audioPlayer = audioPlayer,
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


@OptIn(ExperimentalTextApi::class, ExperimentalFoundationApi::class)
@Composable
fun SelectableCard(
    modifier: Modifier = Modifier,
    card: Card,
    onSelect: () -> Unit,
    fontStyle: FontStyle
) {
    var isFlipped by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(
        targetValue = if (isFlipped) 180f else 0f,
        animationSpec = tween(600), label = "CardFlipAnimation"
    )

    LaunchedEffect(isFlipped) {
        if (isFlipped) {
            delay(1000)
            isFlipped = false
        }
    }

    val styleConfig = CardStyles.getStyle(fontStyle)

    val hebrewTextStyle = if (fontStyle == FontStyle.REGULAR) {
        TextStyle(
            fontFamily = FontFamily(Font(R.font.noto_sans_hebrew_variable, variationSettings = FontVariation.Settings(
                FontVariation.weight(styleConfig.fontWeight.roundToInt()),
                FontVariation.width(styleConfig.fontWidth)
            ))),
            fontSize = 29.sp,
            textAlign = TextAlign.Right,
            textDirection = TextDirection.Rtl
        )
    } else {
        TextStyle(
            fontFamily = fontStyle.fontFamily,
            fontSize = 29.sp,
            textAlign = TextAlign.Right,
            fontWeight = FontWeight(styleConfig.fontWeight.roundToInt()),
            textDirection = TextDirection.Rtl
        )
    }

    Card(
        modifier = modifier
            .graphicsLayer { rotationX = rotation }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onSelect() },
                    onLongPress = { isFlipped = true }
                )
            },
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(containerColor = StickyNoteYellow),
        border = BorderStroke(styleConfig.borderWidth, styleConfig.borderColor)
    ) {
        Box(
            modifier = Modifier
                .padding(vertical = styleConfig.verticalPadding, horizontal = styleConfig.horizontalPadding)
                .widthIn(min = 51.dp),
            contentAlignment = Alignment.Center
        ) {
            if (rotation < 90f) {
                Text(
                    text = card.text.replace('\n', ' ').replace('\r', ' '),
                    style = hebrewTextStyle,
                    color = StickyNoteText
                )
            }
            else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { rotationX = 180f },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = card.translation.ifEmpty { stringResource(R.string.result_translation_not_found) },
                        fontSize = 26.sp,
                        textAlign = TextAlign.Center,
                        color = StickyNoteText
                    )
                }
            }
        }
    }
}

@Composable
private fun ResultSheetContent(
    snapshot: RoundResultSnapshot,
    onContinueClick: () -> Unit,
    onRepeatClick: () -> Unit,
    audioPlayer: AudioPlayer,
    onTrackClick: () -> Unit
) {
    val scrollState = rememberScrollState()
    LaunchedEffect(snapshot.audioFilename) {
        snapshot.audioFilename?.let {
            delay(300)
            audioPlayer.play(it)
        }
    }

    Column(
        modifier = Modifier
            .padding(top = 24.dp, start = 16.dp, end = 16.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(scrollState),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = snapshot.translation,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Start
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onTrackClick) {
                Icon(
                    imageVector = Icons.Default.Extension,
                    contentDescription = stringResource(R.string.round_track_title, snapshot.levelId),
                    modifier = Modifier.size(32.dp)
                )
            }

            IconButton(onClick = onRepeatClick) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = stringResource(R.string.button_repeat_round),
                    modifier = Modifier.size(32.dp)
                )
            }

            Button(
                onClick = onContinueClick,
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
            ) {
                Text(text = stringResource(R.string.button_continue), fontSize = 20.sp)
            }
        }
    }
}