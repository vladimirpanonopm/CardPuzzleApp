package com.example.cardpuzzleapp

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
        fontSize = 22.sp, // Было 28.sp
        horizontalPadding = 7.dp, // <-- ИЗМЕНЕНИЕ (Было 8.dp)
        verticalPadding = 2.dp, // (Оставляем)
        cornerRadius = 10.dp, // Было 12.dp
        borderWidth = 1.1.dp, // Было 1.4.dp
        fontWeight = 520f,
        fontWidth = 110f,
        borderColor = StickyNoteText.copy(alpha = 0.6f)
    )

    private val cursiveStyle = CardStyleConfig(
        fontSize = 22.sp, // Было 28.sp
        horizontalPadding = 9.dp, // <-- ИЗМЕНЕНИЕ (Было 10.dp)
        verticalPadding = 3.dp, // <-- ИЗМЕНЕНИЕ (Было 4.dp)
        cornerRadius = 10.dp, // Было 12.dp
        borderWidth = 1.1.dp, // Было 1.4.dp
        fontWeight = 400f,
        fontWidth = 100f,
        borderColor = StickyNoteText.copy(alpha = 0.6f)
    )

    // ИЗМЕНЕНИЕ: Тип возвращаемого значения исправлен на CardStyleConfig
    fun getStyle(fontStyle: FontStyle): CardStyleConfig {
        return when (fontStyle) {
            FontStyle.REGULAR -> regularStyle
            FontStyle.CURSIVE -> cursiveStyle
        }
    }
}