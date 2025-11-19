@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalTextApi::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)

package com.example.cardpuzzleapp
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.ui.draw.clip
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
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

@Composable
fun AlefbetScreen(
    viewModel: AlefbetViewModel,
    onBackClick: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val view = LocalView.current

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()

    // --- ИСПРАВЛЕНИЕ: Удалена переменная userLanguage ---

    LaunchedEffect(Unit) {
        viewModel.hapticEvents.collectLatest { event ->
            when (event) {
                HapticEvent.Success -> view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                HapticEvent.Failure -> {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    delay(50)
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.alefbet_task_assemble),
                onBackClick = onBackClick,
                actions = {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                color = StickyNoteYellow,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable(
                                onClick = { viewModel.toggleFont() },
                                interactionSource = remember { MutableInteractionSource() },
                                indication = rememberRipple(color = StickyNoteText)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        FontToggleIcon(fontStyle = viewModel.currentFontStyle)
                    }
                }
            )
        },
        bottomBar = {
            AppBottomBar {
                AppBottomBarIcon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = stringResource(R.string.button_reset),
                    onClick = { viewModel.shuffleAndReset() }
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.16f),
                color = StickyNoteYellow
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.End
                ) {
                    val isCursive = viewModel.currentFontStyle == FontStyle.CURSIVE
                    val styleConfig = CardStyles.getStyle(viewModel.currentFontStyle)

                    val textStyle = if (isCursive) {
                        TextStyle(
                            fontFamily = viewModel.currentFontStyle.fontFamily,
                            fontSize = 36.sp,
                            textAlign = TextAlign.Right,
                            fontWeight = FontWeight(styleConfig.fontWeight.roundToInt()),
                            color = StickyNoteText,
                            letterSpacing = 12.sp
                        )
                    } else {
                        TextStyle(
                            fontFamily = FontFamily(Font(R.font.noto_sans_hebrew_variable, variationSettings = FontVariation.Settings(
                                FontVariation.weight(styleConfig.fontWeight.roundToInt()),
                                FontVariation.width(styleConfig.fontWidth)
                            ))),
                            fontSize = 36.sp,
                            textAlign = TextAlign.Right,
                            color = StickyNoteText,
                            letterSpacing = 12.sp
                        )
                    }

                    // Первая строка
                    Text(
                        text = viewModel.line1,
                        style = textStyle,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Вторая строка, если она есть
                    if (viewModel.line2.isNotEmpty()) {
                        val line2Modifier = if (isCursive) {
                            Modifier
                                .fillMaxWidth()
                                .offset(x = (-26.7).dp)
                        } else {
                            Modifier.fillMaxWidth()
                        }

                        Text(
                            text = viewModel.line2,
                            style = textStyle,
                            modifier = line2Modifier
                        )
                    }
                }
            }

            FlowRow(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(0.84f)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                viewModel.availableLetters.forEach { letter ->
                    key(letter.id) {
                        Box(
                            modifier = Modifier.size(80.dp)
                        ) {
                            if (!viewModel.selectedLetters.any { it.id == letter.id }) {
                                Shakeable(
                                    trigger = viewModel.errorCount,
                                    errorCardId = viewModel.errorCardId,
                                    currentCardId = letter.id,
                                ) { shakeModifier ->
                                    AlefbetCard(
                                        modifier = shakeModifier,
                                        letter = letter,
                                        fontStyle = viewModel.currentFontStyle,
                                        onSelect = {
                                            viewModel.selectLetter(letter)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (viewModel.isGameWon) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.shuffleAndReset() },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            dragHandle = null,
            scrimColor = Color.Transparent
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(vertical = 24.dp, horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Text(
                    text = "Собрано: ${viewModel.completionCount} раз",
                    style = MaterialTheme.typography.titleLarge
                )

                Button(
                    onClick = {
                        coroutineScope.launch {
                            sheetState.hide()
                        }.invokeOnCompletion {
                            viewModel.shuffleAndReset()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Text(stringResource(R.string.button_reset), fontSize = 20.sp)
                }
            }
        }
    }
}

@Composable
fun AlefbetCard(
    modifier: Modifier = Modifier,
    letter: HebrewLetter,
    fontStyle: FontStyle,
    onSelect: () -> Unit
) {
    val cardSize = 80f
    val letterFontSize = (cardSize * 0.5f).sp

    val styleConfig = CardStyles.getStyle(fontStyle)
    val hebrewTextStyle = if (fontStyle == FontStyle.REGULAR) {
        TextStyle(
            fontFamily = FontFamily(Font(R.font.noto_sans_hebrew_variable, variationSettings = FontVariation.Settings(
                FontVariation.weight(styleConfig.fontWeight.roundToInt()),
                FontVariation.width(styleConfig.fontWidth)
            ))),
            fontSize = letterFontSize,
            textAlign = TextAlign.Center
        )
    } else {
        TextStyle(
            fontFamily = fontStyle.fontFamily,
            fontSize = letterFontSize,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight(styleConfig.fontWeight.roundToInt())
        )
    }

    Card(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onSelect() }
                )
            },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = letter.letter,
                style = hebrewTextStyle,
            )
        }
    }
}