package com.example.cardpuzzleapp

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepeatControlPanel(
    cardCount: Int,
    currentCardIndex: Int,
    onSliderPositionChange: (Int) -> Unit,
    cardPreviewText: String,
    pointA: Int?,
    pointB: Int?,
    onSetAClick: () -> Unit,
    onSetBClick: () -> Unit,
    onStartLoopClick: () -> Unit,
    isLooping: Boolean,
    playbackSpeed: Float,
    onSpeedChange: (Float) -> Unit,
    pauseDuration: Float,
    onPauseChange: (Float) -> Unit
) {
    var sliderPosition by remember(currentCardIndex) { mutableFloatStateOf(currentCardIndex.toFloat()) }
    val speedOptions = listOf(0.5f, 0.75f, 1.0f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.ab_repeat_title),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center
        )

        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = cardPreviewText,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                textAlign = TextAlign.Center,
                fontSize = 24.sp,
                maxLines = 2,
                fontWeight = FontWeight.Bold
            )
        }

        Slider(
            value = sliderPosition,
            onValueChange = {
                sliderPosition = it
                onSliderPositionChange(it.roundToInt())
            },
            valueRange = 0f..(cardCount - 1).toFloat(),
            steps = (cardCount - 2).coerceAtLeast(0)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Button(onClick = onSetAClick, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.ab_repeat_set_a) + (pointA?.let { " (${it + 1})" } ?: ""))
            }
            Button(onClick = onSetBClick, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.ab_repeat_set_b) + (pointB?.let { " (${it + 1})" } ?: ""))
            }
        }

        Text(stringResource(id = R.string.ab_repeat_speed), style = MaterialTheme.typography.labelLarge)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            speedOptions.forEachIndexed { index, speed ->
                SegmentedButton(
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = speedOptions.size),
                    onClick = { onSpeedChange(speed) },
                    selected = speed == playbackSpeed
                ) {
                    Text("${speed}x")
                }
            }
        }

        Text(stringResource(id = R.string.ab_repeat_pause) + ": ${pauseDuration.roundToInt()} сек", style = MaterialTheme.typography.labelLarge)
        Slider(
            value = pauseDuration,
            onValueChange = onPauseChange,
            valueRange = 0f..5f,
            steps = 4
        )

        Button(
            onClick = onStartLoopClick,
            enabled = (pointA != null && pointB != null),
            modifier = Modifier.fillMaxWidth(),
            colors = if(isLooping) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error) else ButtonDefaults.buttonColors()
        ) {
            Text(
                if (isLooping) stringResource(R.string.ab_repeat_stop_loop)
                else stringResource(R.string.ab_repeat_start_loop)
            )
        }

        Spacer(modifier = Modifier.navigationBarsPadding())
    }
}