package com.example.cardpuzzleapp

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
    isInteractionEnabled: Boolean,
    // --- НОВОЕ: Параметры для активного слота ---
    isActive: Boolean = false,
    onClick: () -> Unit = {}
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

        // Меняем цвет и толщину рамки, если слот активен
        val borderColor = if (isActive) MaterialTheme.colorScheme.primary else StickyNoteText.copy(alpha = 0.4f)
        val borderWidth = if (isActive) 2.dp else 1.dp

        Box(
            modifier = Modifier
                .height(intrinsicSize = IntrinsicSize.Min)
                .widthIn(min = 51.dp)
                .border(
                    width = borderWidth,
                    color = borderColor,
                    shape = RoundedCornerShape(styleConfig.cornerRadius)
                )
                // Делаем кликабельным, только если взаимодействие разрешено
                .clickable(
                    enabled = isInteractionEnabled,
                    onClick = onClick,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null // Убираем ripple эффект для чистоты
                )
                .padding(vertical = styleConfig.verticalPadding, horizontal = styleConfig.horizontalPadding),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "___",
                style = textStyle.copy(
                    fontSize = 26.sp,
                    color = StickyNoteText.copy(alpha = 0.3f),
                    textDirection = TextDirection.Ltr
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