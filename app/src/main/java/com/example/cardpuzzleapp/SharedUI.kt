package com.example.cardpuzzleapp

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import com.example.cardpuzzleapp.ui.theme.StickyNoteText
import kotlin.math.roundToInt

@Composable
fun InfoChip(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = label, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, fontSize = 20.sp)
    }
}

/**
 * Универсальная Composable-функция для анимации "потряхивания".
 * Работает с любым типом идентификатора (UUID, String, Int и т.д.).
 * @param T Тип идентификатора карточки.
 * @param trigger Значение, изменение которого запускает проверку (например, счетчик ошибок).
 * @param errorCardId ID карточки, которую нужно "потрясти".
 * @param currentCardId ID текущей карточки, для которой применяется этот модификатор.
 * @param content Контент, к которому применяется модификатор.
 */
@Composable
fun <T> Shakeable(
    trigger: Int,
    errorCardId: T?,
    currentCardId: T,
    content: @Composable (Modifier) -> Unit
) {
    // --- НАЧАЛО ВОССТАНОВЛЕННОГО КОДА ---

    // Animatable для смещения по оси X
    val xOffset = remember { Animatable(0f) }

    // Этот LaunchedEffect будет перезапускаться КАЖДЫЙ РАЗ, когда меняется trigger (errorCount)
    LaunchedEffect(trigger) {
        // Мы анимируем, только если ID этой карточки совпадает с ID ошибочной карточки
        if (errorCardId == currentCardId) {
            launch { // Используем launch, т.к. мы уже в CoroutineScope
                // Анимация "тряски"
                xOffset.animateTo(10f, tween(50))
                xOffset.animateTo(-10f, tween(50))
                xOffset.animateTo(10f, tween(50))
                xOffset.animateTo(-10f, tween(50))
                xOffset.animateTo(0f, tween(50))
            }
        }
    }

    // Применяем анимированное смещение к контенту
    content(Modifier.offset(x = xOffset.value.dp))

    // --- КОНЕЦ ВОССТАНОВЛЕННОГО КОДА ---
}
// --- ВСТАВЬТЕ ЭТО В КОНЕЦ SharedUI.kt ---



@OptIn(ExperimentalTextApi::class)
@Composable
fun FontToggleIcon(fontStyle: FontStyle) {
    // 1. ЛОГИКА ИЗМЕНЕНА НА ПРОТИВОПОЛОЖНУЮ
    // Определяем стиль, который будет ПОСЛЕ нажатия
    val targetStyle = if (fontStyle == FontStyle.CURSIVE) FontStyle.REGULAR else FontStyle.CURSIVE
    val styleConfig = CardStyles.getStyle(targetStyle)

    // Стиль буквы 'Алеф' на иконке теперь соответствует целевому стилю
    val iconTextStyle = if (targetStyle == FontStyle.CURSIVE) {
        TextStyle(
            fontFamily = targetStyle.fontFamily,
            fontWeight = FontWeight(styleConfig.fontWeight.roundToInt()),
            fontSize = 28.sp,
            textAlign = TextAlign.Center,
            textDirection = TextDirection.Rtl
        )
    } else { // REGULAR
        TextStyle(
            fontFamily = FontFamily(Font(R.font.noto_sans_hebrew_variable, variationSettings = FontVariation.Settings(
                FontVariation.weight(styleConfig.fontWeight.roundToInt()),
                FontVariation.width(styleConfig.fontWidth)
            ))),
            fontSize = 28.sp,
            textAlign = TextAlign.Center,
            textDirection = TextDirection.Rtl
        )
    }

    Text(
        text = "א",
        style = iconTextStyle,
        color = StickyNoteText
    )
}