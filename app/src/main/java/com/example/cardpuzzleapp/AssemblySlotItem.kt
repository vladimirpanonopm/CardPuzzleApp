package com.example.cardpuzzleapp

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cardpuzzleapp.ui.theme.StickyNoteText

@Composable
fun AssemblySlotItem(
    slot: AssemblySlot,
    textStyle: TextStyle,
    fontStyle: FontStyle,
    taskType: TaskType,
    onReturnCard: () -> Unit,
    // --- ИЗМЕНЕНИЕ: Добавлен isInteractionEnabled ---
    // (Этот параметр был утерян при загрузке старого файла)
    isInteractionEnabled: Boolean
) {
    val filledCard = slot.filledCard

    if (filledCard != null) {
        // --- Слот ЗАПОЛНЕН ---
        SelectableCard(
            card = filledCard,
            onSelect = onReturnCard,
            fontStyle = fontStyle,
            taskType = taskType,
            isAssembledCard = true,
            isVisible = true,
            isInteractionEnabled = isInteractionEnabled
        )
    } else if (slot.isBlank) {
        // --- Слот ПУСТОЙ (___) ---
        val styleConfig = CardStyles.getStyle(fontStyle)
        Box(
            modifier = Modifier
                .height(intrinsicSize = IntrinsicSize.Min)
                .widthIn(min = 51.dp)
                .border(
                    width = 1.dp,
                    color = StickyNoteText.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(styleConfig.cornerRadius)
                )
                .padding(vertical = styleConfig.verticalPadding, horizontal = styleConfig.horizontalPadding),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "___",
                style = textStyle.copy(
                    fontSize = 26.sp, // <-- ИЗМЕНЕНИЕ (Было 29.sp)
                    color = StickyNoteText.copy(alpha = 0.3f),
                    textDirection = TextDirection.Ltr // (Символы '___' всегда LTR)
                )
            )
        }
    } else {
        // --- Это обычный ТЕКСТ (напр. "אני ") ---
        Text(
            text = slot.text,
            style = textStyle,
            modifier = Modifier.padding(vertical = CardStyles.getStyle(fontStyle).verticalPadding)
        )
    }
}