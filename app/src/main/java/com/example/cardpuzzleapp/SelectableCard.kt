package com.example.cardpuzzleapp

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cardpuzzleapp.ui.theme.StickyNoteText
import com.example.cardpuzzleapp.ui.theme.StickyNoteYellow
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import androidx.compose.ui.geometry.Offset

@OptIn(ExperimentalTextApi::class, ExperimentalFoundationApi::class)
@Composable
fun SelectableCard(
    modifier: Modifier = Modifier,
    card: Card,
    onSelect: () -> Unit,
    fontStyle: FontStyle,
    taskType: TaskType,
    isAssembledCard: Boolean = false,
    isVisible: Boolean,
    isInteractionEnabled: Boolean
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
            fontSize = 26.sp, // <-- ИЗМЕНЕНИЕ (Было 29.sp)
            textAlign = TextAlign.Right,
            textDirection = TextDirection.Rtl
        )
    } else {
        TextStyle(
            fontFamily = fontStyle.fontFamily,
            fontSize = 26.sp, // <-- ИЗМЕНЕНИЕ (Было 29.sp)
            textAlign = TextAlign.Right,
            fontWeight = FontWeight(styleConfig.fontWeight.roundToInt()),
            textDirection = TextDirection.Rtl
        )
    }

    val tapHandler: (Offset) -> Unit = remember(isVisible, taskType, isAssembledCard, onSelect, isInteractionEnabled) {

        if (!isVisible || !isInteractionEnabled) {
            return@remember {}
        }

        if (isAssembledCard && taskType == TaskType.FILL_IN_BLANK) {
            return@remember {}
        }

        return@remember { _ -> onSelect() }
    }


    Card(
        modifier = modifier
            .graphicsLayer {
                alpha = if (isVisible) 1f else 0f
                rotationX = rotation
            }
            .pointerInput(isVisible, isInteractionEnabled) {
                detectTapGestures(
                    onTap = tapHandler,
                    onLongPress = { if (isVisible && isInteractionEnabled) isFlipped = true }
                )
            },
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(containerColor = StickyNoteYellow),

        border = BorderStroke(
            width = if (isAssembledCard && taskType == TaskType.ASSEMBLE_TRANSLATION) {
                styleConfig.borderWidth
            } else {
                0.dp
            },
            color = if (isAssembledCard && taskType == TaskType.ASSEMBLE_TRANSLATION) {
                styleConfig.borderColor
            } else {
                Color.Transparent
            }
        )
    ) {
        Box(
            modifier = Modifier
                .padding(vertical = styleConfig.verticalPadding, horizontal = styleConfig.horizontalPadding)
                .widthIn(min = 51.dp),
            contentAlignment = Alignment.Center
        ) {
            if (rotation < 90f) {
                val textToShow = if (isAssembledCard && taskType == TaskType.ASSEMBLE_TRANSLATION) {
                    card.text
                } else {
                    card.text.replace('\n', ' ').replace('\r', ' ').trim()
                }

                Text(
                    text = textToShow,
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