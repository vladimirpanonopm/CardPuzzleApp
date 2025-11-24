package com.example.cardpuzzleapp

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cardpuzzleapp.ui.theme.BorderGray
import com.example.cardpuzzleapp.ui.theme.StickyNoteText

data class CardStyleConfig(
    val fontSize: androidx.compose.ui.unit.TextUnit,
    val horizontalPadding: androidx.compose.ui.unit.Dp,
    val verticalPadding: androidx.compose.ui.unit.Dp,
    val cornerRadius: androidx.compose.ui.unit.Dp,
    val borderWidth: androidx.compose.ui.unit.Dp,
    val fontWeight: Float,
    val fontWidth: Float,
    val borderColor: Color
)

object CardStyles {
    private val regularStyle = CardStyleConfig(
        fontSize = 22.sp,
        horizontalPadding = 7.dp,
        verticalPadding = 2.dp,
        cornerRadius = 10.dp,
        borderWidth = 1.dp,
        fontWeight = 520f,
        fontWidth = 110f,
        borderColor = BorderGray
    )

    private val cursiveStyle = CardStyleConfig(
        fontSize = 22.sp,
        horizontalPadding = 10.dp,
        // Увеличенный отступ для рукописного шрифта, чтобы не обрезались буквы
        verticalPadding = 8.dp,
        cornerRadius = 10.dp,
        borderWidth = 1.dp,
        fontWeight = 400f,
        fontWidth = 100f,
        borderColor = BorderGray
    )

    fun getStyle(fontStyle: FontStyle): CardStyleConfig {
        return when (fontStyle) {
            FontStyle.REGULAR -> regularStyle
            FontStyle.CURSIVE -> cursiveStyle
        }
    }
}