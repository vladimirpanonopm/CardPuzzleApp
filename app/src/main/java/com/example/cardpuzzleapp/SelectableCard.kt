package com.example.cardpuzzleapp

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cardpuzzleapp.ui.theme.StickyNoteText
import com.example.cardpuzzleapp.ui.theme.CardWhite
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
    val styleConfig = CardStyles.getStyle(fontStyle)

    // Настройка для обрезки лишнего воздуха в шрифте
    val platformStyle = PlatformTextStyle(
        includeFontPadding = false
    )

    val lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.Both
    )

    val hebrewTextStyle = if (fontStyle == FontStyle.REGULAR) {
        TextStyle(
            fontFamily = FontFamily(Font(R.font.noto_sans_hebrew_variable, variationSettings = FontVariation.Settings(
                FontVariation.weight(styleConfig.fontWeight.roundToInt()),
                FontVariation.width(styleConfig.fontWidth)
            ))),
            fontSize = 26.sp,
            textAlign = TextAlign.Center,
            textDirection = TextDirection.Rtl,
            platformStyle = platformStyle,
            lineHeightStyle = lineHeightStyle
        )
    } else {
        TextStyle(
            fontFamily = fontStyle.fontFamily,
            fontSize = 26.sp,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight(styleConfig.fontWeight.roundToInt()),
            textDirection = TextDirection.Rtl,
            platformStyle = platformStyle,
            lineHeightStyle = lineHeightStyle
        )
    }

    val tapHandler: (Offset) -> Unit = remember(isVisible, isAssembledCard, onSelect, isInteractionEnabled) {
        if (!isVisible || !isInteractionEnabled) {
            return@remember { }
        }
        if (isAssembledCard) {
            return@remember {}
        }
        return@remember { _ -> onSelect() }
    }

    Card(
        modifier = modifier
            .graphicsLayer {
                alpha = if (isVisible) 1f else 0f
            }
            .pointerInput(isVisible, isInteractionEnabled, isAssembledCard) {
                detectTapGestures(onTap = tapHandler)
            },
        // Убираем тень и ставим белый фон
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),

        // Рамку рисуем всегда
        border = BorderStroke(
            width = styleConfig.borderWidth,
            color = styleConfig.borderColor
        )
    ) {
        Box(
            modifier = Modifier
                .padding(vertical = styleConfig.verticalPadding, horizontal = styleConfig.horizontalPadding)
                .widthIn(min = 51.dp),
            contentAlignment = Alignment.Center
        ) {
            val textToShow = if (isAssembledCard) {
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
    }
}